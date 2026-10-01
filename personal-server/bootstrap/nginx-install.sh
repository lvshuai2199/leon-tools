#!/usr/bin/env bash
# 在服务器上（root）安装 LeonPro 的 nginx 站点配置。由 deploy-nginx.ps1 / deploy-nginx.sh 上传后调用，也可以手动执行：
#
#   sudo bash nginx-install.sh <新配置文件> [--check] [--force]
#   sudo bash nginx-install.sh --rollback [备份文件]
#
# 流程：检查新旧配置差异（发现线上有新配置里没有的 HTTPS / location 时停下）→ 带时间戳备份旧配置 →
#       写入新配置 → nginx -t（失败立即还原旧配置，不 reload）→ reload（失败同样还原）→ 本机 curl 冒烟。
#   --check     只做检查和 nginx -t，然后还原旧配置，不 reload（线上不受影响）
#   --force     线上配置里有新配置没有的内容、或前端目录还没有 index.html 时仍然继续
#   --rollback  还原最近一次（或指定的）备份，nginx -t 通过后 reload
set -euo pipefail

TARGET="${NGINX_TARGET:-/etc/nginx/sites-available/default}"
BACKUP_DIR="${NGINX_BACKUP_DIR:-/etc/nginx/leonpro-backups}"
WEB_DIR="${LEONPRO_WEB_DIR:-/var/www/leonpro-web}"
ADMIN_DIR="${LEONPRO_ADMIN_DIR:-/var/www/leonpro-admin}"
# 新布局里有意去掉的 location（其余被去掉的都要 --force 确认）
EXPECTED_REMOVED=("/h5" "= /h5" "/h5/" "= /portal" "/portal" "/portal/")

NEW=""
CHECK=0
FORCE=0
ROLLBACK=0
ROLLBACK_FILE=""
for a in "$@"; do
  case "$a" in
    --check) CHECK=1 ;;
    --force) FORCE=1 ;;
    --rollback) ROLLBACK=1 ;;
    -*) echo "未知参数：$a" >&2; exit 2 ;;
    *) if [[ "$ROLLBACK" == "1" ]]; then ROLLBACK_FILE="$a"; else NEW="$a"; fi ;;
  esac
done

log() { echo "[nginx] $*"; }
die() { echo "[nginx] 错误：$1" >&2; exit "${2:-1}"; }

[[ "${EUID}" -eq 0 ]] || die "请用 root 执行：sudo bash $0 ..."
command -v nginx >/dev/null 2>&1 || die "没有找到 nginx"
[[ -e "$TARGET" ]] || die "站点配置 $TARGET 不存在"
REAL_TARGET="$(readlink -f "$TARGET")"

reload_nginx() {
  if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet nginx 2>/dev/null; then
    systemctl reload nginx
  else
    nginx -s reload
  fi
}

restore() {
  local from="$1"
  cp -a "$from" "$REAL_TARGET"
  log "已还原 $REAL_TARGET ← $from"
}

smoke() {
  command -v curl >/dev/null 2>&1 || return 0
  sleep 1
  for p in / /admin/ /prod-api/; do
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1$p" || echo ERR)"
    log "冒烟 GET $p -> $code"
  done
}

if [[ "$ROLLBACK" == "1" ]]; then
  if [[ -z "$ROLLBACK_FILE" ]]; then
    # 备份文件名里带时间戳，按文件名倒序取最新（cp -a 保留了原文件的修改时间，不能按 mtime 排）
    ROLLBACK_FILE="$(ls -1 "$BACKUP_DIR"/"$(basename "$REAL_TARGET")".* 2>/dev/null | grep -Ev '\.(check|before-rollback)$' | sort -r | head -n 1 || true)"
  fi
  [[ -n "$ROLLBACK_FILE" && -f "$ROLLBACK_FILE" ]] || die "没有可用的备份（$BACKUP_DIR）"
  mkdir -p "$BACKUP_DIR"
  BEFORE="$BACKUP_DIR/$(basename "$REAL_TARGET").$(date +%Y%m%d-%H%M%S).before-rollback"
  cp -a "$REAL_TARGET" "$BEFORE"
  if cmp -s "$ROLLBACK_FILE" "$REAL_TARGET"; then
    log "注意：$ROLLBACK_FILE 与当前配置完全相同"
  fi
  restore "$ROLLBACK_FILE"
  if ! nginx -t; then
    restore "$BEFORE"
    die "备份 $ROLLBACK_FILE 的 nginx -t 不通过，已放回回滚前的配置，没有 reload" 4
  fi
  reload_nginx || die "reload 失败，请人工检查（当前文件是 $ROLLBACK_FILE 的内容）" 5
  log "已回滚到 $ROLLBACK_FILE 并 reload（回滚前的配置另存为 $BEFORE）"
  smoke
  exit 0
fi

[[ -n "$NEW" && -f "$NEW" ]] || die "用法：sudo bash $0 <新配置文件> [--check] [--force]" 2

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
tr -d '\r' < "$NEW" > "$TMP"

# 1. 目标文件确实被 nginx 加载
enabled=0
for f in /etc/nginx/sites-enabled/*; do
  [[ -e "$f" ]] || continue
  [[ "$(readlink -f "$f")" == "$REAL_TARGET" ]] && enabled=1
done
if [[ "$enabled" == "0" ]] && nginx -T 2>/dev/null | grep -Fq "# configuration file $REAL_TARGET:"; then
  enabled=1
fi
if [[ "$enabled" == "0" ]]; then
  log "警告：$REAL_TARGET 看起来没有被 nginx 加载（sites-enabled 里没有指向它的链接）"
  [[ "$FORCE" == "1" ]] || die "确认无误后加 --force 重试" 3
fi

# 2. 与线上配置对比
unsafe=0
tls_re='^[[:space:]]*(listen[[:space:]]+[^;]*443|ssl_certificate)'
if grep -Eq "$tls_re" "$REAL_TARGET" && ! grep -Eq "$tls_re" "$TMP"; then
  log "警告：线上配置有 HTTPS（listen 443 / ssl_certificate），新配置没有——直接替换会关掉 HTTPS"
  unsafe=1
fi
locs() { grep -Eo '^[[:space:]]*location[[:space:]]+[^{]+' "$1" | sed -E 's/^[[:space:]]*location[[:space:]]+//; s/[[:space:]]+$//' | sort -u; }
names() { grep -Eo '^[[:space:]]*server_name[[:space:]]+[^;]+' "$1" | sed -E 's/^[[:space:]]*server_name[[:space:]]+//' | sort -u; }
while IFS= read -r loc; do
  [[ -z "$loc" ]] && continue
  skip=0
  for e in "${EXPECTED_REMOVED[@]}"; do [[ "$loc" == "$e" ]] && skip=1; done
  if [[ "$skip" == "1" ]]; then
    log "按计划去掉 location $loc"
  else
    log "警告：线上有 location $loc，新配置里没有"
    unsafe=1
  fi
done < <(comm -23 <(locs "$REAL_TARGET") <(locs "$TMP"))
if [[ -n "$(comm -23 <(names "$REAL_TARGET") <(names "$TMP"))" ]]; then
  log "警告：线上 server_name 与新配置不同：$(names "$REAL_TARGET" | tr '\n' ' ')→ $(names "$TMP" | tr '\n' ' ')"
  unsafe=1
fi
for d in "$WEB_DIR" "$ADMIN_DIR"; do
  if [[ ! -f "$d/index.html" ]]; then
    log "警告：$d/index.html 不存在——应先部署前端再切 nginx"
    unsafe=1
  fi
done
log "新旧配置差异（- 线上  + 新）："
diff -u "$REAL_TARGET" "$TMP" || true
if [[ "$unsafe" == "1" && "$FORCE" != "1" ]]; then
  die "有上面的警告，未做任何修改。确认无误后加 --force 重试" 3
fi

# 3. 备份
mkdir -p "$BACKUP_DIR"
TS="$(date +%Y%m%d-%H%M%S)"
BK="$BACKUP_DIR/$(basename "$REAL_TARGET").$TS"
[[ "$CHECK" == "1" ]] && BK="$BK.check"
cp -a "$REAL_TARGET" "$BK"
log "已备份旧配置 → $BK"

# 4. 写入并 nginx -t
cat "$TMP" > "$REAL_TARGET"
if ! nginx -t; then
  restore "$BK"
  rm -f "$BK"   # 与当前配置相同，不留着（免得 --rollback 选到它）
  nginx -t >/dev/null 2>&1 || log "注意：还原后 nginx -t 仍不通过，说明问题不在本站点配置，请人工检查"
  die "新配置 nginx -t 不通过，已还原旧配置，没有 reload" 4
fi
if [[ "$CHECK" == "1" ]]; then
  restore "$BK"
  rm -f "$BK"
  log "检查通过：新配置 nginx -t OK（--check 模式，已还原旧配置，没有 reload）"
  exit 0
fi

# 5. reload（失败则还原）
if ! reload_nginx; then
  restore "$BK"
  rm -f "$BK"
  nginx -t && reload_nginx || true
  die "reload 失败，已还原旧配置" 5
fi
log "已生效。回滚：sudo bash $0 --rollback $BK"
smoke

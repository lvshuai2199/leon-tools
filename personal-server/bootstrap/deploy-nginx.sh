#!/usr/bin/env bash
# LeonPro：上传 nginx-leonpro.conf 并在服务器上安装（备份 → nginx -t → reload，失败自动回滚）。只由站点负责人执行。
#   bash deploy-nginx.sh --check        上传并在服务器 nginx -t，然后还原旧配置（不 reload）
#   bash deploy-nginx.sh                正式安装（会要求输入 yes 确认）
#   bash deploy-nginx.sh --force        线上配置有新配置没有的 HTTPS / location 时仍继续
#   bash deploy-nginx.sh --rollback [备份文件]   还原最近一次（或指定）备份并 reload
#   加 --yes 跳过确认
# 登录信息读 bootstrap.env（DEPLOY_HOST/USER/PORT/PASSWORD/SSH_KEY），缺的再从后端 deploy/deploy.env 读。
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
CONF="$DIR/nginx-leonpro.conf"
INSTALL="$DIR/nginx-install.sh"
REMOTE_DIR="/tmp/leonpro-nginx"

CHECK=0; FORCE=0; ROLLBACK=0; YES=0; BACKUP_FILE=""
for a in "$@"; do
  case "$a" in
    --check) CHECK=1 ;;
    --force) FORCE=1 ;;
    --rollback) ROLLBACK=1 ;;
    --yes) YES=1 ;;
    -*) echo "未知参数：$a" >&2; exit 2 ;;
    *) BACKUP_FILE="$a" ;;
  esac
done

read_env() {
  local f="$1"
  [[ -f "$f" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    local key="${line%%=*}" value="${line#*=}"
    key="$(echo "$key" | xargs)"
    [[ -z "${!key:-}" && -n "$value" ]] && printf -v "$key" '%s' "$value"
  done < "$f"
}
DEPLOY_HOST="${DEPLOY_HOST:-}"; DEPLOY_USER="${DEPLOY_USER:-}"; DEPLOY_PORT="${DEPLOY_PORT:-}"
DEPLOY_PASSWORD="${DEPLOY_PASSWORD:-}"; DEPLOY_SSH_KEY="${DEPLOY_SSH_KEY:-}"
read_env "$DIR/bootstrap.env"
read_env "$DIR/../LeonPro_backend/SpringBoot/deploy/deploy.env"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
[[ -n "$DEPLOY_HOST" && -n "$DEPLOY_USER" ]] || { echo "bootstrap.env 或后端 deploy.env 需要 DEPLOY_HOST / DEPLOY_USER" >&2; exit 1; }
[[ -f "$INSTALL" ]] || { echo "缺少 $INSTALL" >&2; exit 1; }
[[ "$ROLLBACK" == "1" || -f "$CONF" ]] || { echo "缺少 $CONF" >&2; exit 1; }

SSH_OPTS=(-p "$DEPLOY_PORT" -o StrictHostKeyChecking=accept-new)
SCP_OPTS=(-P "$DEPLOY_PORT" -o StrictHostKeyChecking=accept-new)
SSH_BIN=(ssh); SCP_BIN=(scp)
if [[ -n "$DEPLOY_SSH_KEY" ]]; then
  SSH_OPTS+=(-i "$DEPLOY_SSH_KEY"); SCP_OPTS+=(-i "$DEPLOY_SSH_KEY")
elif [[ -n "$DEPLOY_PASSWORD" ]]; then
  command -v sshpass >/dev/null 2>&1 || { echo "密码登录需要 sshpass" >&2; exit 1; }
  export SSHPASS="$DEPLOY_PASSWORD"
  SSH_BIN=(sshpass -e ssh); SCP_BIN=(sshpass -e scp)
  SSH_OPTS+=(-o PreferredAuthentications=password -o PubkeyAuthentication=no)
  SCP_OPTS+=(-o PreferredAuthentications=password -o PubkeyAuthentication=no)
fi
REMOTE="${DEPLOY_USER}@${DEPLOY_HOST}"

if [[ "$ROLLBACK" == "1" ]]; then
  ACTION="回滚 $REMOTE 的 nginx 配置到 ${BACKUP_FILE:-最近一次备份}"
  ARGS="--rollback${BACKUP_FILE:+ '$BACKUP_FILE'}"
elif [[ "$CHECK" == "1" ]]; then
  ACTION="检查 $REMOTE 上的新 nginx 配置（nginx -t 后还原，不 reload）"
  ARGS="'$REMOTE_DIR/nginx-leonpro.conf' --check"
else
  ACTION="在 $REMOTE 安装新 nginx 配置并 reload"
  ARGS="'$REMOTE_DIR/nginx-leonpro.conf'"
fi
[[ "$FORCE" == "1" ]] && ARGS="$ARGS --force"
echo "$ACTION"
if [[ "$YES" != "1" && "$CHECK" != "1" ]]; then
  read -r -p "输入 yes 继续：" ans
  [[ "$ans" == "yes" ]] || { echo "已取消"; exit 1; }
fi

"${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "mkdir -p '$REMOTE_DIR'"
"${SCP_BIN[@]}" "${SCP_OPTS[@]}" "$INSTALL" "${REMOTE}:$REMOTE_DIR/nginx-install.sh"
[[ "$ROLLBACK" == "1" ]] || "${SCP_BIN[@]}" "${SCP_OPTS[@]}" "$CONF" "${REMOTE}:$REMOTE_DIR/nginx-leonpro.conf"

set +e
CRLF_FILES="'$REMOTE_DIR/nginx-install.sh'"
[[ "$ROLLBACK" == "1" ]] || CRLF_FILES+=" '$REMOTE_DIR/nginx-leonpro.conf'"
"${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "sed -i 's/\r\$//' $CRLF_FILES && if [ \"\$(id -u)\" = 0 ]; then bash '$REMOTE_DIR/nginx-install.sh' $ARGS; elif sudo -n true 2>/dev/null; then sudo bash '$REMOTE_DIR/nginx-install.sh' $ARGS; else sudo -S -p '' bash '$REMOTE_DIR/nginx-install.sh' $ARGS <<< '$DEPLOY_PASSWORD'; fi"
code=$?
set -e
case "$code" in
  0) echo "完成" ;;
  3) echo "有警告，未做任何修改；确认无误后加 --force 重试" ;;
  4) echo "nginx -t 不通过，已还原旧配置，没有 reload" ;;
  5) echo "reload 失败，已还原旧配置" ;;
  *) echo "服务器脚本退出码 $code" ;;
esac
exit "$code"

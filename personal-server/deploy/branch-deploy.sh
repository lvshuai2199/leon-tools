#!/usr/bin/env bash
# 在服务器上按 Git 分支拉取代码，用 Docker 直接构建并部署这次改动对应的程序。
#
# 服务器上：
#   bash branch-deploy.sh <分支> [程序...]
#   bash branch-deploy.sh --dry-run <分支>
#   bash branch-deploy.sh --all --yes <分支>
#   bash branch-deploy.sh --status
#
# 自己电脑上（读取后端 deploy/deploy.env，把脚本和 Dockerfile 放到服务器再执行）：
#   bash personal-server/deploy/branch-deploy.sh --remote --dry-run <分支>
#   bash personal-server/deploy/branch-deploy.sh --remote --yes <分支> web admin
#
# GitHub Actions：Deploy branch。Actions 只上传脚本和 Dockerfile，构建在服务器上完成。
#
# 不写程序名时：和该程序上次部署的提交对比，只部署有文件变化的程序。
# backend、web、admin 还没有记录时会部署。nginx 没有记录时不改线上配置，
# 真正部署时只记下提交，之后配置文件有变化才安装。点名 nginx 或加 --all 会立刻安装。
# 程序顺序固定为 backend → web → admin → nginx。
#
# 构建是 docker build（多阶段镜像）。服务器不需要安装 JDK / Node。
# Dockerfile 跟脚本放在一起（/opt/leonpro/bin/docker），不跟被检出的旧分支走。
# 后端打成 leonpro-backend:latest 后，compose 用 --no-build 启动，避免被只复制 jar 的 Dockerfile 覆盖。
# 运行目录：
#   源码   /opt/leonpro/src
#   后端   /opt/leonpro/backend   （不覆盖 .env、uploads、data）
#   用户端 /var/www/leonpro-web
#   管理端 /var/www/leonpro-admin
# 可选 /opt/leonpro/git.env：GIT_REMOTE=...  GIT_TOKEN=...（私有仓库；不要提交）

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
fi

# 公开仓库地址。行尾标记让提交扫描放过这段链接。
REPO_DEFAULT="https://github.com/lvshuai2199/leon-tools.git" # pragma: allowlist secret
ROOT="${LEONPRO_ROOT:-/opt/leonpro}"
SRC="${LEONPRO_SRC:-$ROOT/src}"
STATE_DIR="${LEONPRO_STATE:-$ROOT/deploy-state}"
STATE_FILE="$STATE_DIR/state"
LOG="$STATE_DIR/branch-deploy.log"
TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROGRAMS=(backend web admin nginx)

program_paths() {
  case "$1" in
    backend)
      printf '%s\n' \
        'personal-server/LeonPro_backend/' \
        'personal-server/LeonPro_frontend/vue3_frontend/src/router/menus.json' \
        'personal-server/LeonPro_frontend/frontend_phone/src/router/menus.json'
      ;;
    web)
      printf '%s\n' \
        'personal-server/LeonPro_frontend/frontend_phone/' \
        'personal-server/LeonPro_frontend/shared/'
      ;;
    admin)
      printf '%s\n' \
        'personal-server/LeonPro_frontend/vue3_frontend/' \
        'personal-server/LeonPro_frontend/shared/'
      ;;
    nginx)
      printf '%s\n' \
        'personal-server/bootstrap/nginx-leonpro.conf' \
        'personal-server/bootstrap/nginx-install.sh'
      ;;
    *)
      return 1
      ;;
  esac
}

file_hits_program() {
  local file="$1" prog="$2" prefix
  while IFS= read -r prefix; do
    [[ -z "$prefix" ]] && continue
    if [[ "$prefix" == */ ]]; then
      [[ "$file" == "$prefix"* ]] && return 0
    else
      [[ "$file" == "$prefix" ]] && return 0
    fi
  done < <(program_paths "$prog")
  return 1
}

# 标准输入是变更文件列表，按固定顺序打印要部署的程序。
programs_for_files() {
  local prog file hit
  local -a files=()
  while IFS= read -r file || [[ -n "${file:-}" ]]; do
    [[ -n "$file" ]] && files+=("$file")
  done
  for prog in "${PROGRAMS[@]}"; do
    hit=0
    for file in "${files[@]+"${files[@]}"}"; do
      if file_hits_program "$file" "$prog"; then
        hit=1
        break
      fi
    done
    [[ "$hit" == 1 ]] && printf '%s\n' "$prog"
  done
}

valid_branch() {
  local b="$1"
  [[ "$b" =~ ^[A-Za-z0-9._/-]+$ ]] || return 1
  [[ "$b" != *".."* && "$b" != /* && "$b" != */ && "$b" != -* ]]
}

valid_program() {
  local p="$1" known
  for known in "${PROGRAMS[@]}"; do
    [[ "$p" == "$known" ]] && return 0
  done
  return 1
}

usage() {
  cat <<'EOF'
在服务器上按 Git 分支拉取代码，用 Docker 直接构建并部署这次改动对应的程序。

服务器上：
  bash branch-deploy.sh <分支> [程序...]
  bash branch-deploy.sh --dry-run <分支>
  bash branch-deploy.sh --all --yes <分支>
  bash branch-deploy.sh --status

自己电脑上（读取后端 deploy/deploy.env，把脚本和 Dockerfile 放到服务器再执行）：
  bash personal-server/deploy/branch-deploy.sh --remote --dry-run <分支>
  bash personal-server/deploy/branch-deploy.sh --remote --yes <分支> web admin

GitHub Actions：Deploy branch。Actions 只上传脚本和 Dockerfile，构建在服务器上完成。

不写程序名时：和该程序上次部署的提交对比，只部署有文件变化的程序。
backend、web、admin 还没有记录时会部署。nginx 没有记录时不改线上配置，
真正部署时只记下提交，之后配置文件有变化才安装。点名 nginx 或加 --all 会立刻安装。
程序顺序固定为 backend → web → admin → nginx。

构建是 docker build（多阶段镜像）。服务器不需要安装 JDK / Node。
Dockerfile 跟脚本放在一起（/opt/leonpro/bin/docker），不跟被检出的旧分支走。
后端打成 leonpro-backend:latest 后，compose 用 --no-build 启动，避免被只复制 jar 的 Dockerfile 覆盖。
运行目录：
  源码   /opt/leonpro/src
  后端   /opt/leonpro/backend   （不覆盖 .env、uploads、data）
  用户端 /var/www/leonpro-web
  管理端 /var/www/leonpro-admin
可选 /opt/leonpro/git.env：GIT_REMOTE=...  GIT_TOKEN=...（私有仓库；不要提交）
EOF
}

log() {
  local line
  line="$(date '+%F %T') $*"
  printf '%s\n' "$line"
  if [[ -n "${LOG:-}" && -d "$(dirname "$LOG")" ]]; then
    printf '%s\n' "$line" >> "$LOG" || true
  fi
}

die() {
  log "错误：$*"
  exit 1
}

need_dir() {
  local d="$1"
  [[ -d "$d" ]] && return 0
  sudo mkdir -p "$d"
  sudo chown "$(id -u):$(id -g)" "$d"
}

load_git_env() {
  local f="$ROOT/git.env"
  [[ -f "$f" ]] || return 0
  local line key value
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      GIT_REMOTE) GIT_REMOTE="$value" ;;
      GIT_TOKEN) GIT_TOKEN="$value" ;;
    esac
  done < "$f"
}

gitc() {
  if [[ -n "${GIT_TOKEN:-}" ]]; then
    git -c "http.extraheader=AUTHORIZATION: bearer ${GIT_TOKEN}" "$@"
  else
    git "$@"
  fi
}

read_env_file() {
  local f="$1" line key value
  [[ -f "$f" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    key="$(echo "$key" | xargs)"
    case "$key" in
      DEPLOY_HOST|DEPLOY_USER|DEPLOY_PORT|DEPLOY_PASSWORD|DEPLOY_SSH_KEY)
        if [[ -z "${!key:-}" && -n "$value" ]]; then
          printf -v "$key" '%s' "$value"
        fi
        ;;
    esac
  done < "$f"
  return 0
}

remote_launch() {
  local script_dir env_file
  script_dir="$(cd "$(dirname "$0")" && pwd)"
  DEPLOY_HOST="${DEPLOY_HOST:-}"
  DEPLOY_USER="${DEPLOY_USER:-}"
  DEPLOY_PORT="${DEPLOY_PORT:-}"
  DEPLOY_PASSWORD="${DEPLOY_PASSWORD:-}"
  DEPLOY_SSH_KEY="${DEPLOY_SSH_KEY:-}"
  read_env_file "$script_dir/../LeonPro_backend/SpringBoot/deploy/deploy.env"
  read_env_file "$script_dir/../bootstrap/bootstrap.env"
  # 有的环境把同一串密码写进 DEPLOY_SSH_KEY。不是现成的密钥文件就按密码登录。
  if [[ -n "${DEPLOY_SSH_KEY:-}" && ! -f "$DEPLOY_SSH_KEY" ]]; then
    DEPLOY_SSH_KEY=""
  fi
  : "${DEPLOY_HOST:?deploy.env 需要 DEPLOY_HOST}"
  : "${DEPLOY_USER:?deploy.env 需要 DEPLOY_USER}"
  DEPLOY_PORT="${DEPLOY_PORT:-22}"

  local -a ssh_opts scp_opts ssh_bin scp_bin
  ssh_opts=(-p "$DEPLOY_PORT" -o StrictHostKeyChecking=accept-new)
  scp_opts=(-P "$DEPLOY_PORT" -o StrictHostKeyChecking=accept-new)
  ssh_bin=(ssh)
  scp_bin=(scp)
  if [[ -n "${DEPLOY_SSH_KEY:-}" ]]; then
    ssh_opts+=(-i "$DEPLOY_SSH_KEY")
    scp_opts+=(-i "$DEPLOY_SSH_KEY")
  elif [[ -n "${DEPLOY_PASSWORD:-}" ]]; then
    command -v sshpass >/dev/null 2>&1 || die "密码登录需要 sshpass"
    export SSHPASS="$DEPLOY_PASSWORD"
    ssh_bin=(sshpass -e ssh)
    scp_bin=(sshpass -e scp)
    ssh_opts+=(-o PreferredAuthentications=password -o PubkeyAuthentication=no)
    scp_opts+=(-o PreferredAuthentications=password -o PubkeyAuthentication=no)
  fi
  local docker_src="$script_dir/docker"
  [[ -d "$docker_src" ]] || die "缺少 $docker_src"
  local remote="${DEPLOY_USER}@${DEPLOY_HOST}"
  "${ssh_bin[@]}" "${ssh_opts[@]}" "$remote" "sudo mkdir -p '$ROOT/bin' '$STATE_DIR' && sudo chown -R \"\$USER:\$USER\" '$ROOT/bin' '$STATE_DIR' && rm -rf '$ROOT/bin/docker'"
  "${scp_bin[@]}" "${scp_opts[@]}" "$0" "${remote}:$ROOT/bin/branch-deploy.sh"
  "${scp_bin[@]}" "${scp_opts[@]}" -r "$docker_src" "${remote}:$ROOT/bin/"
  "${ssh_bin[@]}" "${ssh_opts[@]}" "$remote" "chmod +x '$ROOT/bin/branch-deploy.sh' && bash '$ROOT/bin/branch-deploy.sh' $(printf '%q ' "$@")"
}

load_state() {
  STATE_KEYS=()
  declare -gA STATE=()
  [[ -f "$STATE_FILE" ]] || return 0
  local line key value
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[a-z]+\.[a-z]+= ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    STATE["$key"]="$value"
  done < "$STATE_FILE"
}

save_state() {
  local tmp key
  tmp="$(mktemp)"
  for key in "${!STATE[@]}"; do
    printf '%s=%s\n' "$key" "${STATE[$key]}"
  done | sort > "$tmp"
  mv "$tmp" "$STATE_FILE"
}

mark_deployed() {
  local prog="$1" sha="$2" branch="$3"
  load_state
  STATE["sha.$prog"]="$sha"
  STATE["branch.$prog"]="$branch"
  STATE["time.$prog"]="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  save_state
}

show_status() {
  load_state
  if [[ ! -f "$STATE_FILE" ]]; then
    echo "还没有部署记录（$STATE_FILE）"
    return 0
  fi
  local prog
  for prog in "${PROGRAMS[@]}"; do
    printf '%-8s 分支=%s  提交=%s  时间=%s\n' \
      "$prog" \
      "${STATE[branch.$prog]:-}" \
      "${STATE[sha.$prog]:-从未部署}" \
      "${STATE[time.$prog]:-}"
  done
  if [[ -d "$SRC/.git" ]]; then
    echo "源码目录：$SRC  当前 $(git -C "$SRC" rev-parse --short HEAD 2>/dev/null || echo '无检出')"
  fi
}

ensure_repo() {
  need_dir "$ROOT"
  need_dir "$(dirname "$SRC")"
  load_git_env
  local remote="${GIT_REMOTE:-$REPO_DEFAULT}"
  if [[ ! -d "$SRC/.git" ]]; then
    need_dir "$SRC"
    # 目录必须是空的才能 clone 进去；need_dir 刚建好时是空的
    rmdir "$SRC" 2>/dev/null || true
    log "克隆 $remote"
    gitc clone --filter=blob:none --no-checkout "$remote" "$SRC"
  fi
  log "拉取 origin/$BRANCH"
  gitc -C "$SRC" fetch --prune origin "refs/heads/${BRANCH}:refs/remotes/origin/${BRANCH}"
  SHA="$(gitc -C "$SRC" rev-parse "origin/${BRANCH}")"
  log "目标提交 ${SHA:0:12}"
}

changed_files_since() {
  local old="$1"
  if ! git -C "$SRC" cat-file -e "${old}^{commit}" 2>/dev/null; then
    log "上次记录的提交 $old 已经不在仓库里，这个程序按全部文件处理"
    git -C "$SRC" ls-tree -r --name-only "$SHA"
    return 0
  fi
  git -C "$SRC" diff --name-only "$old" "$SHA"
}

plan_programs() {
  PLANNED=()
  NGINX_NEEDS_BASELINE=0
  if [[ "$ALL" == 1 ]]; then
    PLANNED=("${PROGRAMS[@]}")
    return 0
  fi
  if [[ ${#EXPLICIT[@]} -gt 0 ]]; then
    PLANNED=("${EXPLICIT[@]}")
    return 0
  fi
  load_state
  local prog old file hit
  for prog in "${PROGRAMS[@]}"; do
    old="${STATE[sha.$prog]:-}"
    if [[ -z "$old" ]]; then
      # 线上 nginx 可能有仓库里没有的配置。没有记录时不安装，只在真正部署时记下提交。
      if [[ "$prog" == nginx ]]; then
        NGINX_NEEDS_BASELINE=1
        continue
      fi
      PLANNED+=("$prog")
      continue
    fi
    [[ "$old" == "$SHA" ]] && continue
    hit=0
    while IFS= read -r file || [[ -n "${file:-}" ]]; do
      [[ -z "$file" ]] && continue
      if file_hits_program "$file" "$prog"; then
        hit=1
        break
      fi
    done < <(changed_files_since "$old")
    [[ "$hit" == 1 ]] && PLANNED+=("$prog")
  done
}

checkout_src() {
  log "检出 ${SHA:0:12}"
  git -C "$SRC" checkout --force --detach "$SHA"
  git -C "$SRC" clean -fdx
}

install_dockerignore() {
  local ignore="$TOOL_DIR/docker/src.dockerignore"
  [[ -f "$ignore" ]] || die "缺少 $ignore"
  cp -a "$ignore" "$SRC/.dockerignore"
}

docker_build() {
  local dockerfile="$1" tag="$2"
  [[ -f "$dockerfile" ]] || die "缺少 $dockerfile"
  install_dockerignore
  log "docker build -t $tag"
  docker build -f "$dockerfile" -t "$tag" "$SRC"
}

copy_from_image() {
  local image="$1" inner="$2" dest="$3" cid
  cid="$(docker create "$image")"
  if ! docker cp "$cid:$inner" "$dest"; then
    docker rm "$cid" >/dev/null || true
    die "无法从 $image 拷贝 $inner"
  fi
  docker rm "$cid" >/dev/null
}

check_boot_jar() {
  local jar="$1"
  command -v python3 >/dev/null 2>&1 || die "检查 jar 需要 python3"
  python3 - "$jar" <<'PY' || die "jar 里缺少页面清单，不替换正在运行的后端"
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
names = set(z.namelist())
need = [
    "BOOT-INF/classes/menus/admin/menus.json",
    "BOOT-INF/classes/menus/app/menus.json",
]
missing = [n for n in need if n not in names]
if missing:
    print("缺少: " + ", ".join(missing))
    sys.exit(1)
PY
}

# 旧分支可能没有 elite-task。空目录进不了构建上下文，放一个占位文件。
ensure_elite_task() {
  local d="$SRC/personal-server/LeonPro_frontend/elite-task"
  if [[ ! -d "$d" ]] || [[ -z "$(find "$d" -type f -print -quit)" ]]; then
    mkdir -p "$d"
    printf '\n' > "$d/.keep"
  fi
}

deploy_backend() {
  local tag="leonpro-backend:latest"
  docker_build "$TOOL_DIR/docker/backend.Dockerfile" "$tag"
  local tmp
  tmp="$(mktemp -d)"
  copy_from_image "$tag" /app/app.jar "$tmp/app.jar"
  [[ -f "$tmp/app.jar" ]] || die "镜像里没有 /app/app.jar"
  check_boot_jar "$tmp/app.jar"
  local rt="$ROOT/backend"
  local boot="$SRC/personal-server/LeonPro_backend/SpringBoot"
  need_dir "$rt"
  cp -a "$tmp/app.jar" "$rt/app.jar"
  cp -a "$boot/Dockerfile" "$rt/Dockerfile"
  cp -a "$boot/docker-compose.yml" "$rt/docker-compose.yml"
  cp -a "$boot/.dockerignore" "$rt/.dockerignore"
  rm -rf "$tmp"
  log "启动 leonpro-backend（镜像已是 $tag，compose 不再重建；保留 $rt/.env、uploads、data）"
  (cd "$rt" && docker compose up -d --no-build)
  local i code
  for i in $(seq 1 40); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://127.0.0.1:8089/ || true)"
    [[ -n "$code" && "$code" != "000" ]] && break
    sleep 2
  done
  [[ -n "${code:-}" && "$code" != "000" ]] || die "8089 没有在时间内起来，看：docker logs --tail 80 leonpro-backend"
  log "后端已响应 HTTP $code"
}

swap_dist() {
  local name="$1" src_dist="$2" dest="$3"
  [[ -f "$src_dist/index.html" ]] || die "$name 构建结果里没有 index.html"
  local stage parent base
  stage="$(mktemp -d)"
  cp -a "$src_dist"/. "$stage/"
  chmod -R a+rX "$stage"
  parent="$(dirname "$dest")"
  base="$(basename "$dest")"
  sudo mkdir -p "$parent"
  sudo rm -rf "$parent/$base.new"
  sudo mv "$stage" "$parent/$base.new"
  if [[ -d "$dest" ]]; then
    sudo rm -rf "$parent/$base.prev"
    sudo mv "$dest" "$parent/$base.prev"
  fi
  sudo mv "$parent/$base.new" "$dest"
  log "$name 已换成 $dest"
}

extract_static() {
  local tag="$1" name="$2" dest="$3" stage
  stage="$(mktemp -d)"
  # 目标目录还不存在时，docker cp 会把 /out 里的内容放进去，而不是再套一层 out。
  copy_from_image "$tag" "/out" "$stage/dist"
  swap_dist "$name" "$stage/dist" "$dest"
  rm -rf "$stage"
  docker image rm "$tag" >/dev/null 2>&1 || true
}

deploy_web() {
  local tag="leonpro-web-dist:build"
  docker_build "$TOOL_DIR/docker/web.Dockerfile" "$tag"
  extract_static "$tag" "用户端" "/var/www/leonpro-web"
}

deploy_admin() {
  ensure_elite_task
  local tag="leonpro-admin-dist:build"
  docker_build "$TOOL_DIR/docker/admin.Dockerfile" "$tag"
  extract_static "$tag" "管理端" "/var/www/leonpro-admin"
}

deploy_nginx() {
  local install="$SRC/personal-server/bootstrap/nginx-install.sh"
  local conf="$SRC/personal-server/bootstrap/nginx-leonpro.conf"
  [[ -f "$install" && -f "$conf" ]] || die "分支里没有 nginx 配置"
  log "安装 nginx 配置（有线上独有的 location / HTTPS 时会停下，不会 --force）"
  sudo bash "$install" "$conf"
}

smoke() {
  local path code
  for path in / /admin/ /prod-api/; do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1$path" || echo ERR)"
    log "检查 GET $path -> $code"
  done
}

run_program() {
  case "$1" in
    backend) deploy_backend ;;
    web) deploy_web ;;
    admin) deploy_admin ;;
    nginx) deploy_nginx ;;
    *) die "没有部署动作：$1" ;;
  esac
}

take_branch_arg() {
  local arg="$1"
  if [[ -z "$BRANCH" ]]; then
    BRANCH="$arg"
  else
    EXPLICIT+=("$arg")
  fi
}

parse_args() {
  DRY=0
  ALL=0
  YES=0
  STATUS=0
  EXPLICIT=()
  BRANCH=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -h|--help) usage; exit 0 ;;
      --dry-run) DRY=1; shift ;;
      --all) ALL=1; shift ;;
      --yes) YES=1; shift ;;
      --status) STATUS=1; shift ;;
      --) shift; break ;;
      -*) die "未知参数：$1" ;;
      *)
        take_branch_arg "$1"
        shift
        ;;
    esac
  done
  while [[ $# -gt 0 ]]; do
    take_branch_arg "$1"
    shift
  done
}

main() {
  local DRY=0 ALL=0 YES=0 STATUS=0
  local -a EXPLICIT=()
  BRANCH=""
  parse_args "$@"

  if [[ "$STATUS" == 1 ]]; then
    show_status
    exit 0
  fi
  [[ -n "$BRANCH" ]] || die "要指定分支。看 --help"
  valid_branch "$BRANCH" || die "分支名不合法：$BRANCH"
  local prog
  for prog in "${EXPLICIT[@]+"${EXPLICIT[@]}"}"; do
    valid_program "$prog" || die "未知程序：$prog（可选：${PROGRAMS[*]}）"
  done

  need_dir "$STATE_DIR"
  touch "$LOG"
  exec 9>"$STATE_DIR/lock"
  flock -n 9 || die "已有部署在进行"

  ensure_repo
  plan_programs

  if [[ "$NGINX_NEEDS_BASELINE" == 1 ]]; then
    log "nginx 还没有部署记录，这次不改线上配置。真正部署时会记下当前提交，之后只在 nginx 文件变化时安装。要现在安装请写上 nginx 或加 --all"
  fi
  if [[ ${#PLANNED[@]} -eq 0 ]]; then
    if [[ "$NGINX_NEEDS_BASELINE" == 1 && "$DRY" != 1 ]]; then
      mark_deployed nginx "$SHA" "$BRANCH"
      log "已记下 nginx 的提交 ${SHA:0:12}，未安装配置"
    fi
    log "分支 $BRANCH（${SHA:0:12}）相对上次部署没有要更新的程序"
    exit 0
  fi
  log "分支 $BRANCH（${SHA:0:12}）将部署：${PLANNED[*]}"
  if [[ "$DRY" == 1 ]]; then
    log "dry-run，未检出、未构建、未替换线上文件"
    exit 0
  fi
  if [[ "$YES" != 1 ]]; then
    if [[ ! -t 0 ]]; then
      die "非交互环境请加 --yes"
    fi
    local ans
    read -r -p "输入 yes 继续：" ans
    [[ "$ans" == "yes" ]] || die "已取消"
  fi

  checkout_src
  for prog in "${PLANNED[@]}"; do
    log "---- $prog ----"
    run_program "$prog"
    mark_deployed "$prog" "$SHA" "$BRANCH"
  done
  if [[ "$NGINX_NEEDS_BASELINE" == 1 ]]; then
    mark_deployed nginx "$SHA" "$BRANCH"
    log "已记下 nginx 的提交 ${SHA:0:12}，未安装配置"
  fi
  smoke
  log "完成"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
args=()
remote=0
for arg in "$@"; do
  if [[ "$arg" == "--remote" ]]; then
    remote=1
  else
    args+=("$arg")
  fi
done
if [[ "$remote" == 1 ]]; then
  remote_launch "${args[@]+"${args[@]}"}"
  exit $?
fi

main "$@"
fi

#!/usr/bin/env bash
# LeonPro 前端一键部署。用法见同目录 README.md
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"
ENV_FILE="$DEPLOY_DIR/deploy.env"
DIST_DIR="$FRONTEND_DIR/dist"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少 $ENV_FILE ，请先复制 deploy.env.example 为 deploy.env 并填写服务器信息" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DEPLOY_HOST:?}"
: "${DEPLOY_USER:?}"
# 管理端只部署到自己的目录，不再读旧的 DEPLOY_REMOTE_DIR（那是站点根目录，现在归用户端）
DEPLOY_REMOTE_DIR="${DEPLOY_ADMIN_REMOTE_DIR:-/var/www/leonpro-admin}"
case "$DEPLOY_REMOTE_DIR" in /var/www/?*) ;; *) echo "DEPLOY_ADMIN_REMOTE_DIR 必须在 /var/www/ 下：$DEPLOY_REMOTE_DIR" >&2; exit 1 ;; esac
DEPLOY_PORT="${DEPLOY_PORT:-22}"
SKIP_BUILD="${SKIP_BUILD:-0}"
DEPLOY_PASSWORD="${DEPLOY_PASSWORD:-}"
DEPLOY_SSH_KEY="${DEPLOY_SSH_KEY:-}"

COMMON_OPTS=(-o StrictHostKeyChecking=accept-new)
SSH_OPTS=(-p "$DEPLOY_PORT" "${COMMON_OPTS[@]}")
SCP_OPTS=(-P "$DEPLOY_PORT" "${COMMON_OPTS[@]}")
SSH_BIN=(ssh)
SCP_BIN=(scp)

if [[ -n "$DEPLOY_SSH_KEY" ]]; then
  SSH_OPTS+=(-i "$DEPLOY_SSH_KEY")
  SCP_OPTS+=(-i "$DEPLOY_SSH_KEY")
elif [[ -n "$DEPLOY_PASSWORD" ]]; then
  SSH_OPTS+=(-o PreferredAuthentications=password -o PubkeyAuthentication=no)
  SCP_OPTS+=(-o PreferredAuthentications=password -o PubkeyAuthentication=no)
  if command -v sshpass >/dev/null 2>&1; then
    export SSHPASS="$DEPLOY_PASSWORD"
    SSH_BIN=(sshpass -e ssh)
    SCP_BIN=(sshpass -e scp)
  else
    export DEPLOY_PASSWORD
    export SSH_ASKPASS="$DEPLOY_DIR/askpass.sh"
    export SSH_ASKPASS_REQUIRE=force
    export DISPLAY="${DISPLAY:-:0}"
    chmod +x "$DEPLOY_DIR/askpass.sh" || true
    SSH_BIN=(setsid ssh)
    SCP_BIN=(setsid scp)
  fi
fi

REMOTE="${DEPLOY_USER}@${DEPLOY_HOST}"

if [[ "$SKIP_BUILD" != "1" ]]; then
  echo "pnpm 构建..."
  cd "$FRONTEND_DIR"
  pnpm run build-only
fi

if [[ ! -f "$DIST_DIR/index.html" ]]; then
  echo "未找到 $DIST_DIR/index.html" >&2
  exit 1
fi

echo "上传 dist 到 $DEPLOY_REMOTE_DIR ..."
"${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "if sudo -n true 2>/dev/null; then sudo mkdir -p '$DEPLOY_REMOTE_DIR' && sudo chown -R '$DEPLOY_USER':'$DEPLOY_USER' '$DEPLOY_REMOTE_DIR'; else mkdir -p '$DEPLOY_REMOTE_DIR'; fi && rm -rf '$DEPLOY_REMOTE_DIR'/*"
tar -cf - -C "$DIST_DIR" . | "${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "tar -xf - -C '$DEPLOY_REMOTE_DIR'"

# nginx 配置由后端统一部署，这里不再改 nginx

echo "完成：管理端已放到 ${DEPLOY_HOST}:$DEPLOY_REMOTE_DIR，访问 http://${DEPLOY_HOST}/admin/"

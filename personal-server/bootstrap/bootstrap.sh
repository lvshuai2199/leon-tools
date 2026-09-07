#!/usr/bin/env bash
# Upload this directory and run bootstrap-server.sh with sudo.
# Copy env.example to bootstrap.env first.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$DEPLOY_DIR/bootstrap.env"
REMOTE_DIR="/tmp/leonpro-bootstrap"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少 $ENV_FILE ，请先复制 env.example 为 bootstrap.env" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DEPLOY_HOST:?}"
: "${DEPLOY_USER:?}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
DEPLOY_PASSWORD="${DEPLOY_PASSWORD:-}"
DEPLOY_SSH_KEY="${DEPLOY_SSH_KEY:-}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-Root@123456}"
MYSQL_PUBLISH="${MYSQL_PUBLISH:-127.0.0.1:3306:3306}"
DOCKER_MIRROR="${DOCKER_MIRROR:-}"
SETUP_UFW="${SETUP_UFW:-0}"

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
    echo "密码登录需要 sshpass：sudo apt-get install -y sshpass" >&2
    exit 1
  fi
fi

REMOTE="${DEPLOY_USER}@${DEPLOY_HOST}"

echo "上传 bootstrap 到 ${REMOTE}:${REMOTE_DIR}"
"${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "mkdir -p '$REMOTE_DIR'"

shopt -s nullglob
for f in "$DEPLOY_DIR"/*; do
  base="$(basename "$f")"
  [[ -f "$f" ]] || continue
  [[ "$base" == "bootstrap.env" ]] && continue
  "${SCP_BIN[@]}" "${SCP_OPTS[@]}" "$f" "${REMOTE}:${REMOTE_DIR}/${base}"
done

REMOTE_ENV=$(printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_PUBLISH=%s\nDOCKER_MIRROR=%s\nSETUP_UFW=%s\n' \
  "$MYSQL_ROOT_PASSWORD" "$MYSQL_PUBLISH" "$DOCKER_MIRROR" "$SETUP_UFW")
B64=$(printf '%s' "$REMOTE_ENV" | base64 -w0 2>/dev/null || printf '%s' "$REMOTE_ENV" | base64)
"${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "printf '%s' '$B64' | base64 -d > '$REMOTE_DIR/bootstrap.env'"

echo "在服务器用 sudo 执行 bootstrap-server.sh"
"${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "chmod +x '$REMOTE_DIR/bootstrap-server.sh' && if sudo -n true 2>/dev/null; then sudo bash '$REMOTE_DIR/bootstrap-server.sh'; else echo '$DEPLOY_PASSWORD' | sudo -S bash '$REMOTE_DIR/bootstrap-server.sh'; fi"

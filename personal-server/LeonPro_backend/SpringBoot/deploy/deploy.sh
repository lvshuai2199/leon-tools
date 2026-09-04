#!/usr/bin/env bash
# LeonPro 后端一键部署。用法见同目录 README.md
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
SPRINGBOOT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"
ENV_FILE="$DEPLOY_DIR/deploy.env"
JAR_PATH="$DEPLOY_DIR/dist/app.jar"

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
: "${DEPLOY_REMOTE_DIR:?}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
SKIP_BUILD="${SKIP_BUILD:-0}"
DOCKER_UP="${DOCKER_UP:-0}"
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
  echo "Maven 打包..."
  cd "$SPRINGBOOT_DIR"
  if [[ -x ./mvnw ]]; then
    ./mvnw -B clean package -Dmaven.test.skip=true
  else
    mvn -B clean package -Dmaven.test.skip=true
  fi
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "未找到 $JAR_PATH ，请先成功执行 mvn package" >&2
  exit 1
fi

echo "在服务器创建目录 $DEPLOY_REMOTE_DIR ..."
"${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "if sudo -n true 2>/dev/null; then sudo mkdir -p '$DEPLOY_REMOTE_DIR' && sudo chown -R '$DEPLOY_USER':'$DEPLOY_USER' '$DEPLOY_REMOTE_DIR'; else mkdir -p '$DEPLOY_REMOTE_DIR'; fi"

echo "拷贝 jar 到 ${REMOTE}:$DEPLOY_REMOTE_DIR/app.jar ..."
"${SCP_BIN[@]}" "${SCP_OPTS[@]}" "$JAR_PATH" "${REMOTE}:${DEPLOY_REMOTE_DIR}/app.jar"

echo "拷贝 Docker 文件..."
"${SCP_BIN[@]}" "${SCP_OPTS[@]}" \
  "$SPRINGBOOT_DIR/Dockerfile" \
  "$SPRINGBOOT_DIR/docker-compose.yml" \
  "$SPRINGBOOT_DIR/.dockerignore" \
  "${REMOTE}:${DEPLOY_REMOTE_DIR}/"

if [[ "$DOCKER_UP" == "1" ]]; then
  echo "在服务器启动 Docker 容器..."
  "${SSH_BIN[@]}" "${SSH_OPTS[@]}" "$REMOTE" "cd '$DEPLOY_REMOTE_DIR' && if sudo -n true 2>/dev/null; then sudo docker compose up -d --build || sudo docker-compose up -d --build; else docker compose up -d --build || docker-compose up -d --build; fi"
fi

echo "完成：jar 已放到 ${DEPLOY_HOST}:$DEPLOY_REMOTE_DIR"
if [[ "$DOCKER_UP" != "1" ]]; then
  echo "稍后在服务器执行：cd $DEPLOY_REMOTE_DIR && docker compose up -d --build"
fi

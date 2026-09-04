#!/usr/bin/env bash
# LeonPro 新服务器一次性初始化：Nginx、Docker、MySQL8 容器、目录。
# 在目标 Linux（Ubuntu/Debian）上以 root 执行。可重复运行。
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "请用 root 执行：sudo bash $0"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/bootstrap.env"
MYSQL_DIR="/opt/leonpro/mysql"
BACKEND_DIR="/opt/leonpro/backend"
WWW_DIR="/var/www/leonpro"
TOOL_DIR="/var/www/tool"
NGINX_CONF="/etc/nginx/sites-available/default"

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-Root@123456}"
MYSQL_PUBLISH="${MYSQL_PUBLISH:-127.0.0.1:3306:3306}"
DOCKER_MIRROR="${DOCKER_MIRROR:-}"
SETUP_UFW="${SETUP_UFW:-0}"

if [[ -f "${ENV_FILE}" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key%"${key##*[![:space:]]}"}"
    key="${key#"${key%%[![:space:]]*}"}"
    export "${key}=${value}"
  done < "${ENV_FILE}"
fi

log() { echo "[bootstrap] $*"; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1
}

if [[ ! -f /etc/os-release ]]; then
  echo "无法识别系统，仅支持 Ubuntu/Debian。"
  exit 1
fi
# shellcheck disable=SC1091
. /etc/os-release
case "${ID}" in
  ubuntu|debian) ;;
  *)
    echo "当前系统 ${ID} 未适配，请用 Ubuntu/Debian。"
    exit 1
    ;;
esac

export DEBIAN_FRONTEND=noninteractive
if need_cmd timedatectl; then
  timedatectl set-timezone Asia/Shanghai || true
fi

log "安装基础包"
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release apt-transport-https \
  unzip tar git ufw

log "安装 Nginx"
apt-get install -y nginx
systemctl enable nginx
mkdir -p "${WWW_DIR}" "${TOOL_DIR}"
if [[ ! -f "${WWW_DIR}/index.html" ]]; then
  cat >"${WWW_DIR}/index.html" <<'HTML'
<!doctype html><meta charset="utf-8"><title>LeonPro</title>
<p>Nginx 已就绪。请部署前端到 /var/www/leonpro。</p>
HTML
fi

NGINX_SRC="${SCRIPT_DIR}/nginx-leonpro.conf"
if [[ -f "${NGINX_SRC}" ]]; then
  cp "${NGINX_SRC}" "${NGINX_CONF}"
fi
nginx -t
systemctl restart nginx

try_docker_ce() {
  local gpg_url="$1"
  local repo_url="$2"
  local tmp
  tmp="$(mktemp)"
  if ! curl -fsSL --connect-timeout 15 --max-time 60 "${gpg_url}" -o "${tmp}" || [[ ! -s "${tmp}" ]]; then
    rm -f "${tmp}"
    return 1
  fi
  gpg --dearmor <"${tmp}" >/etc/apt/keyrings/docker.gpg || { rm -f "${tmp}"; return 1; }
  chmod a+r /etc/apt/keyrings/docker.gpg
  rm -f "${tmp}"
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] ${repo_url} ${VERSION_CODENAME} stable" \
    >/etc/apt/sources.list.d/docker.list
  apt-get update -y || return 1
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin docker-buildx-plugin || return 1
  return 0
}

log "安装 Docker"
if ! need_cmd docker; then
  install -m 0755 -d /etc/apt/keyrings
  rm -f /etc/apt/keyrings/docker.gpg /etc/apt/sources.list.d/docker.list
  if try_docker_ce \
      "https://mirrors.cloud.tencent.com/docker-ce/linux/${ID}/gpg" \
      "https://mirrors.cloud.tencent.com/docker-ce/linux/${ID}"; then
    log "已从腾讯云 Docker CE 源安装"
  elif try_docker_ce \
      "https://mirrors.aliyun.com/docker-ce/linux/${ID}/gpg" \
      "https://mirrors.aliyun.com/docker-ce/linux/${ID}"; then
    log "已从阿里云 Docker CE 源安装"
  else
    log "镜像源失败，改用发行版 docker.io"
    rm -f /etc/apt/sources.list.d/docker.list /etc/apt/keyrings/docker.gpg
    apt-get install -y docker.io docker-compose-v2 \
      || apt-get install -y docker.io docker-compose-plugin \
      || apt-get install -y docker.io
  fi
else
  log "Docker 已存在，跳过安装"
  apt-get install -y docker-compose-plugin docker-compose-v2 2>/dev/null || true
fi

systemctl enable docker
systemctl start docker

if [[ -n "${SUDO_USER:-}" && "${SUDO_USER}" != "root" ]]; then
  usermod -aG docker "${SUDO_USER}" || true
  log "已将 ${SUDO_USER} 加入 docker 组（需重新登录后生效）"
fi

mkdir -p /etc/docker
if [[ ! -f /etc/docker/daemon.json ]]; then
  if [[ -z "${DOCKER_MIRROR}" ]]; then
    cat >/etc/docker/daemon.json <<'JSON'
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.m.daocloud.io"
  ]
}
JSON
    log "已写入腾讯云/DaoCloud 镜像加速"
  else
    cat >/etc/docker/daemon.json <<JSON
{
  "registry-mirrors": ["${DOCKER_MIRROR}"]
}
JSON
    log "已写入 Docker 加速器 ${DOCKER_MIRROR}"
  fi
  systemctl restart docker
else
  log "已有 /etc/docker/daemon.json，不覆盖"
fi

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif need_cmd docker-compose; then
    docker-compose "$@"
  else
    echo "未找到 docker compose"
    exit 1
  fi
}

log "准备 MySQL 目录与 compose"
mkdir -p "${MYSQL_DIR}/data" "${MYSQL_DIR}/init" "${BACKEND_DIR}"
cp "${SCRIPT_DIR}/init-db.sql" "${MYSQL_DIR}/init/01-databases.sql"
cp "${SCRIPT_DIR}/docker-compose.mysql.yml" "${MYSQL_DIR}/docker-compose.yml"

cat >"${MYSQL_DIR}/.env" <<EOF
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
MYSQL_PUBLISH=${MYSQL_PUBLISH}
EOF
chmod 600 "${MYSQL_DIR}/.env"

cd "${MYSQL_DIR}"
if docker ps -a --format '{{.Names}}' | grep -qx mysql8; then
  log "容器 mysql8 已存在，执行 compose up 以对齐配置（数据卷不删）"
fi
compose up -d

log "等待 MySQL 就绪"
for i in $(seq 1 60); do
  if docker exec mysql8 mysqladmin ping -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null 2>&1; then
    break
  fi
  if [[ "${i}" -eq 60 ]]; then
    echo "MySQL 启动超时，查看：docker logs mysql8"
    exit 1
  fi
  sleep 2
done

docker exec -i mysql8 mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < "${MYSQL_DIR}/init/01-databases.sql"

if [[ "${SETUP_UFW}" == "1" ]]; then
  log "配置 ufw"
  ufw allow OpenSSH || ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw --force enable
fi

log "完成"
echo "----------------------------------------"
echo "Nginx:     systemctl status nginx"
echo "Docker:    docker ps"
echo "MySQL:     容器 mysql8，映射 ${MYSQL_PUBLISH}"
echo "库:        leonpro_db_prod / leonpro_db_dev"
echo "前端目录:  ${WWW_DIR}"
echo "后端目录:  ${BACKEND_DIR}"
echo "Redis:     随后端 docker compose 一起启动，无需本脚本安装"
echo
echo "接下来："
echo "  1. 旧机导出：docker exec mysql8 mysqldump -uroot -p --databases leonpro_db_prod leonpro_db_dev > dump.sql"
echo "  2. 新机导入：docker exec -i mysql8 mysql -uroot -p < dump.sql"
echo "  3. 本机执行前端/后端 deploy.ps1 发布应用"
echo "----------------------------------------"

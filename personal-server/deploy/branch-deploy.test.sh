#!/usr/bin/env bash
# 选择「这次分支要部署哪些程序」的检查，不连服务器、不构建。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export LEONPRO_ROOT="$tmp/leon"
export LEONPRO_SRC="$tmp/repo"
export LEONPRO_STATE="$tmp/state"
# shellcheck disable=SC1091
source "$ROOT_DIR/branch-deploy.sh"

fail() { printf 'FAIL %s\n' "$*" >&2; exit 1; }

mapped() {
  if [[ $# -eq 0 ]]; then
    programs_for_files
  else
    printf '%s\n' "$@" | programs_for_files | paste -sd, -
  fi
}

[[ "$(mapped 'personal-server/LeonPro_backend/SpringBoot/src/A.java')" == "backend" ]] || fail backend
[[ "$(mapped 'personal-server/LeonPro_frontend/frontend_phone/src/pages/notes/index.vue')" == "web" ]] || fail web
[[ "$(mapped 'personal-server/LeonPro_frontend/shared/theme.scss')" == "web,admin" ]] || fail shared
[[ "$(mapped 'personal-server/LeonPro_frontend/frontend_phone/src/router/menus.json')" == "backend,web" ]] || fail app-menu
[[ "$(mapped 'personal-server/LeonPro_frontend/vue3_frontend/src/router/menus.json')" == "backend,admin" ]] || fail admin-menu
[[ "$(mapped 'personal-server/bootstrap/nginx-leonpro.conf')" == "nginx" ]] || fail nginx
[[ "$(mapped 'personal-server/bootstrap/nginx-install.sh')" == "nginx" ]] || fail nginx-install
[[ "$(mapped 'README.md')" == "" ]] || fail readme
[[ "$(mapped 'personal-server/LeonPro_backend_extra/x.java')" == "" ]] || fail sibling
[[ "$(mapped \
  'personal-server/LeonPro_backend/SpringBoot/src/A.java' \
  'personal-server/LeonPro_frontend/frontend_phone/src/a.vue')" == "backend,web" ]] || fail order

valid_branch 'dev' || fail branch-dev
valid_branch 'cursor/notes-repo-sync-07b3' || fail branch-slash
valid_branch 'a..b' && fail branch-dotdot
valid_branch '/tmp' && fail branch-abs
valid_program backend || fail prog
valid_program h5 && fail prog-unknown

# 两个提交：后端文件变了，用户端没变。已记录的 web 停在新提交上，backend 停在旧提交上。
mkdir -p "$LEONPRO_SRC"
git -C "$LEONPRO_SRC" init -q
git -C "$LEONPRO_SRC" config user.email test@example.com
git -C "$LEONPRO_SRC" config user.name test
mkdir -p "$LEONPRO_SRC/personal-server/LeonPro_backend/SpringBoot" \
  "$LEONPRO_SRC/personal-server/LeonPro_frontend/frontend_phone"
echo a > "$LEONPRO_SRC/personal-server/LeonPro_backend/SpringBoot/A.java"
echo w > "$LEONPRO_SRC/personal-server/LeonPro_frontend/frontend_phone/a.vue"
git -C "$LEONPRO_SRC" add .
git -C "$LEONPRO_SRC" commit -q -m first
old="$(git -C "$LEONPRO_SRC" rev-parse HEAD)"
echo b > "$LEONPRO_SRC/personal-server/LeonPro_backend/SpringBoot/A.java"
git -C "$LEONPRO_SRC" add .
git -C "$LEONPRO_SRC" commit -q -m second
SHA="$(git -C "$LEONPRO_SRC" rev-parse HEAD)"
mkdir -p "$LEONPRO_STATE"
printf 'sha.backend=%s\nsha.web=%s\nsha.admin=%s\nsha.nginx=%s\n' "$old" "$SHA" "$SHA" "$SHA" > "$LEONPRO_STATE/state"
ALL=0
EXPLICIT=()
plan_programs
[[ "${PLANNED[*]}" == "backend" ]] || fail "plan got: ${PLANNED[*]}"

rm -f "$LEONPRO_STATE/state"
plan_programs
[[ "${PLANNED[*]}" == "backend web admin nginx" ]] || fail "first got: ${PLANNED[*]}"

ALL=1
plan_programs
[[ "${PLANNED[*]}" == "backend web admin nginx" ]] || fail "all got: ${PLANNED[*]}"

(
  parse_args --yes --dry-run -- dev web admin
  [[ "$YES" == 1 && "$DRY" == 1 && "$ALL" == 0 && "$BRANCH" == dev && "${EXPLICIT[*]}" == "web admin" ]] || exit 1
)
(
  parse_args --all --yes -- cursor/server-branch-deploy-07b3 backend
  [[ "$ALL" == 1 && "$YES" == 1 && "$BRANCH" == cursor/server-branch-deploy-07b3 && "${EXPLICIT[*]}" == backend ]] || exit 1
)
(
  parse_args dev nginx
  [[ "$BRANCH" == dev && "${EXPLICIT[*]}" == nginx && "$YES" == 0 ]] || exit 1
)
(
  call_like_main() {
    local DRY=0 ALL=0 YES=0 STATUS=0
    local -a EXPLICIT=()
    BRANCH=""
    parse_args --yes -- cursor/notes-repo-sync-07b3
    [[ "$YES" == 1 && "$BRANCH" == cursor/notes-repo-sync-07b3 && ${#EXPLICIT[@]} -eq 0 ]] || exit 1
  }
  call_like_main
)
(
  envf="$tmp/env"
  printf 'DEPLOY_USER=ubuntu\nDEPLOY_SSH_KEY=\n' > "$envf"
  DEPLOY_HOST=already
  DEPLOY_USER=
  DEPLOY_PORT=
  DEPLOY_PASSWORD=already
  DEPLOY_SSH_KEY=preset
  read_env_file "$envf"
  [[ "$DEPLOY_USER" == ubuntu && "$DEPLOY_SSH_KEY" == preset && "$DEPLOY_PASSWORD" == already ]] || exit 1
)

echo OK

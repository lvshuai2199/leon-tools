#!/usr/bin/env bash
# CAN 测试工具启动：优先 .venv；失败/超时立刻落到 --user
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"
export PYTHONPATH="$ROOT${PYTHONPATH:+:$PYTHONPATH}"

PY="${PYTHON:-python3}"
USE_USER=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --user|--no-venv) USE_USER=1 ;;
    *) ARGS+=("$a") ;;
  esac
done
if [[ "${CAN_TESTER_NO_VENV:-}" == "1" ]]; then
  USE_USER=1
fi

# curl/wget/pip/venv 单步最长秒数，避免卡死
STEP_TIMEOUT="${CAN_TESTER_STEP_TIMEOUT:-20}"

run_timeout() {
  # usage: run_timeout <cmd...>
  if command -v timeout >/dev/null 2>&1; then
    timeout --foreground "$STEP_TIMEOUT" "$@"
  else
    "$@"
  fi
}

pip_install_user() {
  if $PY -m pip install --user -r requirements.txt; then
    return 0
  fi
  echo "[can_tester] pip --user 失败（可能是 PEP 668），改用 --break-system-packages --user …" >&2
  $PY -m pip install --user --break-system-packages -r requirements.txt
}

run_with_user() {
  echo "[can_tester] 使用系统 Python（--user，无发行版 venv 包）: $($PY -V 2>&1)" >&2
  if ! $PY -c "import can" 2>/dev/null; then
    echo "[can_tester] 正在 pip install --user -r requirements.txt …" >&2
    pip_install_user
  fi
  if ! $PY -c "import tkinter" 2>/dev/null; then
    echo "[can_tester] 警告: 未检测到 tkinter，GUI 可能无法打开。请安装: sudo apt install python3-tk" >&2
  fi
  exec $PY -m can_tester "${ARGS[@]}"
}

fallback_user() {
  local why="${1:-venv 不可用}"
  echo "[can_tester] ${why}" >&2
  echo "[can_tester] 已切换备用启动（./run.sh --user）" >&2
  rm -rf .venv 2>/dev/null || true
  run_with_user
}

# 无 ensurepip 时短试：venv --without-pip + get-pip（带超时，失败立刻备用）
bootstrap_venv_without_ensurepip() {
  echo "[can_tester] 尝试 venv --without-pip + 引导 pip（最多 ${STEP_TIMEOUT}s/步）…" >&2
  rm -rf .venv
  run_timeout $PY -m venv --without-pip .venv || return 1
  local getpip
  getpip="$(mktemp)"
  if command -v curl >/dev/null 2>&1; then
    run_timeout curl -fsSL --connect-timeout 5 --max-time "$STEP_TIMEOUT" \
      https://bootstrap.pypa.io/get-pip.py -o "$getpip" || { rm -f "$getpip"; return 1; }
  elif command -v wget >/dev/null 2>&1; then
    run_timeout wget -q --timeout="$STEP_TIMEOUT" -O "$getpip" \
      https://bootstrap.pypa.io/get-pip.py || { rm -f "$getpip"; return 1; }
  else
    echo "[can_tester] 无 curl/wget，跳过 get-pip 引导" >&2
    rm -f "$getpip"
    return 1
  fi
  run_timeout .venv/bin/python "$getpip" || { rm -f "$getpip"; return 1; }
  rm -f "$getpip"
  run_timeout .venv/bin/pip install -r requirements.txt || return 1
  return 0
}

if [[ "$USE_USER" -eq 1 ]]; then
  run_with_user
fi

if [[ -x .venv/bin/python ]] && .venv/bin/python -c "import can" 2>/dev/null; then
  exec .venv/bin/python -m can_tester "${ARGS[@]}"
fi

if [[ -d .venv ]] && [[ ! -x .venv/bin/python ]]; then
  rm -rf .venv
fi

echo "[can_tester] 创建虚拟环境 .venv …" >&2
VENV_ERR="$(mktemp)"
if run_timeout $PY -m venv .venv 2>"$VENV_ERR"; then
  if run_timeout .venv/bin/pip install -r requirements.txt; then
    rm -f "$VENV_ERR"
    exec .venv/bin/python -m can_tester "${ARGS[@]}"
  fi
fi

# 标准 venv 失败：打印摘要后短试 without-pip；再失败立刻 --user
if [[ -s "$VENV_ERR" ]]; then
  echo "[can_tester] venv 失败摘要：" >&2
  tail -n 8 "$VENV_ERR" >&2 || true
fi
rm -f "$VENV_ERR"

if bootstrap_venv_without_ensurepip; then
  exec .venv/bin/python -m can_tester "${ARGS[@]}"
fi

fallback_user "venv / get-pip 超时或失败"

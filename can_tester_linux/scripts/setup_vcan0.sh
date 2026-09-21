#!/usr/bin/env bash
set -euo pipefail
if [[ "$(id -u)" -ne 0 ]]; then exec sudo -- "$0" "$@"; fi
command -v ip >/dev/null || { echo "需要 iproute2" >&2; exit 1; }
command -v modprobe >/dev/null && modprobe vcan || true
ip link show vcan0 >/dev/null 2>&1 || ip link add dev vcan0 type vcan
ip link set up vcan0
ip -details link show vcan0
echo "vcan0 已就绪。"

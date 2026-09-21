#!/usr/bin/env bash
# 不依赖 venv：系统 Python + pip --user
set -euo pipefail
cd "$(dirname "$0")"
exec ./run.sh --user "$@"

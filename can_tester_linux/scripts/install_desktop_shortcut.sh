#!/bin/bash
# 在解压后的 can_tester_linux 目录里执行，把快捷方式放到桌面。
# 快捷方式指向本目录里的程序，不能单独把 .desktop 拷走。
set -euo pipefail
cd "$(dirname "$0")"
APP="$(pwd)"
if [[ ! -x "$APP/python/bin/python3" ]]; then
  echo "找不到 $APP/python/bin/python3" >&2
  echo "请在离线包解压后的 can_tester_linux 目录里运行本脚本。" >&2
  exit 1
fi
DESK="$(xdg-user-dir DESKTOP 2>/dev/null || true)"
if [[ -z "$DESK" ]]; then
  DESK="$HOME/Desktop"
fi
mkdir -p "$DESK"
TARGET="$DESK/CAN测试工具.desktop"
cat > "$TARGET" << EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=CAN 通用监听 / 发送
Comment=离线启动，无需联网安装
Exec=$APP/python/bin/python3
Path=$APP
Icon=utilities-terminal
Terminal=false
Categories=Utility;
StartupNotify=true
EOF
chmod +x "$TARGET"
gio set "$TARGET" metadata::trusted true 2>/dev/null || true
echo "已放到桌面: $TARGET"
echo "若仍提示不受信任，在图标上右键选一次「允许启动」。"

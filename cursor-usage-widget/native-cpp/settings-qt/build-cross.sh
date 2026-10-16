#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
QT_PREFIX="${QT_PREFIX:-/workspace/qt-win/6.7.3/mingw_64}"
# Host moc wrappers if needed
for tool in moc uic rcc; do
  exe="$QT_PREFIX/bin/${tool}.exe"
  if [ -f "$exe" ] && file "$exe" 2>/dev/null | grep -qi PE; then
    mv "$exe" "${exe}.bak" 2>/dev/null || true
    printf '#!/bin/sh\nexec /usr/lib/qt6/libexec/%s "$@"\n' "$tool" > "$exe"
    chmod +x "$exe"
  fi
done
cmake -S "$ROOT" -B "$ROOT/build" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ROOT/toolchain-mingw64.cmake" \
  -DCMAKE_PREFIX_PATH="$QT_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$ROOT/build" -j"$(nproc)"
DEST="$ROOT/../qt-runtime"
mkdir -p "$DEST/platforms"
cp "$ROOT/build/CursorUsageSettings.exe" "$DEST/"
cp "$QT_PREFIX/bin/Qt6Core.dll" "$QT_PREFIX/bin/Qt6Gui.dll" "$QT_PREFIX/bin/Qt6Widgets.dll" "$DEST/"
cp "$QT_PREFIX/plugins/platforms/qwindows.dll" "$DEST/platforms/"
cp /usr/lib/gcc/x86_64-w64-mingw32/14-win32/libgcc_s_seh-1.dll "$DEST/"
cp /usr/lib/gcc/x86_64-w64-mingw32/14-win32/libstdc++-6.dll "$DEST/"
cp /usr/x86_64-w64-mingw32/lib/libwinpthread-1.dll "$DEST/"
echo "Staged -> $DEST"

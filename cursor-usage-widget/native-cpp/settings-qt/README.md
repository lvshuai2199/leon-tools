# CursorUsage Settings (Qt)

独立进程设置窗；托盘/用量环仍为 Win32（`CursorUsage.cpp`）。

## 架构
- `CursorUsageSettings.exe`：Qt Widgets 设置 UI
- 配置读写 `%APPDATA%\cursor-usage-widget\ui.ini`（与托盘共用）
- 变更后 `PostMessage(CursorUsageWidget, WM_APP+0x41)` 通知重载
- 清除：`PostMessage(..., WM_APP+0x42, flags)`，由托盘进程执行既有 `ClearCursorHistory`

## 交叉编译（Linux box → Windows）
需：mingw-w64、CMake、Ninja、Windows Qt（mingw_64）与 Linux host `moc`。

```bash
cmake -S . -B build -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=toolchain-mingw64.cmake \
  -DCMAKE_PREFIX_PATH=/path/to/Qt/6.x/mingw_64 \
  -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

产物与便携 DLL 放到 `../qt-runtime/`（勿提交大体积 DLL 进 git）。

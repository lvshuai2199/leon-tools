# Cursor 桌面用量条

贴在屏幕左右边缘的轻量小组件，读取本机已登录的 Cursor 会话，显示套餐用量和今日 Token。

## 发给别人安装

双击 `pack.bat`（或运行 `pack.ps1`）会先编译再打包。安装包在 `dist` 里，文件名带时间戳，例如：

```
cursor-usage-widget\dist\CursorUsage-Setup-20260911-143500.exe
```

每次打包文件名都不同，发给别人时不会和旧包重名。对方双击安装，选一个目录即可。无需 Visual Studio。卸载用安装目录里的 `卸载.bat`。

## 本机从源码安装

先编译 `native-cpp\build.bat`，再运行 `install.bat`。已装过可 `install.bat -InPlace`。

## 开发运行

```bat
cursor-usage-widget\run.bat
```

请先打开并登录 Cursor。小组件只读本机会话。

## 操作

- 点击展开，移开收起
- 拖动记住位置
- 右键：刷新、用量页、打开程序、显示大小、贴边、开机启动、退出
- 「打开程序」可搜索本机已装软件并勾选多项；已开则调窗，未开则启动

## 安装包（含设置窗）

双击 `pack.bat` 生成：

```
dist\CursorUsage-Setup-yyyyMMdd-HHmmss.exe
dist\CursorUsage-Setup.exe
```

安装包内含托盘 `CursorUsage.exe` 与便携 `qt-runtime\`（Qt DLL + `CursorUsageSettings.exe`），**不包含**本机 `%APPDATA%\cursor-usage-widget` 配置。

别人：双击安装包 → 选择目录（默认会在其下创建 `CursorUsage`）→ 自动开机启动并打开托盘。设置窗由托盘菜单打开，或运行安装目录下 `qt-runtime\CursorUsageSettings.exe`。卸载用安装目录「卸载.bat」。

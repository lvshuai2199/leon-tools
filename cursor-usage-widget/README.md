# Cursor 桌面用量条

贴在屏幕左右边缘的轻量小组件，读取本机已登录的 Cursor 会话，显示套餐用量和今日 Token。

## 发给别人安装

在本机编译后运行 `pack.ps1`，把生成的文件发给对方即可：

```
cursor-usage-widget\dist\CursorUsage-Setup.exe
```

对方双击安装，选一个目录即可。无需 Visual Studio。卸载用安装目录里的 `卸载.bat`。

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

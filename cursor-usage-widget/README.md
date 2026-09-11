# Cursor 桌面用量条

贴在屏幕左右边缘的轻量小组件，读取本机已登录的 Cursor 会话，显示套餐用量和今日 Token。

## 安装（开机自启）

先编译 `native-cpp\build.bat`（已有 `CursorUsage.exe` 可跳过），再双击：

```bat
cursor-usage-widget\install.bat
```

会弹出文件夹选择。确认后，在所选目录下创建 `CursorUsage` 文件夹并装入程序（如果选中的就是这个文件夹，则直接使用）。默认从 `%LOCALAPPDATA%` 开始，所以不改目录时仍装到 `%LOCALAPPDATA%\CursorUsage\`。

已经装过、只想覆盖更新时：

```bat
cursor-usage-widget\install.bat -InPlace
```

会按 `%APPDATA%\cursor-usage-widget\install.path` 里记下的目录重装，不再弹选择框。

实际安装路径记在 `install.path`。贴边位置、显示大小记在同目录的 `ui.ini`；右键「打开程序」勾选的启动项记在 `shortcuts.txt`。下次启动（含开机）仍用这些配置。

卸载：`uninstall.bat`。也可在条上右键勾选或取消「开机启动」。

## 开发运行

```bat
cursor-usage-widget\run.bat
```

请先打开并登录 Cursor。小组件只读本机会话，向 cursor.com 查询你自己的用量。

## 操作

- 点击展开，移开收起
- 拖动上下移动（会记住位置）
- 右键：刷新、用量页、打开程序、显示大小、左右/顶部贴边、开机启动、退出
- 「打开程序」→「管理启动程序」可搜索本机已装软件并一次勾选多项；点程序名时，已开则调出窗口，未开则启动

# CNC 上下料控制台 · UI 定稿标注稿（2026-10-01）

顺序：首页 → 工程编辑 → 程序 → 夹爪 → 料盘 → 信号 → 设置。通用规范先看 `00-global.md`；**默认主题是浅色**（标注稿均为浅色）；深色为备选，色值、可粘贴的变量块和对比度见 `00-dark-theme.md`。

| # | 页面 | 标注稿（带红色尺寸标注） | 补充状态图 | 规范 |
|---|---|---|---|---|
| 01 | 首页 | 01-home-1280x800.png / 01-home-1024x640.png | 01-home-log-1280x800.png（切换记录弹层）、01-home-video-1024x640.png（1024 监控浮窗） | 01-home.md |
| 02 | 工程编辑 | 02-project-1280x800.png / 02-project-1024x640.png | 02-project-config-1280x800.png（工程配置） | 02-project.md |
| 03 | 程序 | 03-program-1280x800.png / 03-program-1024x640.png | — | 03-program.md |
| 04 | 夹爪 | 04-gripper-1280x800.png / 04-gripper-1024x640.png | — | 04-gripper.md |
| 05 | 料盘 | 05-tray-1280x800.png / 05-tray-1024x640.png | 05-tray-readonly-1280x800.png（默认只读） | 05-tray.md |
| 06 | 信号 | 06-signal-1280x800.png / 06-signal-1024x640.png | 06-signal-list-1280x800.png（信号列表表格） | 06-signal.md |
| 07 | 设置 | 07-settings-1280x800.png / 07-settings-1024x640.png | — | 07-settings.md |

- `clean/`：同名无标注版本。
- `src/`：HTML/CSS 源文件（common.css、icons.js、shell.js、anno.js 标注层）、`shoot.py`（截图 + 自动校验）、`measure.py`（量尺寸）、`check-report.json`（校验结果：19 张全部通过——无横向溢出、首页无任何滚动、可点元素 ≥48）。
- 重新出图：`cd src && python3 shoot.py`。
- `dark/01-home-dark-1280x800.png`：首页深色（备选主题）干净图；`dark/dark-contrast-report.json`：逐元素对比度审计结果。任何页面加 `?theme=dark` 都会切到深色（`src/theme-dark.css` 覆盖变量）；深色出图加审计：`cd src && python3 dark_shoot.py`；浅色对比度审计：`python3 dark_shoot.py --light`。标注稿仍是浅色。
- `group-posts-draft.md`：7 条群消息草稿（未发送）。

## v3-module（10-02）
程序模块、引用、料盘自动主循环、首页料盘行：见 [v3-module/README.md](v3-module/README.md)。首页数据卡片已按 D2 把「料盘剩余」换成「报警 / 暂停次数」。

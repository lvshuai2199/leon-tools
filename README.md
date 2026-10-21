# LeonTools

个人小工具 monorepo（脚本、托盘、扩展、机器人辅助）。

相关仓库：

| 仓库 | 说明 |
|------|------|
| [lvshuai2199/leon-notes](https://github.com/lvshuai2199/leon-notes) | 个人笔记 |
| [lvshuai2199/personal-server](https://github.com/lvshuai2199/personal-server) | LeonPro 个人服务器（已独立） |

## 顶层目录

| 目录 | 说明 |
|------|------|
| `can_tester_linux/` | Linux CAN 测试工具 |
| `cursor-usage-widget/` | Cursor 用量托盘小工具 |
| `edge-start-hub/` | Edge 起始页扩展 |
| `git-picbed/` | Git 图床托盘 |
| `leonpro-dev-hub/` | LeonPro 本地开发托盘/入口 |
| `local-git/` | 本地 Git 辅助 |
| `stm32-servo/` | STM32 舵机固件与上位机 |
| `robot/` | 机器人相关（不拆散） |
| `robot/elite_script_send/` | Elite 脚本下发 |
| `robot/urdf-server/` | 本地 URDF 预览 |
| `archive/` | 历史归档（原 `history/`，只读；大 zip 暂留） |

根目录 `.venv/` 仅本机 Python 环境，**不提交**。打包产物 `build/`、`dist/`、`*.exe` 也不进 git；运行拷贝放 Softs。

## Softs 对应（本机 DESKTOP）

| 源码路径 | Softs 运行目录 |
|----------|----------------|
| `cursor-usage-widget/` | `D:\Softs\CursorUsage` |
| `git-picbed/` | `D:\Softs\git-picbed` |
| `leonpro-dev-hub/` | 无独立 Softs（`D:\Softs\leonpro-local` 是完整本地栈，不是本托盘） |
| `stm32-servo/` | `D:\Softs\Servo` |
| `robot/elite_script_send/` | `D:\Softs\elite-script-send` |
| `can_tester_linux/` | 无 Windows Softs（Linux 部署） |
| `edge-start-hub/` | 浏览器扩展，无 Softs |
| `local-git/` | 无独立 Softs |
| `robot/urdf-server/` | 本地预览，无 Softs |

整理约定：不改各工具业务逻辑；`archive/` 内删除需帅吕点头；push 另说。
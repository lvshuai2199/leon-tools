# 开发评审：「生成上下料程序 + 后续快速修改」能力与分支整合

> 评审时间：2026-10-07 23:2x（UTC+8）。只读评审，没有提交、合并、推送或部署，也没有改任何工作区。
> 依据：仓库 `lvshuai2199/cnc-station` 全部分支（PC 上 `git fetch --all --prune` 后打成 bundle，在 box 临时克隆 `/workspace/review-1007/repo` 里分析）；
> `D:\GitFiles\leon-tools\notes\cnc-station\` 下的《CNC上下料技术方案 2026-09-30》《产品方案-v2》（只读）。
> 合并试算只在 box 的一次性克隆 `/workspace/review-1007/tmpmerge` 里做（`git merge-tree` + `merge --no-commit`），没有碰 PC 上的仓库。
> 行号指 `feat/ui-v2-7menu`（dc0fe47）里的 `上下料/原型/cnc-prototype.html`，除非另注。

## 0. 结论先说

- **现阶段不能满足「生成一个能在机器人上跑的上下料程序」。** 现在有两块互不相连的东西：
  1. 原型 `cnc-prototype.html`（单文件 HTML，已上线 /cnc/）：配置模型做得比较完整（程序 / 料盘 / 夹爪按 id 引用、带版本、料盘自动主循环、待核对传递），但「生成 → 上传 → 加载 → 核对」是 `setTimeout` 动画，「文本视图」只是中文步骤清单，示教点用 `Math.random()` 填值，数据只存在浏览器 localStorage。
  2. 后端 `backend/`（FastAPI 骨架，自 9-01 起没动）：`script_generator.py` 只有一个写死的 3 点模板，输出的是自造伪代码（`MOVJ` / `CNC_SIGNAL` / `SUB`），**不是 ARCS Lua**；`script_sender.py` 用裸 TCP 推到 `:30001`，而仓库自己的架构规则（`.cursor/rules/cnc-load-unload-architecture.mdc`）和技术方案 §2/§6 都明确禁止这种做法用于 C5 生产。原型从不调用后端（没有任何 fetch / WebSocket / `/api/`）。
- **「后续快速修改」在配置层面基础不错**（料盘网格参数化、点位改一次全引用跟随、版本号 + 受影响节点标黄、待核对拦截开始），但缺工件实体、缺 A 类参数（换产只改的那几项）、缺多工程 / 导入导出 / 历史回滚，也没有把参数和程序骨架分开（技术方案 §4.1 的「配方寄存器 + 模板脚本」还没落地）。
- **分支很好整合**：`main ⊂ dev_ls ⊂ feat/ui-v2-7menu` 是一条直线，快进就行，零冲突。唯一有冲突的是 `cursor/tray-loop-program-modules-1dbc`（草稿 PR #1）：14 处冲突、约 600 行，全在核心模拟函数里，而且它的功能已被 ef00659/948cfbb 按 D37–D48 重新实现，**建议不合并、关闭 PR**。
- 仓库里**没有 `dev` 分支**（本地、远端都没有）。产品方案里写的「没有合进 dev」实际指的是不存在的分支；GitHub 默认分支是 `main`。

## 1. 分支盘点

`git fetch --all --prune` 后远端只有 4 个分支，另有 1 个打开的草稿 PR。

| 分支 | 末端提交 | 时间（UTC+8） | 作者 | 相对 main（落后/领先） | 相对 dev_ls | 相对 feat/ui-v2-7menu | 内容 |
|---|---|---|---|---|---|---|---|
| `main`（GitHub 默认） | 19a60b0 | 09-04 15:23 | lvshuai | 0 / 0 | 落后 11 | 落后 15 | FastAPI 后端骨架（f5bb900 起：采集子进程、WS、Mock 适配器、`script_generator` 伪代码、`script_sender` 裸 TCP、Ctrl+C 修复）＋ 9-03/9-04 第一版 4 页原型（`上下料/cnc-prototype.html`，78 KB） |
| `dev_ls` | 83a5f98 | 10-01 06:25 | lvshuai | 0 / 11 | — | 落后 4 | main 之上：9-04 点位组原型、9-08 HMI 画板与信号校验、9-24/9-29/9-30 文档整理（方案文档迁出到 leon-tools notes，`上下料/` 改成 原型/图纸/工具 三个子目录，新增 `资料/` 参考图和 19 MB 的 `T2K5-20260821.zip`）、10-01 7 菜单原型 v2（247f561）和两轮 UI 走查（eb8bd0f、83a5f98）。PC 上主仓库 `D:\GitFiles\cnc-station` 检出的就是它 |
| `feat/ui-v2-7menu`（**线上 /cnc/**） | dc0fe47 | 10-04 23:29 | lvshuai | 0 / 15 | 0 / 4 | — | dev_ls 之上 4 个提交，只改 `cnc-prototype.html`（+1249/−297）：8d44fd7 本地保存（localStorage `cnc.v2.`）、ef00659 数据层（程序 / 料盘按 id + 版本、传递影响、料盘主循环与收尾、D47/D48、存储 v:2）、948cfbb v3 模块化界面、dc0fe47 美工 4 处修正。10-05 00:15 推送并上线 |
| `cursor/tray-loop-program-modules-1dbc` | 3d3d956 | 10-03 07:16（提交时间 10-02 23:16 UTC） | Cursor Agent（co-author lvshuai2199） | 0 / 12 | 0 / 1 | 落后 4、领先 1 | 基于 83a5f98 的 1 个提交（+92/−24）：工程编辑左侧列出程序页的程序并可调用（机床换料 / 二次定位 / 翻转台）、运行周期展开被调用程序的步骤；保存料盘即设为主循环、逐格取到完。草稿 PR #1，base = `feat/ui-v2-7menu`（建 PR 时 base 在 83a5f98），未合并 |

补充：

- **10-04 23:16 主仓库那次 `git pull origin dev_ls`**：主仓库 reflog 是 `83a5f98 HEAD@{2026-10-04 23:16:23 +0800}: pull origin dev_ls: Fast-forward`，前一条是 9-30 的 4f019d8。`origin/dev_ls` 早在 10-02 01:47 已经被 fetch 到 83a5f98（reflog 记的是 `fetch: fast-forward`，不是这台 PC 的 `update by push`），说明这 3 个原型提交是从别处推到 `origin/dev_ls` 的。这次 pull 只是让本地 dev_ls 跟上远端，没有引入新内容，工作区仍然干净（10-07 复核：HEAD 83a5f98，0 处改动）。执行人仍未知。
- 记录上的出入：产品方案写「83a5f98 推送到远端新分支 feat/ui-v2-7menu，没有合进 dev」，但实际上 `origin/dev_ls` 也在 83a5f98，即 v2 原型的 3 个提交已经在远端 dev_ls 上了。
- 没有任何 tag。线上 /cnc/ 对应 dc0fe47，建议打 tag 固定（见 §5）。

## 2. 模块盘点（与「生成程序 + 快速修改」相关）

标记说明：**真** = 有真实逻辑，数据会变、规则会执行；**模拟** = 只是界面演示或随机值；**无** = 没有。

| 能力 | 位置 | 状态 | 说明 / 证据 |
|---|---|---|---|
| 工程（节点、主循环、插入位） | 原型 `DB.proj` L1123–1137；`genLoop` L1258；`guardSwitch` L1304 | 真（前端） | 7 个节点都能调用模块，选了料盘自动生成锁定骨架和 10 个插入位，切换 / 取消有确认、保存后才待核对。**只有一个工程**：工程下拉里切到「上下料二号」会提示「原型只提供上下料一号」并退回（L877、L2127） |
| 程序模块（步骤） | `DB.progs` L1151–1166；步骤属性 L2409–2456 | 真（前端） | 6 种步骤：移动 / 信号 / 等待 / 夹爪 / 调用 / 料盘；每步有速度、加速度、超时等参数；保存时按内容签名 +1 版本（`bumpVersions` L1212） |
| 点位 | `DB.points` L1177；点位 Tab `renderPts` L2457 | 数据真，示教模拟 | 点位按名字共享，坐标也算进程序签名（L1207：重录点位会让用到它的程序 +1 版本）。「记录当前位置」写的是 `Math.random()`（L2456、L2461） |
| 料盘模块 | `DB.trays` L1145–1150；规格字段 `tSpecFields` L2567；格位 `trayPos` L1388 | 参数真，坐标无 | 行 / 列 / 层 / 行列间隔 / 高度 / 抬升 / 取料方式 / 夹爪模板引用 / 前后置点 / 信号组都是参数。**没有原点坐标字段，也没有格位 → XYZ 的计算**（`trayPos` 只返回第几层第几格）；示教按钮只弹确认后 toast「已执行（模拟）」（L2645） |
| 夹爪 | `DB.grips` L1138–1143 | 真（前端） | TCP、开 / 关 / 吹气 DO、到位 DI、超时、取 / 放动作模板；被料盘和步骤按「夹爪 id\|模板」引用 |
| 工件 / 物料 | — | 无 | 没有工件实体（尺寸、夹持高度、取放偏移），料盘只有 `h` 高度 |
| 信号 | `SIG` L1095–1120；信号组 L1167 | 模拟 | 同一份真值，演示面板可拨；「导入」页是模拟（L2789「已导入 2 行（模拟）」） |
| 引用 / 版本 / 影响 | `refsTo` L1315、`markAffected` L1358、`brokenRefs` L1362、`renameProg` L1374 | 真（前端） | 按 id 引用，改名不 +1，传递影响标黄，失效标红，被引用不能删 |
| 运行模拟 | `buildCycle` L1757、`tick` L1798 | 模拟（但按工程数据走） | 按工程主循环 + 插入位 + 被调用程序的步骤展开（`progPhases`），料盘逐格、第一圈不下料、取完收尾 |
| 生成 / 下发 | `$('#eDeploy')` L2116–2126；`renderDeploy` L2094 | 模拟 | 4 步 `setTimeout` 动画后直接 `S.verified=true` |
| 文本视图 | `renderCode` L2463 | 模拟 | 输出「1. 移动 设备前端安全点 # 关节 · 30%」这种中文清单；「切换为可编辑」不回写（L2467） |
| 本地保存 | L1405–1477 | 真（前端） | localStorage，前缀 `cnc.v2.`、`v:2`，按页保存、结构校验、坏数据回示例；页面级快照用于「不保存离开」 |
| 导入 / 导出工程 | — | 无 | 没有工程 JSON 下载或上传 |
| 版本历史 / 撤销 | — | 无 | 只有当前 `ver` 计数，没有历史内容；没有 undo/redo（只能整页放弃修改） |
| 后端：程序生成 | `backend/app/services/script_generator.py` L29–114；`schemas/process.py` | 有代码，格式不对 | 单工件、3 个固定点（主轴夹紧 / 松开 / 夹具）、SAFE_Z、阀号、速度，拼出 `VAR POS … / MOVJ / MOVL … OFFSET_Z / GRIPPER / CNC_SIGNAL WAIT_PART_READY` 的**自造伪代码**，写成 `.script` 文件。和原型的数据模型（料盘、多模块、夹爪模板）完全不对应；技术方案 §6 明确说「不能沿用生成器里的 MOVJ / CNC_SIGNAL 伪代码」 |
| 后端：下发 | `services/script_sender.py`、`routes/script.py` | 开发占位 | 裸 TCP `sendall` 到 `192.168.249.128:30001`，不读回执；架构规则禁止对 C5 这样用，应走 `pyaubo-sdk` 上传 + `loadProgram` + 核对 |
| 后端：设备 | `devices/mock_robot.py`、`processes/collector.py`、`ws/manager.py` | 真（Mock） | 采集子进程 + `/ws/robot` 广播，只有 Mock 适配器；没有 `aubo_c5_adapter` |
| 机器人程序生成器（Lua / ARCS / JBI / Elite） | 全部分支、`D:\GitFiles` 全盘搜 `*.lua` / `*aubo*adapter*` / `steps.json` | **无** | 任何分支里都没有真实机器人语言的输出。目标格式按技术方案是 **ARCS 工程 / Lua**（Aubo C5），不是 Elite |

另外两处小问题：设置页适配器示例写的是「Aubo i5 适配器」（L1174），和第一阶段定的 C5 不一致；测试脚本（persist / scen / fixcheck / loopsim / qa1）都在 box 的 `/workspace/cnc-v3/`，仓库里没有任何测试。

## 3. 差距分析

| 问题 | 结论 | 证据 |
|---|---|---|
| (a) 能不能用模板 / 向导新建一个上下料程序 | **部分** | 有：选料盘后自动生成主循环骨架（`genLoop` L1258），模块库给出 4 个槽位的标准实现，料盘有「从其他料盘新建」（L2699–2706，复制后会追问坐标 / 信号是否已改）。缺：新建工程（只能用「上下料一号」，L2127）、新建工程向导、程序模板（「新建程序」是空步骤，L2478）、按现场基准程序（双爪单机床，技术方案 §8.9）一键起一套 |
| (b) 后续能不能快速改（工件尺寸、点位、件数、夹爪、速度）而不用全部重新示教 | **部分** | 有：料盘行列层 / 间隔改参数即可（L2567）；点位按名共享，改一处全引用跟随并自动 +1 版本、标受影响节点（L1207、L1358）；夹爪模板按 id 引用，改一次全局生效；每步速度 / 加速度可改（L2417）。缺：工件实体和偏移（改工件尺寸没有入口）；格位坐标计算（L1388 只算序号）；A 类参数集中面板（技术方案 §8.9「一般换产只改 A 项」）；批量调速 / 点位整体平移；参数与骨架分离（技术方案 §4.1 配方寄存器），目前任何改动都会让整包「待核对 → 重新生成下发」（`markStale` L2105） |
| (c) 能不能保存 / 加载 / 版本管理 | **部分** | 有：localStorage v2 按页保存、结构校验、坏数据回退（L1405–1477）；程序 / 料盘有 `ver` 和内容签名（L1206–1212）。缺：多工程、文件导出 / 导入、服务端持久化、版本历史与回滚、撤销 / 重做、下发记录（哪个版本、CRC 下发到了哪台） |
| (d) 能不能导出 / 运行 | **缺失** | 原型的生成下发是动画（L2116–2126），文本视图是中文清单（L2463），不调后端；后端生成器输出非 ARCS 伪代码（`script_generator.py` L42–101），下发走被禁止的裸 TCP（`script_sender.py` L79–82）；没有 Lua 模板、`steps.json`、CRC、`pyaubo-sdk` 适配器、`loadProgram` 核对（技术方案 §9 已列为缺口） |

## 4. 优化建议（按优先级）

工作量是 1 人的粗估。「落点」写建议的新分支（都从整合后的 `dev` 拉，见 §5）和改哪些文件。

### P0：打通「配置 → 真程序」，否则 a–d 都只能停在原型

| # | 内容 | 工作量 | 落点 |
|---|---|---|---|
| P0-1 | **工程数据模型定稿成 JSON Schema（`project.json`）**：把原型 `DB` 里的 proj / progs / trays / grips / points / signals 按 id + ver 抽成正式模型，补上缺的字段（料盘原点 / 三点、工件、A 类参数标记）。原型加「导出工程 / 导入工程」（下载 / 上传 JSON，导入走现有结构校验）。这是界面和生成器之间唯一的契约 | 2–3 天 | `feat/project-model`：`backend/app/schemas/project.py`、`docs/project.schema.json`、原型 `PERSIST` 段 |
| P0-2 | **ARCS Lua 生成器 v0（模板化）**：每个模块一段 Lua 模板（初始化、料盘.取 / .放用「原点 + 行列间距」公式算格位、机床双爪一次进出、成品下料、收尾），由 `project.json` 渲染；文件头写版本 / CRC / 机床号 / 操作者；关键动作前写 STEP / PHASE 寄存器；同时输出 `steps.json`（步号 → 中文阶段）。**替换**现在的伪代码生成器，旧的 `/api/process/script/generate` 标废弃 | 5–8 天（ARCS 语法、寄存器地址要现场确认） | `feat/lua-generator`：`backend/app/services/generator/`、`backend/templates/*.lua.j2`、golden 测试 |
| P0-3 | **参数和骨架分开（配方化）**：把换产常改的 A 类参数（工件尺寸 / 取放高度偏移、料盘行列层与间距、目标件数、速度倍率、起始盘格）做成参数块 / 寄存器配方，臂端模板只读参数。改这些只需写配方 + CRC，不用重新生成整包、不用重新示教。同时新增「工件」实体，被料盘和机床换料引用 | 3–4 天（设计 + 生成器配合） | `feat/project-model` + `feat/lua-generator`；原型新增「工件」和「换产参数」面板 |
| P0-4 | **原型接后端**：工程 CRUD 接口（列表 / 新建 / 复制 / 保存 / 加载 / 历史），原型把 localStorage 降为草稿缓存；「生成」按钮真调生成器，「文本视图」显示真正生成的 Lua（只读），并和上次下发的版本对比差异 | 3–4 天 | `feat/project-api`：`backend/app/api/routes/project.py`；原型 `eDeploy`、`renderCode` |

### P1：让「快速修改」好用、可追溯

| # | 内容 | 工作量 | 落点 |
|---|---|---|---|
| P1-1 | 新建工程向导 + 模板库：基准「双爪单机床 + 料盘」以及料仓 / 料框 / 输送线几种变体；程序模板（取料、放料、主轴吹气、二次定位） | 3 天 | 原型工程编辑 / 程序页；后端 `templates/projects/` |
| P1-2 | 版本历史与回滚：程序 / 料盘 / 工程每次保存存快照，可查看差异、回滚到某版；下发记录（版本、CRC、时间、操作者）；编辑器内撤销 / 重做 | 3 天 | `feat/project-api` + 原型 |
| P1-3 | 点位管理：料盘点由原点 + 偏移算出，接近 / 离开点写成相对偏移；夹具挪位时「整组平移」；示教接真实 `RobotState`（不再用随机数） | 3 天（真机示教另算） | 原型料盘页 / 点位 Tab；后端适配器 |
| P1-4 | Mock 闭环：Mock 适配器按 `steps.json` 推进并写 STEP / PHASE / HB，首页显示真实步号；`aubo_c5_adapter` 骨架（`pyaubo-sdk` 上传 + `loadProgram` + 核对 + `runProgram`），替换裸 TCP 下发 | 5 天起（真机联调另算） | `feat/aubo-adapter`：`backend/app/devices/aubo_c5_adapter.py` |
| P1-5 | 测试进仓库：把 box 上的 persist / scen / fixcheck / loopsim / qa1 搬到 `tests/`，后端加 pytest（生成器 golden 文件），GitHub Actions 跑一遍 | 1–2 天 | `tests/`、`.github/workflows/` |
| P1-6 | 下一轮 UI 已排的 6 项：操作员视角；程序页「点位 / 文本视图」（文本视图和 P0-4 合并做，接真 Lua）；演示面板标题分割线；1024 全屏关闭按钮距右 16；夹爪标签列宽 88；程序列表长按菜单（改名 / 复制 / 删除 / 查看引用，顺带补上「复制程序」） | 2–3 天 | `feat/ui-round4`（只改 `cnc-prototype.html`） |

### P2：工程化、减少后续冲突

| # | 内容 | 工作量 | 落点 |
|---|---|---|---|
| P2-1 | 单文件原型（2881 行、323 KB）按技术栈拆成 Vue 组件（页面 / 数据层 / 模拟器分开）。不拆的话每个功能都改同一个文件，并行分支必冲突（PR #1 就是例子） | 2–3 周 | 新 `frontend/`，原型冻结为设计参考 |
| P2-2 | 仓库瘦身：`资料/T2K5-20260821.zip`（19 MB）和参考图（约 18 MB）挪到 LFS 或 notes | 0.5 天 | `.gitattributes` |
| P2-3 | 小修：设置页示例适配器改为 C5（L1174）；界面去掉「（模拟）」类字样前确认对应能力已接后端 | 0.5 天 | 原型 |

## 5. 分支整合方案（只是方案，没有执行）

### 5.1 试算结果

在 box 一次性克隆里试算：

| 合并 | 结果 |
|---|---|
| main ← dev_ls | 快进，无冲突 |
| main ← feat/ui-v2-7menu | 快进，无冲突（main 是它的祖先） |
| dev_ls ← feat/ui-v2-7menu | 快进，无冲突 |
| feat/ui-v2-7menu ← cursor/tray-loop… | **冲突**：`cnc-prototype.html` 14 处，共约 600 行，最大一处 280 行（L1998–2278）。冲突集中在 `startWhy`、`renderHome` 数据卡、`buildCycle` / `tick`、`LIB` / `libPick`、`innerSteps`、`renderProj` 画布、`tSave`、`window.__cnc` |
| dev_ls ← cursor/tray-loop… | 能自动合（它本来就基于 83a5f98），但对后续没意义 |

### 5.2 建议顺序

1. **新建 `dev`，指向 dc0fe47**（= main → dev_ls → feat/ui-v2-7menu 一路快进的结果）。零冲突，内容和线上 /cnc/ 一致。同时在 dc0fe47 打 tag，例如 `cnc-proto-v3-20261005`，固定线上版本。
2. **`main` 要不要也快进到 dc0fe47**，由帅吕定。main 是 GitHub 默认分支，目前停在 9-04；快进后默认分支能看到最新原型和文档整理。
3. **PR #1（`cursor/tray-loop-program-modules-1dbc`）建议关闭、不合并。** 它做的两件事（工程编辑调用程序模块；料盘即主循环、逐格取完）都已经在 ef00659/948cfbb 里按 D37–D48 重做了，而且做得更完整：按 id 而不是按名字引用、7 个节点都能调用、主循环带插入位、第一圈不下料、取完收尾。硬合的话要手工解决 14 处冲突，很容易把按名字引用（`progByName`）和旧的 `mods.pick.ref` 逻辑带回来，冲掉 D37–D48。没有需要 cherry-pick 的内容；它的「第 n 行第 m 列」格位文字和 D45 定的「第几层 · 第几格」口径不一致，也不需要。
4. **`dev_ls` 收尾**：`dev` 建好后 dev_ls 就是它的祖先，可以保留做个人分支，或改为从 `dev` 继续。PC 主仓库目前检出在 dev_ls，切不切由帅吕自己定。
5. 之后的新工作都从 `dev` 拉短分支：`feat/project-model` → `feat/lua-generator` → `feat/project-api` → `feat/aubo-adapter`；UI 走 `feat/ui-round4`。**原型文件同一时间只开一个改它的分支**，在 P2-1 拆分之前避免再出现 PR #1 这种冲突。

### 5.3 风险

- 建 `dev`、打 tag、快进 main、关闭 PR、删远端分支都是会被别人看到的远端操作，需要帅吕明确同意后再做。
- 主仓库检出在 dev_ls，而且有过一次来源不明的 pull；worktree 和主仓库共用 refs，在主仓库里 fetch / pull 会影响 worktree 看到的远端状态。建议以后整合操作统一在 worktree 或临时克隆里做。
- `origin/dev_ls` 已经包含 v2 原型，和产品方案里「没有合进 dev」的记录不一致，建议在记录里更正。
- 线上 /cnc/ 是从功能分支部署的，没有 tag；以后回滚靠服务器上的备份目录（`/opt/leonpro/backup/20261005-0014-cnc/`），建议补 tag。
- 生成器要换成 Lua，旧的 `/api/process/script/generate` 和 `/api/script/send` 如果有人在用（例如联调脚本），要先确认再废弃。

## 附：本次评审做过的操作（全部只读）

- PC：在 worktree 里 `git fetch --all --prune`、`git branch -a`、`git worktree list`、`git bundle create`（打到 %TEMP%，拷到 box 后已删除）；主仓库只看了 reflog 和 status。两个工作区复核：主仓库 HEAD 83a5f98 无改动，worktree HEAD dc0fe47 无改动。
- 读了 leon-tools notes 里的技术方案和产品方案 v2（只读）。
- GitHub：只读列出 PR（只有 #1，草稿、打开）。
- box：`/workspace/review-1007/repo`（分析用克隆）、`/workspace/review-1007/tmpmerge`（合并试算用克隆，处于未提交的合并状态，可以直接删除）。

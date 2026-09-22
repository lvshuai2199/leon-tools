**v1.7.47**：离线包自带文泉驿微米黑。Tk 不会为缺字自动换字体，上一版把界面锁成不含中文的字体，汉字变成方框。

**v1.7.46**：界面文字统一成同一种深色、12 号字。设置里的说明不再用浅灰小字。

**v1.7.45**：界面中文改用文泉驿等较清晰的字体，字号提到 11，并打开字体微调。小字号的 Noto 不再把「发送」「时间」抹糊。

**v1.7.44**：界面改用系统里的中文字体（Noto / 文泉驿等）。离线包里「Sans」「DejaVu」缺字时，标题和时间线不再显示成方框。

**v1.7.43**：初始化「添加行」的 CAN ID 默认与上一行相同，扩展帧一并带上；没有上一行则为空。

**v1.7.42**：设置里初始化的「编辑 / 添加行」不必先选中一行。没选中时直接新增并打开编辑；取消则不留下空行。

**v1.7.41**：第一次打开、配置组合为「未选择」时，不再铺出上次保存的「信号」卡片，也不在后台继续当焊机应答。

**v1.7.40**：接收、发送里同一组控件优先排成一行（配置/角色/初始化、勾选、模式电流电压、CAN ID 与数据）。一行放不下再折成两行。

**v1.7.39**：Megmeet - GOO 的接收、发送按同一列标签对齐。给焊机发只保留「发送初始化」按钮，点一下发 F0–F5，开总线不再自动发。

**v1.7.38**：主界面收矮。Megmeet - GOO 时接收在左、发送在右；通用模式发送占满一行。CAN ID / 数据(HEX) 行仍可手改后单次或周期发送。默认窗口约 980×620。

**v1.7.37**：点「单次发送」时，数据与上一帧相同也在时间线新增一行。周期发送和自动应答仍按「仅显示变化」过滤。

**v1.7.36**：「给焊机发」的初始化改为可选勾选，不勾则开总线不发 F0–F5。设置页暂时去掉「应答服务」（模拟焊机仍在主界面角色里）。

**v1.7.35**：发送区「配置组合」增加 **Megmeet - GOO**（初始化 + 应答 + 配置档绑在一起）。角色「给焊机发」用固高 F0–F5 和轮询组帧，接收区显示焊机回帧；角色「模拟焊机」在开总线下方显示机器人下发，发送区自由勾回应答。字节布局对齐固高手册和 `megmeet_can_responder.py`。

**v1.7.34**：TX/RX 灯跟总线活动走，不跟时间线过滤。重复帧不进列表时灯仍会闪；连续收发会亮灭交替，不会常亮或一直灰。

**v1.7.33**：「仅显示变化」只决定是否**追加**时间线：同方向+同 ID 数据未变不进列表；有变化则新加一行（不再就地改原行）。默认开，TX / 周期 / 应答同样过滤。

**v1.7.32**：时间线「仅显示变化」默认开启（主界面可勾选）。v1.7.33 起变化改为追加一行，不再就地更新。

**v1.7.31**：关于框版本号跟 `__version__` 走（不再写死 UI v1.7.26）；其余同 1.7.30。

**v1.7.30**：应答规则按 `megmeet_can_responder.py` **内置写死**（无 `auto_rules` / 无应答编辑页）：轮询帧起焊沿↑勾「起弧成功」、↓清；寻位位为真则勾「寻位成功」（锁存，同 py）。设置→应答服务仅下拉选档（「未选择」=关）+ 灰字「规则按内置写死，不可编辑」（去掉「编辑档」）。主界面信号标题行右侧增加「自动回起弧成功」开关（绑 `state.auto_arc`，默认开；随信号卡隐藏）。手勾信号仍可覆盖。

**v1.7.29**：曾尝试设置内嵌应答「编辑档」滚动/用户副本保存；已被 1.7.30 产品锁取代（应答不可编辑）。

**v1.7.28**：信号显示名「电流 A」「电压 V」对齐美工/文案锁；其余同 1.7.27（跟 megmeet_can_responder.py）。

**v1.7.27**：`megmeet_welder` 信号名/备注对齐 `megmeet_can_responder.py`（就绪/故障/寻位成功/起弧成功/电弧异常/其他异常/粘丝/A/V/错）；不强制手册「气体流量异常」「给定超范围」；引擎打包布局未改。

**v1.7.26**：应答档 JSON `signals` 驱动主界面「信号」区（勾选/数值，无硬编码就绪/故障）；设置去掉「启用初始化/启用」勾选，是否开启只看下拉「未选择」=关；添加行后「编辑」弹窗父窗改为设置窗（修复空白窗）；「仅数据变化时打印」按同 ID（+扩展标志）比较数据，未变不刷时间线。

**v1.7.25**：初始化/应答档「添加行」改为立刻追加空行（可填 ID/HEX/间隔），不再弹编辑对话框；删行/上移/下移与保存不变。设置内嵌时对话框常无法完成导致添加看似无效。

**v1.7.24**：主界面发送「配置档」去掉 `megmeet.PROFILES` 硬编码，改为「未选择」/「通用（仅原始 HEX）」；含 1.7.23 选档「未选择」规则；列表仍只扫 JSON。

**v1.7.23**：选档下拉统一顶项「未选择」（非空白）：选「未选择」灰字 `#787D85`、取消并禁用启用勾选与编辑/删除；选具体项则自动启用。下拉只显示各 JSON 的 `name` 字段（不出 stem /「初始」后缀）。出厂初始化仅 `goo_init.json`「固高 GOO」；应答档 `profiles/megmeet_welder.json` 显示名「Megmeet 焊机应答」。窗 720×520、模块下拉 min 220·H32。

**v1.7.22**：初始化出厂只留「固高 GOO」；拿掉 Megmeet 直流一元初始化样例。Megmeet 仅保留在应答档（`profiles/megmeet_welder.json`）。新建空白模块照旧。

**v1.7.21**：恢复出厂双初始化样例 `goo_init.json`（「固高 GOO」）+`megmeet_dc_synergic.json`（「Megmeet 直流一元」）；去掉启动时 prune 清 Megmeet；设置五页美工：左标签列宽 ~110、行距 12、帮助距控件 8px、口命令/应答/回帧去掉冗余外框标题；模块/应答档下拉 min 220·H32·字号 10；新建仍「未命名模块」；底栏固定、右详情可滚；窗 720×520、导航 120。

**v1.7.20**：设置→初始化模块行美工 lock：下拉最小宽 220、行高 32、字号 10、水平 pad 10、垂直居中；过长显示名截断为「…」；新建/编辑/删除高 32、间距 6；出厂仅 `goo_init.json`（「固高 GOO」）；新建仍默认「未命名模块」；窗仍 720×520、左导航 120。（五页全面美工清单稍后同版可续。）

**v1.7.19**：初始化模块下拉每个模块只显示一条（用显示名，不再同时列 stem）；启动时清理 `~/.can_tester/modules` 里 Megmeet/重复 goo 残留；出厂仅 `goo_init.json`（显示名「固高 GOO」）。

**v1.7.18**：出厂只保留固高 GOO 初始化样例（`modules/goo_init.json`）；已去掉 Megmeet 直流一元初始化样例与 `profiles/megmeet_welder.json` 应答样例数据。新建/编辑/删除仍可用。

**v1.7.17**：设置窗固定 720×520（打开居中）；左导航仍 120；右详情 pad 16、内容溢出可竖滚；初始化模块行下拉弹性伸缩，新建/编辑/删除同行不换行、不裁切。

**v1.7.16**：初始化「新建」弹名默认「未命名模块」；模块行仍为下拉+新建/编辑/删除（间距 6）。

**v1.7.15**：设置「初始化」页模块下拉旁增加「新建 / 编辑 / 删除」（删除前确认「删除模块 xxx？」；危险按钮红色描边字色 `#C4554D`）；可切换多模块（出厂样例含固高 GOO + Megmeet 直流一元）；「新建」在 `~/.can_tester/modules/` 创建空白 JSON 行表；删当前选中后刷新列表并切到「无」。

**v1.7.14**：`run.sh` 在 venv/get-pip 超时或失败时立刻落到 `--user`，打印「已切换备用启动」，避免卡在 without-pip 引导。

**v1.7.13**：设置改为左导航 + 右详情（窗 560×420，导航宽 120）；页：通用 / 口命令 / 初始化 / 应答服务 / 回帧；「编辑档」「查看详情」嵌在右侧并带「← 返回」，不再另开顶层窗抢焦点。

**v1.7.12**：连接栏去掉「建 vcan / 拉起 / 关闭」；设置新增「口命令」（接口名默认 `can0`，可改；「创建」=`ip link add … type vcan`，「拉起」按当前波特率 / vcan 纯 up，「关闭」=down；仍 sudo 确认+时间线）。TX/RX 灯仍 220ms megmeet 闪。

**v1.7.11**：TX/RX 活动灯完全对齐 megmeet `_flash_flow`——亮 220ms 再熄；重触发只续亮，不用 100/80 交替。口状态灯仍只表示开/关。

**v1.7.10**：TX/RX 灯对齐 megmeet（亮 220ms）；快节拍强制灭约 80ms 再亮，避免常亮卡死；空闲超 300ms 回灰。

**v1.7.9**：连接栏增加接口快捷按钮「建 vcan」「拉起」「关闭」（二次确认后 `sudo -n` 执行 `ip link …`，结果写入时间线）；TX/RX 状态灯（绿/蓝，活动约 200ms）。

**v1.7.8**：默认波特率改为 `125000`（新开/重置都带这个值）。

**v1.7.7**：本机回环 RX（socketcan/virtual `receive_own_messages`）默认不进时间线，避免「发送」6 帧看起来像重复一轮；设置「显示本机回环」开启后以备注「本机回环」显示。开总线自动初始化后再点「发送」确认框默认「否」。

**v1.7.6**：初始化只发勾选行、触发一轮；开总线自动初始化后状态栏显示「已初始化 N 帧」，再点「发送」需确认，避免 6 条变 12。\n\n**v1.7.5**：状态栏错误字色 `#C4554D`，过长末尾「…」，悬停/点击看完整命令；开总线主按钮不随错误变红。\n\n**v1.7.4**：开总线前检测 CAN 口是否 UP；`Network is down`/错误码 100 改为「CAN 口未启动（canX down），请先 ip link set … up」，不再写「网络失败」。\n\n# CAN 通用监听 / 发送（Linux）v1.7.26

**v1.7.3**：选 `virtual` 时连接栏浅字提示「仅本进程可见，双开请用 vcan0」（无弹窗）。

**v1.7.2**：同机双实例互通——抽出 `responder_service` 库路径；CLI 新增 `--responder` / `--responder-module` / `--list-responders`；`virtual` 固定共享通道名 `cansim-loopback`（**仅本进程内**多 `CanSession` 互见，跨进程请用 `vcan0`）；自测 `tests/test_dual_virtual_responder.py`。

**v1.7.1**：包布局整理——应答档 JSON 移至 `profiles/`（原 `modules/responder/`）；`modules/` 仅保留初始化模块；去掉根目录 `PRODUCT_NOTE_zh.txt`（说明并入 README）。

**v1.7.0**：新增 Megmeet「应答服务」——设置中开关 /「应答档」下拉 /「编辑档」；与「只监听不独占」互斥；开启且总线打开时连接栏浅字显示「应答服务 · 运行中」。核心动态回帧逻辑见 `can_tester/megmeet_responder.py`（参考 `megmeet_can_responder.py`）；应答档 JSON 表列：启用 / 匹配 ID / 回帧 HEX / 延时 ms / 备注。

**v1.6.1**：周期发送失败不再弹窗刷屏——最多静默重试 3 次后写时间线一行、自动停周期、状态栏一行提示；开总线检测接口占用（如 FullFunctionWelding），提示先断开插件；设置新增「只监听不独占」（`listen_only_shared`）：SocketCAN 无可靠 listen-only/独占标志，尽力共享打开并禁止本工具 TX；virtual/vcan 行为不变。

**v1.6.0**：初始化升为配置模块——设置「启用初始化」时主界面仅显示摘要行（模块名 + 已启用 n/total +「查看详情」），完整表移入独立详情窗；不启用则主界面不渲染该区。设置「仅数据变化时打印」对齐原「只显示变化」（`rx_changes_only`），同 ID 数据未变不刷时间线。

**v1.5.1（UI）**：初始化区去掉重复主按钮，仅保留主操作「发送勾选」，增删移编辑为次要样式；连接栏「开总线」/「闭总线」；设置页「回帧」独立分组（条数+超时）。功能不变。

**v1.5.0（UI）**：主窗按美工重排为「连接栏 → 初始化表 → 发送区 → 时间线」；设置独立对话框；时间线列 时间|方向|ID|数据|备注；空态「未开总线」/「无帧」。功能不变。

默认进 **通用总线监听**：时间线默认收全部 RX，但 **仅显示变化**（同 ID 数据未变不进列表，有变化追加一行）；可按 Megmeet 工具风格再加 **RX ID 白名单**。关「打印客户端」仍只影响自发后回帧计数/超时。初始化为 **可自由编辑的发送行表**（JSON 模块），经设置页开关决定是否在连接时自动跑。

## 能力

- 标准 / 扩展自动识别（收：看帧标志；发：ID > `0x7FF` 自动扩展）
- ID 过滤可空（空 = 全收）；设置页另有 RX 白名单（多 ID）+「仅显示变化」（默认开）
- 扩展帧 ID 上限 `0x1FFFFFFF`（只拦超过 29 位）
- 协议文案固定 **socketcan**
- **Megmeet** 为可选配置档：预填 `0x1FD07063`，解析回帧 `0x1FD08063`；不选则只看原始 HEX
- **自由初始化表格**：每行 = 启用 / CAN ID / 数据 / 间隔ms / 备注；可添加 / 删除 / 上移 / 下移 / 保存 / 另存为
- **设置页**：是否连接时初始化、选用模块、全局默认间隔 + 打印过滤
- Gaogo/GOO 初始化仅为 `modules/` 下的 **默认可编辑样例**；Megmeet 应答行为在 `engine=megmeet` 时 **硬编码**（同 `megmeet_can_responder.py`），设置页不可编辑规则
- 主站发送仍保留（单次 / 周期）


## 包目录

```
can_tester_linux/
  README.md / requirements.txt / run.sh / run_user.sh
  can_tester/     # Python 包
  modules/        # 初始化模块 JSON
  profiles/       # 应答档 JSON
  tests/          # 单元测试
  scripts/        # 辅助脚本（如 setup_vcan0.sh）
```

## 接口

| 接口 | 说明 |
|------|------|
| `vcan0` / `can0` | SocketCAN |
| `virtual` | python-can 软件回环（无需内核 vcan） |

## 运行

```bash
chmod +x run.sh run_user.sh scripts/setup_vcan0.sh
./run.sh                 # GUI（默认通用监听）
./run.sh --user          # 无发行版 venv
./run_user.sh
./run.sh --cli --iface virtual --listen
./run.sh --list
./run.sh --list-modules
./run.sh --init-dry-run --init-module modules/goo_init.json
./run.sh --init-dry-run --init-checked-only --init-module modules/goo_init.json
./run.sh --cli --iface virtual --init-module modules/goo_init.json --listen
./run.sh --cli --iface virtual --listen --rx-filter 1FD07063,1FD08063 --rx-changes-only
./run.sh --list-responders
./run.sh --cli --iface vcan0 --listen --responder --responder-module megmeet_welder
```

缺 `python3-venv` 时会提示 `sudo apt install python3-venv`（或 `python3.10-venv`），并自动 / 可手动走 `--user`。

## 同机双实例（应答 ↔ 客户端）

目标：一端开「应答服务」（模拟焊机），另一端发轮询并收 `0x1FD08063`；**不要抢真实 `can0`**。开应答时本端仍可单次/周期发送。

| 方式 | 跨进程 | 说明 |
|------|--------|------|
| `vcan0` | ✅ | 两终端 / 两 GUI 互见。先 `./scripts/setup_vcan0.sh`（需 sudo）。 |
| `virtual` | ❌ 仅本进程 | 固定通道 `cansim-loopback`；适合库测。两终端各开 `virtual` **互收不到**。 |

### GUI 双开（推荐 vcan0）

1. 准备接口：`./scripts/setup_vcan0.sh`（或选已有 `vcan0`）。
2. **窗口 A（应答端）**：接口选 `vcan0` → 开总线 → 设置打开「应答服务」、应答档选 `megmeet_welder`（或 Megmeet 焊机应答）→ 保存。连接栏应显示「应答服务 · … · 运行中」。**不要**勾「只监听不独占」。
3. **窗口 B（客户端）**：同样 `vcan0` 开总线；配置档可选「Megmeet 焊机」预填 ID，或手动 ID=`0x1FD07063`、数据=`FF 00 00 00 00 00 00 00`、扩展帧 → **单次发送** / **开始周期**。
4. 预期：A 时间线见 RX `0x1FD07063` 并自动 TX `0x1FD08063`；B 见该回帧。两端都可继续手发。

无显示器 / 无 tk 时用 CLI（见下）。本机冒烟：`PYTHONPATH=. python3 tests/test_dual_virtual_responder.py`。

### CLI 双开

```bash
# 终端 A — 应答
./run.sh --cli --iface vcan0 --listen --responder --responder-module megmeet_welder

# 终端 B — 客户端轮询
./run.sh --cli --iface vcan0 --id 0x1FD07063 --data "FF 00 00 00 00 00 00 00" --ext --print-client
```

`./run.sh --list-responders` 可查应答档。

## 设置（`~/.can_tester/config.json`）

GUI 菜单「文件 → 设置…」或连接栏右侧 **设置**（独立对话框，720×520 居中；左导航 120 + 右详情可竖滚）：

| 字段 | 默认 | 说明 |
|------|------|------|
| `enable_init_on_connect` | `false` | 打开总线时是否自动跑选用模块的启用行 |
| `init_module` | `""` | 模块名或文件 stem（如 `goo_init` / `固高 GOO`） |
| `default_interval_ms` | `50` | 行间隔为空/0 时使用的全局默认间隔 |
| `print_client_data` | `false` | 「打印客户端数据」仅影响客户端连续打印提示与回帧计数/超时；不单独隐藏外来帧 |
| `reply_count` | `1` | 关闭打印时，用户 TX 后按回帧条数计数并做超时提示（1~10） |
| `reply_timeout_ms` | `1000` | 等回帧超时（毫秒） |
| `rx_id_filter` | `""` | RX ID 白名单（逗号/空格分隔十六进制，例 `1FD07063,1FD08063`）；空 = 全部 |
| `rx_changes_only` | `true` | 「仅显示变化」：同方向+同 ID 数据未变不进入时间线；首次与每次变化都追加一行（含 TX / 周期 / 应答） |
| `show_self_echo` | `false` | 「显示本机回环」：为 true 时把本机 TX 的回环 RX 写入时间线并标「本机回环」；默认 false（已记 TX 则隐藏回环，避免 6 条变 12） |
| `enable_responder` | `false` | 「应答服务」：匹配应答档自动回帧（可模拟焊机）；**不禁止**单次/周期手发；与 `listen_only_shared` 互斥（开应答会关只监听） |
| `responder_module` | `""` | 应答档名或 stem（如 `megmeet_welder` / `Megmeet 焊机应答`） |
| `listen_only_shared` | `false` | 「只监听不独占」：尽力以共享方式打开 SocketCAN；python-can 无可靠 receive-only/独占标志，故为 best-effort——打开后禁止本工具 TX，占用失败时提示先关 FullFunctionWelding/插件；不强制抢占；`virtual` 不受影响 |

**行为**：启用连接初始化且已选模块 → 连接成功后按序发送勾选行（带行间隔），然后进入监听；关闭则连接后立即监听。手动「发送勾选 / 发送选中」始终可用。

CLI 可用 `--print-client` / `--no-print-client` 临时覆盖打印开关；`--rx-filter` / `--rx-changes-only` 覆盖 RX 过滤。

**RX 显示规则**（参考 elite-notes `megmeet_can_responder.py` 风格）：
1. 白名单非空时，只把命中 ID 的外来 RX 写入时间线（TX 不受影响）。
2. 「仅显示变化」开启时，同方向+同 ID 数据未变不进时间线；有变化则追加一行。TX（含周期发送、自动应答）同样过滤。仍计入回帧捕获（凡通过白名单的 RX）。
3. 关「打印客户端」时，自发一帧后仍按 `reply_count` 做超时提示。
4. 本机刚发出的帧若经总线回环成 RX：默认隐藏；开启「显示本机回环」时显示并备注「本机回环」（不计入回帧捕获、不触发应答）。

## 接口占用 / 只监听不独占

Linux SocketCAN RAW 通常允许多进程同时绑定同一 `can0`；部分适配器/专有栈（如插件 FullFunctionWelding）可能表现为独占。开总线前会做非破坏探测；若遇 `EBUSY` / 权限类错误，提示：

> 接口已被其它程序占用（如 FullFunctionWelding），请先在插件断开或关闭本工具总线

设置「只监听不独占」不会去改内核 `listen-only` ctrlmode（需 root/`ip link`），仅为应用层 best-effort：正常打开共享 RAW socket，并禁止本工具发送，避免与其它程序抢发。无法打开时仍优雅失败，不强制抢占。

## Megmeet 预设

设置「应答服务」只选 `profiles/` 应答档（默认 `megmeet_welder.json`）；`engine=megmeet` 时 `megmeet_responder.py` 按 py **写死**起弧/寻位自动勾，主界面可关「自动回起弧成功」、手勾信号。设置页无编辑档。**开启应答不会禁用**主界面「单次发送 / 周期发送」；仅与「只监听不独占」互斥。

配置档选「Megmeet 焊机」：发送框预填插件扩展 ID，时间线对两路 ID 做简易解析。设置页「Megmeet」按钮可写入白名单 `1FD07063,1FD08063`（插件 TX `0x1FD07063`、焊机 RX `0x1FD08063`）；过滤留空则看全总线。

## 初始化模块（自由行表）

| 路径 | 说明 |
|------|------|
| `modules/`（随包） | 初始化模块出厂样例：`goo_init.json`（「固高 GOO」）、`megmeet_dc_synergic.json`（「Megmeet 直流一元」） |
| `~/.can_tester/modules/` | 用户自定义初始化模块（同名可覆盖随包） |
| `profiles/`（随包） | 应答档出厂样例（可空；用户可自建） |
| `~/.can_tester/responder/` | 用户自定义应答档（同名可覆盖随包） |

### GUI 操作

1. 设置页打开「启用初始化」后，主界面连接栏与发送区之间出现摘要行：`初始化 · {模块名} · 已启用 n/total`，右侧「查看详情」。
2. 设置「初始化」页：模块下拉（出厂样例「固高 GOO」「Megmeet 直流一元」；可新建多模块）旁 **新建 / 编辑 / 删除**（间距 6）；下拉加宽完整显示名；「编辑」嵌在右侧（「← 返回」）；「删除」确认「删除模块 xxx？」后删当前 JSON 并刷新，切到「无」。
3. 「新建」：在 `~/.can_tester/modules/` 创建空白行表，可再添加行后保存。
4. 表列：启用 | CAN ID | 数据 | 间隔ms | 备注；底栏「发送勾选」+ 增删上下编辑。单击「启用」列切换勾选；双击行编辑。
5. **发送勾选**：按序只跑启用行；**发送选中**：只跑当前选中行
6. **保存模块 / 另存为**：写回 JSON（含各行 `enabled`）
7. 关闭「启用初始化」后主界面不显示初始化区。

### JSON 字段（主 schema）

```json
{
  "name": "固高 GOO",
  "description": "…",
  "default_interval_ms": 50,
  "rows": [
    {
      "enabled": true,
      "id": "0x1FD07063",
      "extended": true,
      "data": "F0 00 00 00 00 00 00 00",
      "interval_ms": 50,
      "note": "映射设定 F0"
    }
  ]
}
```

- `interval_ms` 为空 / 0 / 省略 → 使用模块或设置里的全局默认间隔
- 旧版 `steps`（`send_frame` + `delay` / `wait_reply` / `write_param`）加载时会自动转成 `rows`（仅保留发送行；delay 并入行间隔；wait/write 忽略或并入备注）

### 样例：固高 GOO（说明书 / 插件共 6 帧 F0–F5）

随包：`modules/goo_init.json`。TX ID `0x1FD07063` EXT；F0–F5 六帧（产品口头「5 帧」为约数；协议/文档/插件为 6 帧）。默认行间隔约 50ms。**仅为样例，逻辑不硬编码 GOO。**

### 自己加模块

1. 复制 `modules/goo_init.json` 改名，改 `name` / `rows`。
2. 放到 `modules/` 或 `~/.can_tester/modules/`。
3. GUI 点「刷新」，或 `./run.sh --list-modules`。

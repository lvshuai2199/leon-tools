# WeldingTools 代码学习说明

这份仓库不是一个独立应用，而是 ELITE/Elibot 机器人环境里的焊接工艺插件。它主要负责三件事：

1. 在任务树里提供焊接相关节点。
2. 提供不同焊机、不同工艺的配置页面。
3. 生成轨迹、脚本，并和本地 daemon 交互。

## 先建立整体印象

你可以把它理解成四层：

- 插件入口层：`impl/Activator.java`
- 任务节点层：`impl/task/**`
- 配置和参数层：`impl/configure/**`、`impl/task/weldparameters/**`
- 轨迹、数学、daemon 层：`impl/domain/**`、`impl/math/**`、`impl/utils/**`、`src/main/resources/daemon/**`

## 建议的阅读顺序

1. `README.md`
2. `pom.xml`
3. `src/main/java/.../impl/Activator.java`
4. `src/main/java/.../task/base/command/WeldCommandAbstract.java`
5. `src/main/java/.../task/base/weld/WeldTaskNodeAbstract.java`
6. `src/main/java/.../task/weld/WeldTaskNodeContribution.java`
7. `src/main/java/.../task/weldparameters/base/BaseMachineAbstract.java`
8. `src/main/java/.../domain/TrajectoryManager.java`
9. `src/main/java/.../daemon/WeldingDaemonServiceImpl.java`
10. `src/test/java/.../GenerateTrajectoryTest.java`

## 每层在做什么

### 1) 插件入口

`Activator.java` 是 OSGi 启动入口。它会：

- 读取本地化资源
- 注册 daemon 服务
- 注册所有 Swing 任务节点服务
- 注册配置节点服务
- 注册导航栏入口

这是理解整个插件“怎么挂到机器人环境里”的第一站。

### 2) 任务节点层

`task/*` 目录基本都遵循同一模式：

- `xxxServiceImpl`
- `xxxContribution`
- `xxxView`

常见节点有：

- `weld`：焊接根节点
- `weldstart`：起始点
- `weldpoint`：接近点、离开点、绑定点
- `weldshape`：直线段、圆弧段
- `weldtool`：焊接工具总入口
- `weldmultipass`：多层多道
- `weldparameters`：焊机参数
- `tack`、`touchsense`、`touchsearch`、`tracking`、`arctracking`：辅助工艺节点

### 3) 基础抽象层

这里是最值得啃的部分：

- `WeldCommandAbstract`：脚本生成、偏移、树结构遍历、前后节点选择
- `WeldTaskNodeAbstract`：启用状态、等待时间、对子树做整体偏移
- `WeldLinearTaskNodeAbstract`：直线/圆弧点位、逆运动学、自动移动到目标点
- `WeldPointTaskNodeAbstract`：点位命名、示教、路径类型、移动预览

这一层决定了“一个焊接节点到底长什么样、怎么被编辑、怎么被执行”。

### 4) 配置和参数层

`configure/weldconfig` 里是焊机适配器，例如：

- `AotaiWelder`
- `GysWelder`
- `KolarcWelder`
- `LorchWelder`
- `MerkleWelder`
- `MegmeetCanWelder`
- `MegmeetModbusWelder`
- `KemppidcmWelder`
- `AxMigOneWelder`
- `ScriptCBWelder`
- `GenericIOWelder`
- `EmptyWelder`

`task/weldparameters/**` 则是各品牌参数页和数据模型工厂。
`BaseMachineAbstract` 定义了统一的创建方式，子类负责返回对应的 contribution 和 view。

### 5) 轨迹、数学、daemon

- `domain/TrajectoryManager.java`：把焊接段、摆动参数、圆弧角度等整理成 JSON，再交给 daemon
- `XmlRpcDaemonFacade.java`：通过 XML-RPC 调本地 Python/服务端
- `src/main/resources/daemon/`：Python 脚本、`modbus_tk`、架构相关可执行文件
- `math/*`：几何和空间计算
- `utils/*`：IK、偏移、命令请求、图标、布局、日志等通用工具

## 一条典型数据流

1. 用户在 UI 里点“设置点位”。
2. `Contribution` 调用 `TaskApiProvider`，把 pose / joint 写回数据模型。
3. `UndoRedoManager` 记录变更，保证可撤销。
4. 如果是移动点或偏移点，会调用逆运动学或偏移工具重新算关节角。
5. 生成脚本时，`WeldCommandAbstract` 负责把当前节点和子节点写成程序。

## 重点看懂的几个概念

- `TaskApiProvider`：插件对机器人环境的入口
- `DataModelDomain<T>`：类型化的数据键
- `DataModelWrapperExtension`：数据模型读写封装
- `TreeNode` / `TaskExtensionNode`：任务树结构
- `TaskNamedEntityHandler`：节点命名和去重
- `MoveInPlaceContribution`：示教/播放相关接口

## 这份代码最适合怎么学

1. 先看 `README.md`，把业务场景和节点图记住。
2. 再看 `Activator.java`，搞清楚插件怎么注册。
3. 接着从 `WeldTaskNodeContribution.java` 入手，顺着一个焊接根节点往下追。
4. 再回头看 `WeldCommandAbstract.java`，理解脚本、偏移和树操作。
5. 最后读 `TrajectoryManager.java` 和 daemon 层，理解轨迹生成如何落地。

## 跑起来怎么验证

- 本地安装：`mvn install -Pdeploy_local`
- 远程部署：`mvn install -Pdeploy_remote`
- 启动脚本：`start.sh`

`src/test/java` 里的测试更像是轨迹和图表的小实验，不全是严格单测，但很适合拿来观察轨迹生成思路。

## 如果你想继续深入

最值得继续拆的三个方向是：

1. 焊接节点树怎么组织和校验
2. 焊机适配层怎么把不同品牌统一起来
3. 轨迹 JSON 是怎么从线段、圆弧和摆动参数拼出来的

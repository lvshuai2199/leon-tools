# VMware 虚拟机卡顿与 RT 线程报错排查记录

## 1. 基本信息

- 排查日期：2026-09-09
- 主机系统：Windows 10 Pro 22H2，Build 19045
- 主机 CPU：13th Gen Intel Core i5-1340P，16 线程
- 主机内存：约 16 GB
- VMware Workstation：16.2.0 build-18760230
- 虚拟机：`ELITE_CS_2.16`
- 虚拟机系统：Ubuntu 22.04.5 LTS
- 虚拟机内核：6.8.0-138-generic
- 虚拟机配置文件：

  `D:\VM\ELITE_CS_Simulator_2.16\ELITE_CS_Simulator_V2.16.1\ELITE_CS_Simulator\ELITE_CS_Simulator.vmx`

## 2. 原始问题

虚拟机内仿真程序反复输出：

```text
Failed to create RT thread policy=2 priority=60: Operation not permitted; using default scheduling.
```

同时 Ubuntu 图形界面或仿真程序会出现卡死、无响应等现象。

## 3. 排查结论

### 3.1 RT 线程报错的含义

`policy=2` 对应 Linux 的 `SCHED_RR` 实时调度策略，`priority=60` 是请求的实时优先级。

该报错表示当前 Ubuntu 用户没有设置实时调度策略所需的权限，系统返回 `EPERM`，程序随后自动降级为普通调度。它本身通常不是虚拟机卡死的直接原因，但可能导致仿真或音频相关线程实时性下降。

当前用户验证结果：

```text
ulimit -r
0

ulimit -l
495160

chrt -r 60 true
chrt: failed to set pid 0's policy: Operation not permitted
```

因此，Ubuntu 内的实时调度权限目前仍未配置成功。

### 3.2 虚拟机卡顿的主要原因

排查时发现：

1. 主机只有约 16 GB 内存，VM 配置使用 4 GB，VMware 启动时实际预留约 5.8 GB。主机曾经只剩约 2 GB 可用内存，并出现明显页面换入/换出。
2. Windows 的 Hyper-V/VBS 正在运行，VMware 日志显示：

   ```text
   Hyper-V detected by CPUID
   Monitor Mode: ULM
   ```

   这会让 VMware 通过 Windows Hypervisor Platform 运行，增加虚拟化开销和响应延迟。
3. 虚拟机原先启用了 3D 加速，日志中反复出现 SVGA3D/Mesa 初始化、图形设备重建和 VMware Tools 超时。该因素可能导致 Ubuntu 图形桌面或仿真界面卡死。
4. VMware Workstation 版本较旧，为 16.2.0；主机使用 Intel Iris Xe 显卡，显卡驱动也较旧。
5. 虚拟磁盘所在 NVMe 当前健康状态正常，但 Windows 历史日志中曾出现过磁盘控制器错误，后续仍应留意磁盘事件。

## 4. 已经执行的修改

### 4.1 禁止 Hyper-V 在本次启动中加载

执行的管理员命令：

```powershell
bcdedit /set hypervisorlaunchtype off
```

目的：让 VMware Workstation 直接使用 Intel VT-x，而不是运行在 Hyper-V/WHP 的兼容模式下。

重启后验证结果：

```text
HyperVisorPresent : False
VirtualizationBasedSecurityStatus : 0
hvhost : Stopped
```

同时 `systeminfo` 显示 Hyper-V 所需硬件能力可用，但没有显示正在运行的虚拟机监控程序。

影响：WSL2、Windows Sandbox、Virtual Machine Platform 等依赖 Hyper-V 的功能可能无法使用。当前系统功能检查结果显示这些组件均为 Disabled。

恢复命令：

```powershell
bcdedit /set hypervisorlaunchtype auto
```

执行后需要重启 Windows。

### 4.2 关闭目标虚拟机的 3D 加速

修改文件：

`D:\VM\ELITE_CS_Simulator_2.16\ELITE_CS_Simulator_V2.16.1\ELITE_CS_Simulator\ELITE_CS_Simulator.vmx`

修改内容：

```ini
mks.enable3d = "TRUE"
```

改为：

```ini
mks.enable3d = "FALSE"
```

目的：绕过 VMware SVGA3D/DX11 图形路径，降低 Intel 显卡驱动、Ubuntu vmwgfx/Mesa 和 VMware 3D 渲染之间导致图形卡死的概率。

已创建原配置备份：

`D:\VM\ELITE_CS_Simulator_2.16\ELITE_CS_Simulator_V2.16.1\ELITE_CS_Simulator\ELITE_CS_Simulator.vmx.codex-backup-20260909`

### 4.3 归档失效的 VMware 锁目录

原锁目录：

`ELITE_CS_Simulator.vmx.lck`

锁文件指向重启前已经不存在的 VMware 进程 `PID 19684`，因此判断为失效锁。该目录没有删除，而是改名归档为：

`ELITE_CS_Simulator.vmx.lck.stale-20260909-1041`

目的：解除失效锁，同时保留原目录以便回溯或恢复。

## 5. 修改后的验证结果

虚拟机通过 VMware 主程序成功启动，`vmrun list` 显示运行中。

新启动日志显示：

```text
mks.enable3d = "FALSE"
Monitor Mode: CPL0
VMMEM: Initial Reservation: 3771MB (MainMem=4096MB)
MKS-RenderMain: Found Full Renderer: MKSBasicOps
```

说明：

- `Monitor Mode: CPL0`：VMware 已经绕过 Hyper-V，直接使用硬件虚拟化。
- 3D 加速关闭后，渲染器从 DX11/SVGA3D 改为 `MKSBasicOps`。
- VMware 主机内存预留从约 5.8 GB 降到约 3.8 GB。

观察期间：

- `vmware-vmx` 进程持续响应。
- 虚拟机网络连续可达，IP 为 `192.168.249.131`。
- 没有出现新的 `hard reset`、`PANIC`、`Triple fault` 或 `GuestRpcSendTimedOut`。
- 主机仍可能因同时运行多个大型应用而只有约 2 GB 可用内存，并出现换页压力。

## 6. 尚未完成的事项

Ubuntu 内的实时调度权限尚未生效，当前仍为：

```text
ulimit -r = 0
chrt -r 60 true = Operation not permitted
```

因此，RT 线程报错仍会出现。前面建议的 `limits.d` 配置需要在 Ubuntu 虚拟机内真正执行，并且必须重新登录或重启后再验证。

建议配置：

```bash
u=$(id -un)

sudo apt update
sudo apt install -y rtkit

sudo tee /etc/security/limits.d/99-realtime.conf >/dev/null <<EOF
$u - rtprio 95
$u - nice -10
$u - memlock unlimited
EOF
```

然后重启 Ubuntu：

```bash
sudo reboot
```

重启后验证：

```bash
ulimit -r
ulimit -l
chrt -r 60 true
```

预期结果：`ulimit -r` 至少为 `60`，最好为 `95`；`chrt -r 60 true` 不再报错。

如果仍然是 `ulimit -r = 0`，需要继续检查：

- `/etc/pam.d/common-session` 是否加载 `pam_limits.so`
- 仿真程序是否通过 Snap、Flatpak 或 systemd 服务启动
- 启动程序是否运行在单独的沙箱或服务用户下
- 仿真程序是否自行调用了更高权限的实时调度策略

## 7. 回滚方法

### 恢复 3D 加速配置

关闭虚拟机后，将 `.vmx` 中：

```ini
mks.enable3d = "FALSE"
```

改回：

```ini
mks.enable3d = "TRUE"
```

也可以用备份文件恢复，但恢复前必须确认虚拟机处于关机状态。

### 恢复 Hyper-V 启动

以管理员身份执行：

```powershell
bcdedit /set hypervisorlaunchtype auto
```

然后重启 Windows。

## 8. 当前建议

1. 先保持 Hyper-V 关闭和 3D 加速关闭，继续使用该 VM 验证是否还会卡死。
2. 启动 VM 前关闭不必要的浏览器、远程控制、微信、语雀和开发工具，尽量保持至少 4 GB 主机可用内存。
3. 暂时不要把 VM 内存从 4 GB 调高。
4. 升级 VMware Workstation 到较新的版本，并更新 Intel Iris Xe 显卡驱动。
5. 单独处理 Ubuntu 的 `rtprio` 权限；该问题与 Hyper-V 设置是两个独立问题。


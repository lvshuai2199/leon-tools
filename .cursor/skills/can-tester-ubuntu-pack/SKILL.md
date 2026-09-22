---
name: can-tester-ubuntu-pack
description: >-
  Build the offline Ubuntu double-click package for can_tester_linux and place
  a desktop shortcut. Use when the user says 打包ubuntu、离线包、双击打开、
  发送快捷方式到桌面、CAN测试工具.desktop, or asks to ship can_tester to
  \\192.168.249.134\pc-dir as a no-network Ubuntu app.
---

# can_tester Ubuntu 离线包与桌面快捷方式

两条可以分开做。用户说「打包」且上下文是 Ubuntu / 双击 / 离线时走打包。用户说「快捷方式发到桌面」时只给桌面步骤，不必重新打包。

## 打包（在 Windows 仓库里执行）

工作目录是仓库里的 `can_tester_linux`。

```powershell
python scripts/pack_ubuntu.py
```

脚本会：

- 从 `can_tester/__init__.py` 读版本号
- 复用 `%TEMP%\cpython-linux.tar.gz`（没有则从 GitHub API 下载带 Tk 的 CPython 3.12 x86_64）
- 复用 `%TEMP%\can_tester_wheels` 里的 python-can 轮子（没有则 pip download）
- 打出 `%TEMP%\can_tester_linux-<version>-ubuntu.zip`
- 复制到 `\\192.168.249.134\pc-dir\can_tester_linux-<version>-ubuntu.zip`

这是 64 位 Ubuntu（x86_64）离线包。解压后双击 `CAN测试工具.desktop`，不联网、不装依赖。顶层目录名是 `can_tester_linux`。

普通源码 zip（给已有 Python 的机器、用 `./run.sh`）仍用：

```powershell
tar -a -c -f "$env:TEMP\can_tester_linux-<version>.zip" --exclude=__pycache__ --exclude=*.pyc -C D:\GitFiles\leon-tools can_tester_linux
Copy-Item -Force "$env:TEMP\can_tester_linux-<version>.zip" "\\192.168.249.134\pc-dir\"
```

## 发送快捷方式到桌面（在 Ubuntu 上执行）

快捷方式必须指向解压目录里的 `python/bin/python3`。只把 `.desktop` 拷到桌面，程序会找不到。

在解压后的 `can_tester_linux` 目录执行：

```bash
bash install_desktop_shortcut.sh
```

源码里同一脚本是 `can_tester_linux/scripts/install_desktop_shortcut.sh`。离线包根目录也会带上它。

若图标仍提示不受信任：在桌面图标上右键，选一次「允许启动」。

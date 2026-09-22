"""Build an offline Ubuntu folder: bundled CPython+Tk, app, desktop launcher."""
from __future__ import annotations

import os
import shutil
import stat
import tarfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT
TAR = Path(os.environ["TEMP"]) / "cpython-linux.tar.gz"
WHEELS = Path(os.environ["TEMP"]) / "can_tester_wheels"
STAGE = Path(os.environ["TEMP"]) / "can_tester_ubuntu_stage"


def _version() -> str:
    text = (ROOT / "can_tester" / "__init__.py").read_text(encoding="utf-8")
    for line in text.splitlines():
        if line.startswith("__version__"):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise SystemExit("missing __version__")

DESKTOP = """[Desktop Entry]
Version=1.0
Type=Application
Name=CAN 通用监听 / 发送
Comment=离线启动，无需联网安装
Exec=/bin/bash -c 'exec "$(dirname "$1")/python/bin/python3"' launch %k
Icon=utilities-terminal
Terminal=false
Categories=Utility;
StartupNotify=true
"""

START_SH = """#!/bin/bash
cd "$(dirname "$0")"
exec ./python/bin/python3
"""

SITECUSTOMIZE = '''"""Launch the CAN tester when this bundled interpreter is started with no args."""
import sys

def _launch() -> None:
    if len(sys.argv) > 1:
        return
    from can_tester.cli import main
    raise SystemExit(main())

_launch()
'''


def extract_python(dest: Path) -> None:
    with tarfile.open(TAR, "r:gz") as tf:
        members = [
            m
            for m in tf.getmembers()
            if "/share/" not in m.name.replace("\\", "/")
        ]
        links = [m for m in members if m.issym() or m.islnk()]
        files = [m for m in members if not (m.issym() or m.islnk())]
        for m in files:
            tf.extract(m, dest, set_attrs=False)
        pending = links
        for _ in range(6):
            still = []
            for m in pending:
                target = (dest / m.name).parent / m.linkname
                try:
                    target = target.resolve()
                except OSError:
                    still.append(m)
                    continue
                dst = dest / m.name
                if not target.exists():
                    continue
                dst.parent.mkdir(parents=True, exist_ok=True)
                if dst.exists() or dst.is_symlink():
                    if dst.is_dir() and not dst.is_symlink():
                        shutil.rmtree(dst)
                    else:
                        dst.unlink()
                try:
                    if target.is_dir():
                        shutil.copytree(target, dst)
                    else:
                        shutil.copy2(target, dst)
                except OSError:
                    continue
            pending = still
            if not pending:
                break
        if pending:
            raise SystemExit("unresolved symlinks: " + ", ".join(m.name for m in pending[:8]))


def install_wheels(site: Path) -> None:
    import zipfile as zf

    for whl in WHEELS.glob("*.whl"):
        with zf.ZipFile(whl) as z:
            for info in z.infolist():
                name = info.filename
                if name.endswith("/") or name.startswith(".."):
                    continue
                # cp310 extension will not load on this 3.12 build; wrapt falls back to .py
                if name.endswith(".so"):
                    continue
                if ".dist-info/" in name and name.endswith("RECORD"):
                    continue
                target = site / name
                target.parent.mkdir(parents=True, exist_ok=True)
                data = z.read(info)
                # normalize to LF for any scripts
                target.write_bytes(data)


def copy_app(app: Path) -> None:
    if app.exists():
        shutil.rmtree(app)
    app.mkdir(parents=True)

    def ignore(_dir, names):
        return [n for n in names if n == "__pycache__" or n.endswith(".pyc")]

    shutil.copytree(SRC / "can_tester", app / "can_tester", ignore=ignore)
    shutil.copytree(SRC / "modules", app / "modules", ignore=ignore)
    shutil.copytree(SRC / "profiles", app / "profiles", ignore=ignore)


def zip_tree(root: Path, dest: Path) -> None:
    if dest.exists():
        dest.unlink()
    with zipfile.ZipFile(dest, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for path in root.rglob("*"):
            rel = path.relative_to(root.parent).as_posix()
            info = zipfile.ZipInfo.from_file(path, rel)
            info.create_system = 3
            mode = 0o755 if path.suffix in {".desktop", ".sh"} or path.name in {"python3", "python3.12"} or os.access(path, os.X_OK) else 0o644
            if path.is_dir():
                mode = 0o755
            # binaries and scripts must be executable
            if path.suffix in {".so", ""} and path.is_file():
                if path.stat().st_size > 1000 and path.parent.name == "bin":
                    mode = 0o755
            info.external_attr = (mode & 0xFFFF) << 16
            if path.is_dir():
                continue
            data = path.read_bytes()
            if path.suffix in {".py", ".sh", ".desktop", ".pth"} or path.name == "sitecustomize.py":
                data = data.replace(b"\r\n", b"\n")
            z.writestr(info, data)


def main() -> None:
    version = _version()
    out = Path(os.environ["TEMP"]) / f"can_tester_linux-{version}-ubuntu.zip"
    share = Path(rf"\\192.168.249.134\pc-dir\can_tester_linux-{version}-ubuntu.zip")
    if STAGE.exists():
        shutil.rmtree(STAGE)
    root = STAGE / "can_tester_linux"
    root.mkdir(parents=True)
    print("extract python")
    extract_python(root)
    py_bin = root / "python" / "bin" / "python3"
    if not py_bin.exists():
        # some archives nest once
        alt = next(root.rglob("bin/python3.12"), None)
        raise SystemExit(f"python3 missing, sample={alt}")
    elf = py_bin.read_bytes()[:4]
    if elf != b"\x7fELF":
        raise SystemExit(f"python3 is not ELF: {elf!r} size={py_bin.stat().st_size}")
    tk = list((root / "python").rglob("_tkinter*.so"))
    if not tk:
        raise SystemExit("bundled python has no _tkinter")
    print("tk", tk[0].relative_to(root))
    site = next((root / "python").glob("lib/python3.*/site-packages"))
    print("site", site)
    install_wheels(site)
    app = root / "app"
    copy_app(app)
    (site / "can_tester_app.pth").write_bytes(b"../../../../app\n")
    (site / "sitecustomize.py").write_bytes(SITECUSTOMIZE.encode())
    (root / "CAN测试工具.desktop").write_bytes(DESKTOP.encode())
    (root / "start.sh").write_bytes(START_SH.encode())
    shortcut = SRC / "scripts" / "install_desktop_shortcut.sh"
    (root / "install_desktop_shortcut.sh").write_bytes(
        shortcut.read_bytes().replace(b"\r\n", b"\n")
    )
    print("zip")
    zip_tree(root, out)
    print("zip bytes", out.stat().st_size)
    shutil.copy2(out, share)
    print("copied", share, share.stat().st_size)


if __name__ == "__main__":
    main()

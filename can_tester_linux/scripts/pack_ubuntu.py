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
Exec=/bin/bash -c 'd="$1"; d="${d#file://}"; d="${d#localhost}"; cd "$(dirname "$d")" && exec ./start.sh' launch %k
Icon=utilities-terminal
Terminal=false
Categories=Utility;
StartupNotify=false
"""

START_SH = r"""#!/bin/bash
# 右键 Run as a Program 走这里。失败时把报错留在窗口里，避免一闪而过。
cd "$(dirname "$(readlink -f "$0")")"
ROOT="$(pwd)"
LOG="$ROOT/launch.log"
: >"$LOG"
if [[ -d /usr/share/X11/locale ]]; then
  export XLOCALEDIR=/usr/share/X11/locale
fi
if [[ -f /usr/share/X11/XKeysymDB ]]; then
  export XKEYSYMDB=/usr/share/X11/XKeysymDB
fi
# Bundled Tk has no Xft, so it can only draw the pixel font.
# Keep that face registered as the fallback.
FONTDIR="$ROOT/app/can_tester/fonts/x11"
if [[ -f "$FONTDIR/fonts.dir" ]] && command -v xset >/dev/null 2>&1; then
  xset +fp "$FONTDIR" >/dev/null 2>&1 || true
  xset fp rehash >/dev/null 2>&1 || true
fi
shopt -s nullglob
vendors=("$ROOT"/python/lib/python3.*/site-packages)
shopt -u nullglob
export PYTHONPATH="$ROOT/app${vendors[0]:+:${vendors[0]}}${PYTHONPATH:+:$PYTHONPATH}"
export CAN_TESTER_ROOT="$ROOT"
# Bundled sitecustomize would steal a no-arg interpreter. Outline probes pass
# this so they can exit after checking families.
export CAN_TESTER_NO_SITE_LAUNCH=1
use_outline=""
probe_outline() {
  "$@" - <<'PY' >>"$LOG" 2>&1
import os
import sys
import traceback
os.environ.pop("CAN_TESTER_SYSTEM_FONTS", None)
os.environ.pop("FONTCONFIG_FILE", None)
if sys.version_info < (3, 10):
    raise SystemExit(1)
try:
    import can  # vendored pure-python package
    import can_tester.gui  # registers WenQuanYi before Tk starts
    import tkinter as tk
    from tkinter import font as tkfont
    root = tk.Tk()
    root.withdraw()
    names = {name.casefold() for name in tkfont.families(root)}
    root.destroy()
except Exception:
    traceback.print_exc()
    raise SystemExit(1)
want = {
    "wenquanyi micro hei",
    "wenquanyi zen hei",
    "noto sans cjk sc",
    "noto sans cjk jp",
    "noto sans cjk tc",
    "source han sans sc",
    "source han sans cn",
    "droid sans fallback",
}
hit = sorted(names & want)
print("outline hit:", ", ".join(hit) or "(none)")
raise SystemExit(0 if hit else 1)
PY
}
XFT="$ROOT/python-xft"
XFT_PY="$XFT/usr/bin/python3.10"
loader=""
for cand in /lib64/ld-linux-x86-64.so.2 /lib/x86_64-linux-gnu/ld-linux-x86-64.so.2; do
  if [[ -e "$cand" ]]; then
    loader=$cand
    break
  fi
done
if [[ -n "$loader" && -f "$XFT_PY" ]]; then
  echo "---- probe python-xft ----" >>"$LOG"
  xft_lib="$XFT/usr/lib/x86_64-linux-gnu:$XFT/usr/lib"
  if PYTHONHOME="$XFT/usr" \
     LD_LIBRARY_PATH="$xft_lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
     TCL_LIBRARY="$XFT/usr/share/tcltk/tcl8.6" \
     TK_LIBRARY="$XFT/usr/share/tcltk/tk8.6" \
     probe_outline "$loader" "$XFT_PY"
  then
    use_outline="xft"
  fi
fi
if [[ -z "$use_outline" ]]; then
  for bin in python3 python3.12 python3.11 python3.10; do
    if ! command -v "$bin" >/dev/null 2>&1; then
      continue
    fi
    # Do not probe the copy shipped in this folder.
    if [[ "$(command -v "$bin")" == "$ROOT/python/bin/"* ]]; then
      continue
    fi
    echo "---- probe $bin ----" >>"$LOG"
    if probe_outline "$bin"; then
      use_outline=$bin
      break
    fi
  done
fi
unset CAN_TESTER_NO_SITE_LAUNCH
run_bundled() {
  echo "font: bundled bitmap" >>"$LOG"
  unset PYTHONHOME
  shopt -s nullglob
  tcl_hits=("$ROOT"/python/lib/tcl[0-9]*)
  tk_hits=("$ROOT"/python/lib/tk[0-9]*)
  shopt -u nullglob
  if [[ -n "${tcl_hits[0]:-}" ]]; then
    export TCL_LIBRARY="${tcl_hits[0]}"
  fi
  if [[ -n "${tk_hits[0]:-}" ]]; then
    export TK_LIBRARY="${tk_hits[0]}"
  fi
  export LD_LIBRARY_PATH="$ROOT/python/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  ./python/bin/python3 "$@" >>"$LOG" 2>&1
}
if [[ "$use_outline" == "xft" ]]; then
  echo "font: python-xft outline" >>"$LOG"
  export CAN_TESTER_NO_SITE_LAUNCH=1
  PYTHONHOME="$XFT/usr" \
  LD_LIBRARY_PATH="$XFT/usr/lib/x86_64-linux-gnu:$XFT/usr/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  TCL_LIBRARY="$XFT/usr/share/tcltk/tcl8.6" \
  TK_LIBRARY="$XFT/usr/share/tcltk/tk8.6" \
  "$loader" "$XFT_PY" -m can_tester "$@" >>"$LOG" 2>&1
  code=$?
  unset CAN_TESTER_NO_SITE_LAUNCH
  if [[ $code -ne 0 ]]; then
    echo "outline Tk exited $code, fallback" >>"$LOG"
    run_bundled "$@"
    code=$?
  fi
elif [[ -n "$use_outline" ]]; then
  echo "font: $use_outline outline" >>"$LOG"
  export CAN_TESTER_NO_SITE_LAUNCH=1
  unset FONTCONFIG_FILE
  "$use_outline" -m can_tester "$@" >>"$LOG" 2>&1
  code=$?
  unset CAN_TESTER_NO_SITE_LAUNCH
  if [[ $code -ne 0 ]]; then
    echo "outline Tk exited $code, fallback" >>"$LOG"
    run_bundled "$@"
    code=$?
  fi
else
  run_bundled "$@"
  code=$?
fi
if [[ $code -ne 0 ]]; then
  if [[ ! -s "$LOG" ]]; then
    echo "进程退出码 $code，没有输出" >"$LOG"
  fi
  if command -v zenity >/dev/null 2>&1; then
    zenity --text-info --title="CAN测试工具启动失败（退出码 $code）" --filename="$LOG" --width=640 --height=360
  elif command -v xmessage >/dev/null 2>&1; then
    xmessage -file "$LOG"
  fi
fi
exit "$code"
"""

SITECUSTOMIZE = '''"""Launch the CAN tester when this bundled interpreter is started with no args."""
import os
import sys
from pathlib import Path

def _prepare() -> None:
    lib = Path(sys.prefix) / "lib"
    tcl_hits = sorted(lib.glob("tcl[0-9]*"))
    tk_hits = sorted(lib.glob("tk[0-9]*"))
    if tcl_hits:
        os.environ.setdefault("TCL_LIBRARY", str(tcl_hits[0]))
    if tk_hits:
        os.environ.setdefault("TK_LIBRARY", str(tk_hits[0]))
    if os.path.isdir("/usr/share/X11/locale"):
        os.environ.setdefault("XLOCALEDIR", "/usr/share/X11/locale")
    if os.path.isfile("/usr/share/X11/XKeysymDB"):
        os.environ.setdefault("XKEYSYMDB", "/usr/share/X11/XKeysymDB")
    # Tk in this build has no Xft. Register the bitmap CJK face on the X server
    # before Tk opens the display. SystemExit must not be raised here: site.py
    # treats that as a fatal init_import_site error.
    font_dir = Path(sys.prefix).resolve().parent / "app" / "can_tester" / "fonts" / "x11"
    if (font_dir / "fonts.dir").is_file():
        import subprocess
        subprocess.run(
            ["xset", "+fp", str(font_dir)],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        subprocess.run(
            ["xset", "fp", "rehash"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

def _launch() -> None:
    if os.environ.get("CAN_TESTER_NO_SITE_LAUNCH") == "1":
        return
    if len(sys.argv) > 1:
        return
    code = 1
    try:
        from can_tester.cli import main
        code = main()
    except SystemExit as exc:
        code = exc.code if isinstance(exc.code, int) else 0
    except Exception:
        import traceback
        sys.stderr.write(traceback.format_exc())
        code = 1
    os._exit(code if isinstance(code, int) else 0)

_prepare()
_launch()
'''


def _extract_deb(deb: Path, dest: Path) -> None:
    import io
    import zstandard

    data = deb.read_bytes()
    if data[:8] != b"!<arch>\n":
        raise SystemExit(f"not a deb: {deb}")
    off = 8
    found = False
    while off + 60 <= len(data):
        hdr = data[off : off + 60]
        name = hdr[0:16].decode("ascii", "replace").strip()
        size = int(hdr[48:58].decode().strip() or "0")
        blob = data[off + 60 : off + 60 + size]
        off += 60 + size + (size % 2)
        if not name.startswith("data.tar"):
            continue
        found = True
        if name.endswith(".zst"):
            reader = zstandard.ZstdDecompressor().stream_reader(io.BytesIO(blob))
            blob = reader.read()
        with tarfile.open(fileobj=io.BytesIO(blob), mode="r:*") as tf:
            tf.extractall(dest)
    if not found:
        raise SystemExit(f"deb has no data.tar: {deb}")


def stage_outline_python(dest: Path) -> None:
    """Ubuntu 22.04 CPython 3.10 + Tk/Xft. The bundled Tk build cannot draw TrueType."""
    debs = Path(os.environ["TEMP"]) / "can_tester_xft_debs"
    if not debs.is_dir() or not any(debs.glob("*.deb")):
        raise SystemExit(f"missing outline-python debs in {debs}")
    scratch = Path(os.environ["TEMP"]) / "can_tester_xft_root"
    marker = scratch / "usr" / "bin" / "python3.10"
    if not marker.is_file():
        if scratch.exists():
            shutil.rmtree(scratch)
        scratch.mkdir(parents=True)
        for deb in sorted(debs.glob("*.deb")):
            _extract_deb(deb, scratch)
    if dest.exists():
        shutil.rmtree(dest)
    files = [
        "usr/bin/python3.10",
        "usr/lib/x86_64-linux-gnu/libpython3.10.so.1.0",
        "usr/lib/x86_64-linux-gnu/libtcl8.6.so",
        "usr/lib/x86_64-linux-gnu/libtcl8.6.so.0",
        "usr/lib/x86_64-linux-gnu/libtk8.6.so",
        "usr/lib/x86_64-linux-gnu/libtk8.6.so.0",
        "usr/lib/x86_64-linux-gnu/libXss.so.1.0.0",
        "usr/lib/libBLT.2.5.so.8.6",
    ]
    for rel in files:
        src = scratch / rel
        if not src.is_file():
            raise SystemExit(f"outline python missing {rel}")
        target = dest / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, target)
    xss = dest / "usr/lib/x86_64-linux-gnu/libXss.so.1.0.0"
    shutil.copy2(xss, xss.with_name("libXss.so.1"))

    def _ignore(_dir: str, names: list[str]) -> list[str]:
        drop = {"__pycache__", "config-3.10-x86_64-linux-gnu"}
        return [name for name in names if name in drop or name.endswith(".pyc")]

    shutil.copytree(scratch / "usr/lib/python3.10", dest / "usr/lib/python3.10", ignore=_ignore)
    shutil.copytree(scratch / "usr/share/tcltk", dest / "usr/share/tcltk")
    tkinter_so = dest / "usr/lib/python3.10/lib-dynload/_tkinter.cpython-310-x86_64-linux-gnu.so"
    init_tcl = dest / "usr/share/tcltk/tcl8.6/init.tcl"
    if not tkinter_so.is_file() or not init_tcl.is_file():
        raise SystemExit("outline python is missing Tk")


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
    share = Path(rf"\\192.168.249.137\pc-dir\can_tester_linux-{version}-ubuntu.zip")
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
    print("outline python")
    stage_outline_python(root / "python-xft")
    print("zip")
    zip_tree(root, out)
    print("zip bytes", out.stat().st_size)
    shutil.copy2(out, share)
    print("copied", share, share.stat().st_size)


if __name__ == "__main__":
    main()

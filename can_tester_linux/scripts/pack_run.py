"""Build a no-install .run that launches with the system Python and system fonts."""
from __future__ import annotations

import io
import os
import shutil
import tarfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WHEELS = Path(os.environ["TEMP"]) / "can_tester_wheels"
STAGE = Path(os.environ["TEMP"]) / "can_tester_run_stage"

HEADER = """#!/bin/bash
# 解压到临时目录后用系统 Python 启动。退出后删除临时目录，不安装。
set -euo pipefail
ROOT="$(mktemp -d /tmp/can_tester.XXXXXX)"
cleanup() { rm -rf "$ROOT"; }
trap cleanup EXIT
tail -c +0000000000 "$0" | tar -xz -C "$ROOT"
export CAN_TESTER_SYSTEM_FONTS=1
unset FONTCONFIG_FILE || true
cd "$ROOT/can_tester_linux"
./can_tester.run "$@"
exit $?
"""


def _version() -> str:
    text = (ROOT / "can_tester" / "__init__.py").read_text(encoding="utf-8")
    for line in text.splitlines():
        if line.startswith("__version__"):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise SystemExit("missing __version__")


def _ignore(_dir: str, names: list[str]) -> list[str]:
    return [n for n in names if n == "__pycache__" or n.endswith(".pyc") or n == ".venv"]


def _fill_vendor(vendor: Path) -> None:
    if not WHEELS.is_dir() or not any(WHEELS.glob("*.whl")):
        raise SystemExit(f"missing wheels in {WHEELS}")
    vendor.mkdir(parents=True)
    for whl in WHEELS.glob("*.whl"):
        with zipfile.ZipFile(whl) as z:
            for info in z.infolist():
                name = info.filename
                if name.endswith("/") or name.startswith("..") or name.endswith(".so"):
                    continue
                if ".dist-info/" in name and name.endswith("RECORD"):
                    continue
                target = vendor / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(z.read(info).replace(b"\r\n", b"\n"))


def _stage() -> Path:
    if STAGE.exists():
        shutil.rmtree(STAGE)
    app = STAGE / "can_tester_linux"
    app.mkdir(parents=True)
    shutil.copytree(ROOT / "can_tester", app / "can_tester", ignore=_ignore)
    shutil.copytree(ROOT / "modules", app / "modules", ignore=_ignore)
    shutil.copytree(ROOT / "profiles", app / "profiles", ignore=_ignore)
    launcher = (ROOT / "can_tester.run").read_bytes().replace(b"\r\n", b"\n")
    (app / "can_tester.run").write_bytes(launcher)
    _fill_vendor(app / "vendor")
    return app


def _tar_bytes(app: Path) -> bytes:
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz", format=tarfile.GNU_FORMAT) as tar:
        for path in app.rglob("*"):
            if not path.is_file():
                continue
            rel = Path("can_tester_linux") / path.relative_to(app)
            data = path.read_bytes()
            if path.suffix in {".py", ".sh", ".run", ".json", ".txt", ".md", ".pth"}:
                data = data.replace(b"\r\n", b"\n")
            info = tarfile.TarInfo(rel.as_posix())
            info.size = len(data)
            mode = 0o755 if path.suffix in {".run", ".sh"} else 0o644
            info.mode = mode
            tar.addfile(info, io.BytesIO(data))
    return buf.getvalue()


def main() -> None:
    version = _version()
    app = _stage()
    payload = _tar_bytes(app)
    header = HEADER.replace("\r\n", "\n").encode("utf-8")
    marker = b"0000000000"
    if header.count(marker) != 1:
        raise SystemExit("header offset placeholder missing")
    offset = len(header) + 1  # tail -c +N is 1-based
    header = header.replace(marker, f"{offset:010d}".encode("ascii"), 1)
    out = Path(os.environ["TEMP"]) / f"can_tester_linux-{version}.run"
    out.write_bytes(header + payload)
    print(out)
    print("bytes", out.stat().st_size)


if __name__ == "__main__":
    main()

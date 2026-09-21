"""Free-editable init row tables (JSON modules).

Primary schema is a list of send rows (InitRow). Gaogo/GOO and Megmeet samples
are just default JSON under modules/ — not hard-coded in run logic.

Backward compat: if old ``steps`` is present, convert send_frame + following
delay into rows; ignore wait_reply / write_param (fold label into note).
"""
from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Optional, Sequence

from can_tester.parse import format_data_hex, parse_can_data, parse_can_id_auto

USER_MODULES_DIR = Path.home() / ".can_tester" / "modules"


def package_modules_dir() -> Path:
    """Ship-with modules: <repo>/modules next to the can_tester package."""
    return Path(__file__).resolve().parent.parent / "modules"


@dataclass
class InitRow:
    """One freely editable TX row in an init module table."""

    enabled: bool = True
    can_id: int = 0
    extended: bool = False
    data: bytes = b""
    interval_ms: Optional[int] = None  # None / 0 => use module/global default
    note: str = ""
    raw: dict = field(default_factory=dict)

    @property
    def display(self) -> str:
        return self.note or f"0x{self.can_id:X}"

    def summary(self) -> str:
        cid = f"0x{self.can_id:X}"
        ext = "EXT" if self.extended else "STD"
        data = format_data_hex(self.data) or "(空)"
        iv = f"{self.interval_ms}ms" if self.interval_ms else "默认间隔"
        note = f" — {self.note}" if self.note else ""
        return f"TX {cid} {ext} [{len(self.data)}] {data} @{iv}{note}"

    def effective_interval_ms(self, default_ms: int) -> int:
        if self.interval_ms is None or int(self.interval_ms) <= 0:
            return max(0, int(default_ms))
        return int(self.interval_ms)


@dataclass
class InitModule:
    name: str
    rows: list[InitRow]
    description: str = ""
    default_interval_ms: int = 50
    mode: str = ""  # optional legacy label
    path: Optional[Path] = None
    raw: dict = field(default_factory=dict)

    # Legacy alias used by older CLI/GUI bits
    @property
    def steps(self) -> list[InitRow]:
        return self.rows

    def to_dict(self) -> dict:
        out: dict[str, Any] = {
            "name": self.name,
            "description": self.description,
            "default_interval_ms": int(self.default_interval_ms),
            "rows": [],
        }
        if self.mode:
            out["mode"] = self.mode
        for r in self.rows:
            d: dict[str, Any] = {
                "enabled": bool(r.enabled),
                "id": f"0x{r.can_id:X}",
                "extended": bool(r.extended),
                "data": format_data_hex(r.data),
                "note": r.note or "",
            }
            if r.interval_ms is not None and int(r.interval_ms) > 0:
                d["interval_ms"] = int(r.interval_ms)
            else:
                d["interval_ms"] = None
            out["rows"].append(d)
        return out

    def with_rows(self, rows: list[InitRow]) -> "InitModule":
        return InitModule(
            name=self.name,
            rows=list(rows),
            description=self.description,
            default_interval_ms=self.default_interval_ms,
            mode=self.mode,
            path=self.path,
            raw=dict(self.raw),
        )

    # Compat alias
    def with_steps(self, steps: list[InitRow]) -> "InitModule":
        return self.with_rows(steps)


@dataclass
class RowResult:
    index: int
    row: InitRow
    ok: bool
    message: str = ""


# Compat aliases
StepResult = RowResult
InitStep = InitRow


@dataclass
class RunResult:
    ok: bool
    module: InitModule
    completed: int
    failed_step: Optional[int] = None
    message: str = ""
    results: list[RowResult] = field(default_factory=list)
    params: dict = field(default_factory=dict)


class InitModuleError(Exception):
    """Load / validation / runtime error for an init module."""


def _parse_row(raw: dict, index: int) -> InitRow:
    if not isinstance(raw, dict):
        raise InitModuleError(f"行 {index + 1}: 必须是对象")
    enabled = bool(raw.get("enabled", True))
    id_text = str(raw.get("id", raw.get("can_id", "")))
    force_ext = raw.get("extended", None)
    if force_ext is not None:
        force_ext = bool(force_ext)
    try:
        can_id, extended = parse_can_id_auto(id_text, force_ext)
    except ValueError as exc:
        raise InitModuleError(f"行 {index + 1} CAN ID: {exc}") from exc
    try:
        data = parse_can_data(str(raw.get("data", "")))
    except ValueError as exc:
        raise InitModuleError(f"行 {index + 1} data: {exc}") from exc
    iv_raw = raw.get("interval_ms", raw.get("interval", None))
    interval_ms: Optional[int]
    if iv_raw is None or iv_raw == "":
        interval_ms = None
    else:
        try:
            interval_ms = int(iv_raw)
        except (TypeError, ValueError) as exc:
            raise InitModuleError(f"行 {index + 1} interval_ms 无效") from exc
        if interval_ms <= 0:
            interval_ms = None
    note = str(raw.get("note", raw.get("label", raw.get("remark", "")) or ""))
    return InitRow(
        enabled=enabled,
        can_id=can_id,
        extended=extended,
        data=data,
        interval_ms=interval_ms,
        note=note,
        raw=dict(raw),
    )


def _steps_to_rows(steps_raw: list) -> list[InitRow]:
    """Convert legacy step-type list into free send rows."""
    rows: list[InitRow] = []
    i = 0
    while i < len(steps_raw):
        raw = steps_raw[i]
        if not isinstance(raw, dict):
            i += 1
            continue
        stype = str(raw.get("type", "")).strip()
        if stype == "send_frame":
            row = _parse_row(
                {
                    "enabled": raw.get("enabled", True),
                    "id": raw.get("id", raw.get("can_id", "")),
                    "extended": raw.get("extended"),
                    "data": raw.get("data", ""),
                    "note": raw.get("label", raw.get("note", "")),
                    "interval_ms": None,
                },
                len(rows),
            )
            # Fold following delay into this row's interval
            if i + 1 < len(steps_raw) and isinstance(steps_raw[i + 1], dict):
                nxt = steps_raw[i + 1]
                if str(nxt.get("type", "")).strip() == "delay":
                    try:
                        ms = int(float(nxt.get("ms", nxt.get("delay_ms", 0))))
                    except (TypeError, ValueError):
                        ms = 0
                    if ms > 0:
                        row.interval_ms = ms
                    i += 1  # consume delay
            rows.append(row)
        elif stype in ("wait_reply", "write_param", "delay"):
            # Ignore standalone delay / wait / write_param in primary schema;
            # optionally fold note from wait/write into previous row.
            note_bit = str(raw.get("label", "") or "")
            if note_bit and rows and stype in ("wait_reply", "write_param"):
                prev = rows[-1]
                if prev.note:
                    prev.note = f"{prev.note}; {note_bit}"
                else:
                    prev.note = note_bit
        else:
            # Unknown legacy type — skip
            pass
        i += 1
    return rows


def load_module(path: Path | str) -> InitModule:
    p = Path(path).expanduser().resolve()
    if not p.is_file():
        raise InitModuleError(f"模块文件不存在: {p}")
    try:
        raw = json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise InitModuleError(f"JSON 解析失败 ({p}): {exc}") from exc
    if not isinstance(raw, dict):
        raise InitModuleError(f"模块根必须是对象: {p}")
    name = str(raw.get("name", "")).strip() or p.stem
    description = str(raw.get("description", "") or "")
    mode = str(raw.get("mode", "") or "")
    try:
        default_interval_ms = int(raw.get("default_interval_ms", 50))
    except (TypeError, ValueError):
        default_interval_ms = 50
    if default_interval_ms < 0:
        default_interval_ms = 0

    rows: list[InitRow] = []
    if isinstance(raw.get("rows"), list):
        # Empty rows OK (GUI「新建」blank module); user fills later.
        rows = [_parse_row(r, i) for i, r in enumerate(raw["rows"])]
    elif isinstance(raw.get("steps"), list) and raw["steps"]:
        rows = _steps_to_rows(raw["steps"])
        if not rows:
            raise InitModuleError(
                f"旧 steps 中无可转换的 send_frame 行: {p}"
            )
    else:
        raise InitModuleError(f"模块需要 rows（可空）或旧 steps 项: {p}")

    return InitModule(
        name=name,
        rows=rows,
        description=description,
        default_interval_ms=default_interval_ms,
        mode=mode,
        path=p,
        raw=raw,
    )


def save_module(module: InitModule, path: Path | str) -> Path:
    p = Path(path).expanduser().resolve()
    p.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(module.to_dict(), ensure_ascii=False, indent=2)
    p.write_text(text + "\n", encoding="utf-8")
    module.path = p
    return p


def module_search_dirs() -> list[Path]:
    dirs: list[Path] = []
    pkg = package_modules_dir()
    if pkg.is_dir():
        dirs.append(pkg)
    user = USER_MODULES_DIR
    if user.is_dir():
        dirs.append(user)
    return dirs




def list_modules(extra_dirs: list[Path] | None = None) -> list[InitModule]:
    """Load all *.json modules from search paths (package then user). Later overrides same stem."""
    by_stem: dict[str, InitModule] = {}
    dirs = list(module_search_dirs())
    if extra_dirs:
        dirs.extend(extra_dirs)
    for d in dirs:
        if not d.is_dir():
            continue
        for p in sorted(d.glob("*.json")):
            try:
                mod = load_module(p)
            except InitModuleError:
                continue
            by_stem[p.stem] = mod
    return sorted(by_stem.values(), key=lambda m: (m.mode or "", m.name))


def find_module_by_name_or_stem(key: str) -> Optional[InitModule]:
    """Resolve settings.init_module (name or file stem) to a loaded module."""
    key = (key or "").strip()
    if not key:
        return None
    # Direct path?
    p = Path(key).expanduser()
    if p.is_file():
        try:
            return load_module(p)
        except InitModuleError:
            return None
    for m in list_modules():
        if m.name == key:
            return m
        if m.path and m.path.stem == key:
            return m
        # label form "name [mode]"
        if m.mode and f"{m.name} [{m.mode}]" == key:
            return m
    return None


ProgressCb = Callable[[int, int, InitRow, str], None]


def select_rows(
    module: InitModule,
    *,
    only_enabled: bool = False,
    indices: Optional[Sequence[int]] = None,
) -> InitModule:
    """Return a shallow copy of *module* with a filtered row list."""
    if indices is not None:
        rows: list[InitRow] = []
        for i in indices:
            if i < 0 or i >= len(module.rows):
                raise InitModuleError(f"行索引越界: {i}")
            rows.append(module.rows[i])
        return module.with_rows(rows)
    if only_enabled:
        return module.with_rows([r for r in module.rows if r.enabled])
    return module


# Compat
select_steps = select_rows


def run_module(
    module: InitModule,
    session: Any = None,
    *,
    dry_run: bool = False,
    on_progress: Optional[ProgressCb] = None,
    drain_rx: Optional[Callable[[], list]] = None,  # unused; kept for API compat
    only_enabled: bool = False,
    indices: Optional[Sequence[int]] = None,
    default_interval_ms: Optional[int] = None,
) -> RunResult:
    """Send enabled/selected rows in order, sleeping per-row interval after each TX.

    ``default_interval_ms`` overrides module.default_interval_ms when provided
    (e.g. from AppSettings). Empty/0 row interval uses that default.
    """
    _ = drain_rx  # kept for signature compat with old wait_reply callers
    try:
        work = select_rows(module, only_enabled=only_enabled, indices=indices)
    except InitModuleError as exc:
        return RunResult(
            ok=False,
            module=module,
            completed=0,
            failed_step=None,
            message=str(exc),
            results=[],
            params={},
        )

    if not work.rows:
        return RunResult(
            ok=False,
            module=module,
            completed=0,
            failed_step=None,
            message="没有可执行的行（请勾选至少一行）",
            results=[],
            params={},
        )

    fallback = (
        int(default_interval_ms)
        if default_interval_ms is not None
        else int(module.default_interval_ms)
    )
    results: list[RowResult] = []
    total = len(work.rows)

    def progress(i: int, row: InitRow, msg: str) -> None:
        if on_progress:
            on_progress(i, total, row, msg)

    for i, row in enumerate(work.rows):
        progress(i, row, f"发送: {row.display}")
        try:
            ext = bool(row.extended)
            if dry_run:
                msg = (
                    f"dry-run TX 0x{row.can_id:X} "
                    f"{'EXT' if ext else 'STD'} [{len(row.data)}] "
                    + (format_data_hex(row.data) or "(空)")
                )
            else:
                if session is None or not getattr(session, "connected", False):
                    raise InitModuleError("未连接总线，无法发送")
                fr = session.send(row.can_id, row.data, ext)
                msg = f"TX {fr.id_text} [{fr.dlc}] {fr.data_hex}"
            results.append(RowResult(i, row, True, msg))

            # Per-row delay after send (including last row — product: interval after send)
            iv = row.effective_interval_ms(fallback)
            if iv > 0:
                if dry_run:
                    progress(i, row, f"dry-run delay {iv} ms")
                else:
                    time.sleep(iv / 1000.0)
        except InitModuleError as exc:
            results.append(RowResult(i, row, False, str(exc)))
            return RunResult(
                ok=False,
                module=module,
                completed=i,
                failed_step=i,
                message=f"行 {i + 1}/{total} 失败 ({row.display}): {exc}",
                results=results,
                params={},
            )
        except Exception as exc:
            results.append(RowResult(i, row, False, str(exc)))
            return RunResult(
                ok=False,
                module=module,
                completed=i,
                failed_step=i,
                message=f"行 {i + 1}/{total} 失败 ({row.display}): {exc}",
                results=results,
                params={},
            )

    label = "初始化" if indices is None else "发送"
    return RunResult(
        ok=True,
        module=module,
        completed=total,
        failed_step=None,
        message=f"{label}完成: {module.name}（{total} 行）",
        results=results,
        params={},
    )

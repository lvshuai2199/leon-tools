"""Editable auto-reply profiles (应答档).

Rows: enabled | match_id | reply HEX | delay_ms | note
Optional engine=megmeet → dynamic build_reply for matching rows.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

from can_tester.parse import format_data_hex, parse_can_data, parse_can_id_auto

USER_RESPONDER_DIR = Path.home() / ".can_tester" / "responder"


def package_responder_dir() -> Path:
    return Path(__file__).resolve().parent.parent / "profiles"


@dataclass
class ResponderRow:
    enabled: bool = True
    match_id: int = 0
    extended: bool = True
    reply: bytes = b""
    delay_ms: Optional[int] = None
    note: str = ""
    raw: dict = field(default_factory=dict)

    def effective_delay_ms(self, default_ms: int) -> int:
        if self.delay_ms is None or int(self.delay_ms) < 0:
            return max(0, int(default_ms))
        return int(self.delay_ms)


@dataclass
class SignalDef:
    """One configurable auto-reply signal (bit / u8 / u16) from JSON."""

    name: str = ""
    note: str = ""
    kind: str = "bit"  # bit | u8 | u16
    byte: int = 0
    bit: int = 0
    scale: float = 1.0
    default: Any = False
    key: str = ""  # optional sync key into megmeet ResponderState / module.state
    raw: dict = field(default_factory=dict)

    def rule_summary(self) -> str:
        k = (self.kind or "bit").strip().lower()
        if k == "bit":
            return f"b{int(self.byte)}.{int(self.bit)}"
        if k in ("u8", "uint8"):
            return f"u8@{int(self.byte)}"
        if k in ("u16", "uint16"):
            sc = float(self.scale) if self.scale else 1.0
            if abs(sc - 1.0) < 1e-9:
                return f"u16@{int(self.byte)}"
            return f"u16@{int(self.byte)}×{sc:g}"
        return k or "?"

    def to_dict(self) -> dict:
        k = (self.kind or "bit").strip().lower()
        if k in ("uint8",):
            k = "u8"
        if k in ("uint16",):
            k = "u16"
        out: dict[str, Any] = {
            "name": self.name or "",
            "note": self.note or "",
            "default": self.default,
        }
        if k == "bit":
            out["kind"] = "bit"
            out["byte"] = int(self.byte)
            out["bit"] = int(self.bit)
            out["default"] = bool(self.default)
        elif k == "u8":
            out["kind"] = "u8"
            out["byte"] = int(self.byte)
            out["scale"] = float(self.scale) if self.scale else 1.0
            try:
                out["default"] = float(self.default)
            except (TypeError, ValueError):
                out["default"] = 0
        else:
            out["kind"] = "u16"
            out["byte"] = int(self.byte)
            out["scale"] = float(self.scale) if self.scale else 1.0
            try:
                out["default"] = float(self.default)
            except (TypeError, ValueError):
                out["default"] = 0
        if self.key:
            out["key"] = str(self.key)
        return out


def _parse_signal(item: dict) -> SignalDef:
    kind = str(item.get("kind", "bit") or "bit").strip().lower()
    if kind in ("uint8",):
        kind = "u8"
    if kind in ("uint16",):
        kind = "u16"
    byte_i = 0
    bit_i = 0
    scale = 1.0
    try:
        byte_i = int(item.get("byte", 0) or 0)
    except (TypeError, ValueError):
        byte_i = 0
    byte_i = max(0, min(7, byte_i))
    if kind == "bit":
        try:
            bit_i = int(item.get("bit", 0) or 0)
        except (TypeError, ValueError):
            bit_i = 0
        bit_i = max(0, min(7, bit_i))
        default: Any = bool(item.get("default", False))
    else:
        try:
            scale = float(item.get("scale", 1.0) if item.get("scale", None) is not None else 1.0)
        except (TypeError, ValueError):
            scale = 1.0
        if scale == 0:
            scale = 1.0
        try:
            default = float(item.get("default", 0) or 0)
        except (TypeError, ValueError):
            default = 0.0
        if kind == "u8" and float(default) == int(default):
            default = int(default)
    return SignalDef(
        name=str(item.get("name", "") or ""),
        note=str(item.get("note", "") or ""),
        kind=kind if kind in ("bit", "u8", "u16") else "bit",
        byte=byte_i,
        bit=bit_i,
        scale=scale,
        default=default,
        key=str(item.get("key", "") or ""),
        raw=dict(item),
    )


def signal_defaults_dict(signals: list[SignalDef]) -> dict[str, Any]:
    """Runtime dict keyed by signal.name (fallback index)."""
    out: dict[str, Any] = {}
    for i, s in enumerate(signals):
        k = (s.name or "").strip() or f"signal_{i}"
        if s.kind == "bit":
            out[k] = bool(s.default)
        else:
            out[k] = s.default
    return out


def apply_signals_to_buffer(
    buf: bytearray | bytes,
    signals: list[SignalDef],
    values: dict[str, Any] | None = None,
) -> bytes:
    """Pack signal values onto an 8-byte (or longer) buffer; return bytes(buf)."""
    data = bytearray(buf if buf is not None else b"")
    if len(data) < 8:
        data.extend(b"\x00" * (8 - len(data)))
    vals = values if isinstance(values, dict) else {}
    for i, s in enumerate(signals):
        name = (s.name or "").strip() or f"signal_{i}"
        # Prefer name; also allow key alias
        if name in vals:
            v = vals[name]
        elif s.key and s.key in vals:
            v = vals[s.key]
        else:
            v = s.default
        kind = (s.kind or "bit").strip().lower()
        b = max(0, min(len(data) - 1, int(s.byte)))
        if kind == "bit":
            bit = max(0, min(7, int(s.bit)))
            if bool(v):
                data[b] = (data[b] | (1 << bit)) & 0xFF
            else:
                data[b] = (data[b] & ~(1 << bit)) & 0xFF
        elif kind in ("u8", "uint8"):
            scale = float(s.scale) if s.scale else 1.0
            if scale == 0:
                scale = 1.0
            try:
                raw = int(round(float(v) / scale))
            except (TypeError, ValueError):
                raw = 0
            data[b] = max(0, min(0xFF, raw)) & 0xFF
        elif kind in ("u16", "uint16"):
            scale = float(s.scale) if s.scale else 1.0
            if scale == 0:
                scale = 1.0
            try:
                raw = int(round(float(v) / scale))
            except (TypeError, ValueError):
                raw = 0
            raw = max(0, min(0xFFFF, raw))
            if b + 1 >= len(data):
                data.extend(b"\x00" * (b + 2 - len(data)))
            data[b] = (raw >> 8) & 0xFF
            data[b + 1] = raw & 0xFF
    return bytes(data[: max(8, len(data))])


def values_from_signals_for_state(
    signals: list[SignalDef], values: dict[str, Any]
) -> dict[str, Any]:
    """Map UI signal values → megmeet state keys (via SignalDef.key)."""
    out: dict[str, Any] = {}
    for i, s in enumerate(signals):
        if not s.key:
            continue
        name = (s.name or "").strip() or f"signal_{i}"
        if name in values:
            v = values[name]
        elif s.key in values:
            v = values[s.key]
        else:
            v = s.default
        if s.kind == "bit":
            out[s.key] = bool(v)
        else:
            try:
                num = float(v)
            except (TypeError, ValueError):
                num = 0.0
            if s.key in ("current", "errcode") or s.kind == "u8":
                out[s.key] = int(round(num))
            else:
                out[s.key] = num
    return out


@dataclass
class ResponderModule:
    name: str
    rows: list[ResponderRow]
    description: str = ""
    default_delay_ms: int = 0
    engine: str = ""
    state: dict = field(default_factory=dict)
    signals: list[SignalDef] = field(default_factory=list)
    path: Optional[Path] = None
    raw: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        out: dict[str, Any] = {
            "name": self.name,
            "description": self.description,
            "default_delay_ms": int(self.default_delay_ms),
            "engine": self.engine or "static",
            "rows": [],
        }
        if self.state:
            out["state"] = dict(self.state)
        if self.signals:
            out["signals"] = [s.to_dict() for s in self.signals]
        for r in self.rows:
            d: dict[str, Any] = {
                "enabled": bool(r.enabled),
                "match_id": f"0x{r.match_id:X}",
                "extended": bool(r.extended),
                "reply": format_data_hex(r.reply),
                "note": r.note or "",
            }
            if r.delay_ms is not None and int(r.delay_ms) >= 0:
                d["delay_ms"] = int(r.delay_ms)
            else:
                d["delay_ms"] = None
            out["rows"].append(d)
        return out

    def with_rows(self, rows: list[ResponderRow]) -> "ResponderModule":
        return ResponderModule(
            name=self.name,
            rows=list(rows),
            description=self.description,
            default_delay_ms=self.default_delay_ms,
            engine=self.engine,
            state=dict(self.state),
            signals=list(self.signals),
            path=self.path,
            raw=dict(self.raw),
        )

    def with_signals(self, signals: list[SignalDef]) -> "ResponderModule":
        return ResponderModule(
            name=self.name,
            rows=list(self.rows),
            description=self.description,
            default_delay_ms=self.default_delay_ms,
            engine=self.engine,
            state=dict(self.state),
            signals=list(signals),
            path=self.path,
            raw=dict(self.raw),
        )

    def is_megmeet_engine(self) -> bool:
        return (self.engine or "").strip().lower() in ("megmeet", "megmeet_welder", "dynamic")


def _parse_row(item: dict) -> ResponderRow:
    mid_raw = item.get("match_id", item.get("id", "0"))
    try:
        mid, ext_guess = parse_can_id_auto(str(mid_raw), None)
    except ValueError:
        mid, ext_guess = 0, True
    ext = bool(item.get("extended", ext_guess if mid > 0x7FF else True))
    reply_s = item.get("reply", item.get("data", "")) or ""
    try:
        reply = parse_can_data(str(reply_s)) if str(reply_s).strip() else b""
    except ValueError:
        reply = b""
    delay = item.get("delay_ms", item.get("interval_ms", None))
    if delay is None or delay == "":
        delay_i = None
    else:
        try:
            delay_i = int(delay)
        except (TypeError, ValueError):
            delay_i = None
    return ResponderRow(
        enabled=bool(item.get("enabled", True)),
        match_id=int(mid),
        extended=ext,
        reply=reply,
        delay_ms=delay_i,
        note=str(item.get("note", "") or ""),
        raw=dict(item),
    )


def load_responder_module(path: Path) -> ResponderModule:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError("应答档必须是 JSON 对象")
    rows_raw = raw.get("rows")
    if not isinstance(rows_raw, list):
        rows_raw = []
    rows = [_parse_row(x) for x in rows_raw if isinstance(x, dict)]
    state = raw.get("state") if isinstance(raw.get("state"), dict) else {}
    sigs_raw = raw.get("signals")
    signals: list[SignalDef] = []
    if isinstance(sigs_raw, list):
        for x in sigs_raw:
            if isinstance(x, dict):
                try:
                    signals.append(_parse_signal(x))
                except (TypeError, ValueError):
                    continue
    return ResponderModule(
        name=str(raw.get("name") or path.stem),
        rows=rows,
        description=str(raw.get("description") or ""),
        default_delay_ms=int(raw.get("default_delay_ms", 0) or 0),
        engine=str(raw.get("engine") or "static"),
        state=dict(state),
        signals=signals,
        path=path,
        raw=raw,
    )


def save_responder_module(mod: ResponderModule, path: Path | None = None) -> Path:
    p = path or mod.path
    if p is None:
        raise ValueError("未指定保存路径")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(mod.to_dict(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    mod.path = p
    return p


def list_responder_modules() -> list[ResponderModule]:
    found: dict[str, ResponderModule] = {}
    for base in (package_responder_dir(), USER_RESPONDER_DIR):
        if not base.is_dir():
            continue
        for p in sorted(base.glob("*.json")):
            try:
                found[p.stem] = load_responder_module(p)
            except (OSError, ValueError, json.JSONDecodeError):
                continue
    out: list[ResponderModule] = []
    seen: set[str] = set()
    for base in (package_responder_dir(), USER_RESPONDER_DIR):
        if not base.is_dir():
            continue
        for p in sorted(base.glob("*.json")):
            if p.stem in seen:
                continue
            preferred = USER_RESPONDER_DIR / f"{p.stem}.json"
            mod = found.get(p.stem)
            if preferred.is_file():
                try:
                    mod = load_responder_module(preferred)
                except (OSError, ValueError, json.JSONDecodeError):
                    pass
            if mod is not None:
                out.append(mod)
                seen.add(p.stem)
    return out


def find_responder_by_name_or_stem(key: str) -> Optional[ResponderModule]:
    key = (key or "").strip()
    if not key:
        return None
    for m in list_responder_modules():
        if m.name == key or (m.path and m.path.stem == key):
            return m
    for base in (USER_RESPONDER_DIR, package_responder_dir()):
        p = base / f"{key}.json"
        if p.is_file():
            try:
                return load_responder_module(p)
            except (OSError, ValueError, json.JSONDecodeError):
                return None
    return None


def matching_rows(mod: ResponderModule, can_id: int) -> list[ResponderRow]:
    return [r for r in mod.rows if r.enabled and int(r.match_id) == int(can_id)]

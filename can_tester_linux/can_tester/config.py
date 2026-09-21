"""Persisted app settings (~/.can_tester/config.json)."""
from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path

USER_DIR = Path.home() / ".can_tester"
CONFIG_PATH = USER_DIR / "config.json"

REPLY_COUNT_MIN = 1
REPLY_COUNT_MAX = 10
DEFAULT_REPLY_TIMEOUT_MS = 1000.0
DEFAULT_INTERVAL_MS = 50
# Periodic TX: silent retries before auto-stop (no modal spam).
PERIOD_FAIL_SILENT_MAX = 3

_HEX_ID_SPLIT = re.compile(r"[\s,;]+")


def parse_rx_id_whitelist(text: str) -> frozenset[int]:
    """Parse comma/space/semicolon separated hex CAN IDs.

    Empty / whitespace-only => empty frozenset (caller treats as "show all").
    Invalid tokens are skipped (best-effort).
    """
    raw = (text or "").strip()
    if not raw:
        return frozenset()
    out: set[int] = set()
    for tok in _HEX_ID_SPLIT.split(raw):
        if not tok:
            continue
        t = tok.strip().lower()
        if t.startswith("0x"):
            t = t[2:]
        if not t or any(c not in "0123456789abcdef" for c in t):
            continue
        try:
            v = int(t, 16)
        except ValueError:
            continue
        if 0 <= v <= 0x1FFFFFFF:
            out.add(v)
    return frozenset(out)


@dataclass
class AppSettings:
    """Init-on-connect + log / print filter preferences."""

    enable_init_on_connect: bool = False
    init_module: str = ""
    default_interval_ms: int = DEFAULT_INTERVAL_MS
    print_client_data: bool = False
    reply_count: int = 1
    reply_timeout_ms: float = DEFAULT_REPLY_TIMEOUT_MS
    rx_id_filter: str = ""
    rx_changes_only: bool = True
    # Best-effort shared / listen-focused SocketCAN open (see README).
    listen_only_shared: bool = False
    # Megmeet / generic 应答服务 (mutually exclusive with listen_only_shared).
    enable_responder: bool = False
    responder_module: str = ""
    # Show RX that matches a frame we just TX'd (socketcan/virtual receive_own).
    # Default False: timeline already logs TX from init/send; hide self-echo so
    # 6 TX does not look like a duplicate round. When True, show with 「本机回环」.
    show_self_echo: bool = False

    def clamp(self) -> "AppSettings":
        n = int(self.reply_count)
        if n < REPLY_COUNT_MIN:
            n = REPLY_COUNT_MIN
        if n > REPLY_COUNT_MAX:
            n = REPLY_COUNT_MAX
        t = float(self.reply_timeout_ms)
        if t < 50:
            t = 50.0
        if t > 60_000:
            t = 60_000.0
        iv = int(self.default_interval_ms)
        if iv < 0:
            iv = 0
        if iv > 60_000:
            iv = 60_000
        init_mod = str(self.init_module or "").strip()
        resp_mod = str(self.responder_module or "").strip()
        # v1.7.26: enable flags follow whether a concrete module/profile is selected.
        enable_init = bool(init_mod)
        er = bool(resp_mod)
        lo = bool(self.listen_only_shared)
        # Mutual exclusion: enabling one clears the other (responder wins if both).
        if er and lo:
            lo = False
        return AppSettings(
            enable_init_on_connect=enable_init,
            init_module=init_mod,
            default_interval_ms=iv,
            print_client_data=bool(self.print_client_data),
            reply_count=n,
            reply_timeout_ms=t,
            rx_id_filter=str(self.rx_id_filter or "").strip(),
            rx_changes_only=bool(self.rx_changes_only),
            listen_only_shared=lo,
            enable_responder=er,
            responder_module=resp_mod,
            show_self_echo=bool(self.show_self_echo),
        )


def load_settings(path: Path | None = None) -> AppSettings:
    p = path or CONFIG_PATH
    if not p.is_file():
        return AppSettings().clamp()
    try:
        raw = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return AppSettings().clamp()
    if not isinstance(raw, dict):
        return AppSettings().clamp()
    return AppSettings(
        enable_init_on_connect=bool(raw.get("enable_init_on_connect", False)),
        init_module=str(raw.get("init_module", "") or ""),
        default_interval_ms=int(
            raw.get("default_interval_ms", DEFAULT_INTERVAL_MS)
        ),
        print_client_data=bool(raw.get("print_client_data", False)),
        reply_count=int(raw.get("reply_count", 1)),
        reply_timeout_ms=float(
            raw.get("reply_timeout_ms", DEFAULT_REPLY_TIMEOUT_MS)
        ),
        rx_id_filter=str(raw.get("rx_id_filter", "") or ""),
        rx_changes_only=bool(raw.get("rx_changes_only", True)),
        listen_only_shared=bool(raw.get("listen_only_shared", False)),
        enable_responder=bool(raw.get("enable_responder", False)),
        responder_module=str(raw.get("responder_module", "") or ""),
        show_self_echo=bool(raw.get("show_self_echo", False)),
    ).clamp()


def save_settings(settings: AppSettings, path: Path | None = None) -> Path:
    p = path or CONFIG_PATH
    p.parent.mkdir(parents=True, exist_ok=True)
    s = settings.clamp()
    p.write_text(
        json.dumps(asdict(s), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return p


LogKey = tuple[str, int, bool]


def frame_log_key(direction: str, can_id: int, extended: bool) -> LogKey:
    """Stable key for last-shown payload: direction + id + extended flag."""
    return (str(direction), int(can_id), bool(extended))


def is_new_or_changed_payload(
    last: dict[LogKey, bytes],
    key: LogKey,
    data: bytes,
) -> bool:
    """True if this key is new or the bytes changed. Updates `last`."""
    payload = bytes(data)
    if last.get(key) == payload:
        return False
    last[key] = payload
    return True

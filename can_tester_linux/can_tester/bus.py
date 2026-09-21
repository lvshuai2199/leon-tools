from __future__ import annotations
import errno
import re
import queue
import socket
import threading
import time
from collections import deque
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import can
from can_tester.parse import format_can_id, format_data_hex

ARPHRD_CAN = 280
VIRTUAL_IFACE = "virtual"
# Fixed python-can VirtualBus channel id — all CanSession("virtual") in *this
# process* share one bus. Separate OS processes do NOT share virtual; use vcan0.
VIRTUAL_CHANNEL = "cansim-loopback"
DEFAULT_IFACE = "vcan0"

# Product hint when SocketCAN open fails due to occupancy / permission.
OCCUPANCY_HINT = (
    "接口已被其它程序占用（如 FullFunctionWelding），"
    "请先在插件断开或关闭本工具总线"
)

# errno values that usually mean "busy / not allowed to open"
_OCCUPANCY_ERRNOS = frozenset(
    {
        errno.EBUSY,
        errno.EACCES,
        errno.EPERM,
        getattr(errno, "EADDRINUSE", 98),
    }
)


@dataclass(frozen=True)
class CanFrame:
    direction: str
    timestamp: float
    can_id: int
    is_extended: bool
    data: bytes

    @property
    def dlc(self) -> int:
        return len(self.data)

    @property
    def id_text(self) -> str:
        return format_can_id(self.can_id, self.is_extended)

    @property
    def data_hex(self) -> str:
        return format_data_hex(self.data)


def list_can_interfaces() -> list[str]:
    found: list[str] = []
    net = Path("/sys/class/net")
    if net.is_dir():
        for p in sorted(net.iterdir()):
            try:
                if int((p / "type").read_text().strip()) == ARPHRD_CAN:
                    found.append(p.name)
            except (OSError, ValueError):
                pass
    names = list(found)
    if DEFAULT_IFACE not in names:
        names.insert(0, DEFAULT_IFACE)
    elif names[0] != DEFAULT_IFACE:
        names.remove(DEFAULT_IFACE)
        names.insert(0, DEFAULT_IFACE)
    if VIRTUAL_IFACE not in names:
        names.append(VIRTUAL_IFACE)
    return names


def is_socketcan_present(name: str) -> bool:
    try:
        return int((Path("/sys/class/net") / name / "type").read_text().strip()) == ARPHRD_CAN
    except (OSError, ValueError):
        return False


def _errno_of(exc: BaseException) -> int | None:
    """Best-effort extract errno from OSError / can errors / chained causes."""
    cur: BaseException | None = exc
    seen: set[int] = set()
    while cur is not None and id(cur) not in seen:
        seen.add(id(cur))
        if isinstance(cur, OSError) and cur.errno is not None:
            return int(cur.errno)
        # python-can often puts errno on .error / args
        err = getattr(cur, "error", None)
        if isinstance(err, int):
            return err
        if getattr(cur, "args", None):
            for a in cur.args:
                if isinstance(a, int) and a > 0:
                    return a
                if isinstance(a, OSError) and a.errno is not None:
                    return int(a.errno)
        cur = cur.__cause__ or cur.__context__
    return None


def is_occupancy_error(exc: BaseException) -> bool:
    """True if failure looks like busy / permission / exclusive open."""
    n = _errno_of(exc)
    if n is not None and n in _OCCUPANCY_ERRNOS:
        return True
    text = str(exc).lower()
    keys = (
        "busy",
        "resource busy",
        "device or resource busy",
        "permission denied",
        "operation not permitted",
        "address already in use",
        "already in use",
        "独占",
        "占用",
    )
    return any(k in text for k in keys)



def is_socketcan_up(name: str) -> bool:
    """True if netdev is administratively up (IFF_UP)."""
    base = Path("/sys/class/net") / name
    try:
        flags = int((base / "flags").read_text().strip(), 16)
        return (flags & 0x1) != 0
    except (OSError, ValueError):
        return False


def can_down_hint(channel: str, baud: str | None = None) -> str:
    br = (baud or "125000").strip() or "125000"
    ch = channel or "can0"
    return (
        f"CAN 口未启动（{ch} down），请先执行：\n"
        f"  sudo ip link set {ch} up type can bitrate {br}\n"
        f"（若是 vcan：sudo ip link set {ch} up）"
    )


def is_iface_down_error(exc: BaseException) -> bool:
    n = _errno_of(exc)
    if n is not None and n in (
        errno.ENETDOWN,
        getattr(errno, "ENETUNREACH", 101),
        100,  # ENETDOWN on Linux; also Error Code 100 in some wrappers
    ):
        return True
    text = str(exc).lower()
    if "network is down" in text or "enetdown" in text:
        return True
    if re.search(r"error code\s*100\b", text):
        return True
    return False


def format_bus_error(channel: str, exc: BaseException, *, prefix: str = "总线错误") -> str:
    """User-facing CAN errors — never label SocketCAN down as TCP 网络失败."""
    if is_occupancy_error(exc):
        return OCCUPANCY_HINT
    ch = (channel or "").strip()
    if is_iface_down_error(exc):
        return can_down_hint(ch or "can0")
    if ch and ch != VIRTUAL_IFACE and is_socketcan_present(ch) and not is_socketcan_up(ch):
        return can_down_hint(ch)
    d = str(exc)
    if "network is down" in d.lower():
        return can_down_hint(ch or "can0")
    return f"{prefix}: {d}"


def probe_socketcan_available(channel: str) -> None:
    """Non-destructive bind probe. Raises RuntimeError with occupancy hint on failure.

    SocketCAN RAW is normally shareable; this still catches EBUSY/EPERM-style
    failures from exclusive drivers / adapters before a full Bus open.
    Virtual interfaces are not probed.
    """
    name = channel.strip()
    if not name or name == VIRTUAL_IFACE:
        return
    if not is_socketcan_present(name):
        return
    sock = None
    try:
        # PF_CAN / SOCK_RAW / CAN_RAW — same as python-can
        pf = getattr(socket, "PF_CAN", getattr(socket, "AF_CAN", 29))
        can_raw = getattr(socket, "CAN_RAW", 1)
        sock = socket.socket(pf, socket.SOCK_RAW, can_raw)
        sock.bind((name,))
    except OSError as exc:
        if is_occupancy_error(exc):
            raise RuntimeError(OCCUPANCY_HINT) from exc
        # Other bind errors (ENODEV, ENETDOWN, …) — leave for full connect hint
        return
    finally:
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass


class CanSession:
    def __init__(self) -> None:
        self.rx_queue: queue.Queue[CanFrame] = queue.Queue()
        self.error_queue: queue.Queue[str] = queue.Queue()
        self._bus: Optional[can.BusABC] = None
        self._rx_thread: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self._send_lock = threading.Lock()
        self.channel = ""
        self.backend = ""
        self.last_error = ""
        # Best-effort shared / receive-focused open (see README).
        self.listen_only_shared = False
        # Local TX fingerprints for self-echo (receive_own_messages) matching.
        self._recent_tx: deque[tuple[int, bool, bytes, float]] = deque(maxlen=128)
        self._recent_tx_lock = threading.Lock()

    @property
    def connected(self) -> bool:
        return self._bus is not None

    def connect(self, channel: str, *, listen_only_shared: bool = False) -> None:
        self.disconnect()
        name = channel.strip()
        if not name:
            raise ValueError("请选择接口")
        self.listen_only_shared = bool(listen_only_shared)
        try:
            if name == VIRTUAL_IFACE:
                # virtual/vcan flows unchanged — ignore listen_only flag semantics
                bus = can.Bus(
                    interface="virtual",
                    channel=VIRTUAL_CHANNEL,
                    receive_own_messages=True,
                )
                backend = "virtual"
            else:
                if is_socketcan_present(name) and not is_socketcan_up(name):
                    raise RuntimeError(can_down_hint(name))
                # Probe first so occupancy shows a clear product message
                # (skip when listen_only_shared: still probe — shared open should
                # succeed on true SocketCAN; exclusive drivers still fail).
                try:
                    probe_socketcan_available(name)
                except RuntimeError:
                    raise
                except Exception:
                    pass
                # python-can SocketcanBus has no reliable listen-only / shared
                # exclusive flag. SocketCAN RAW is shareable by default; we open
                # normally. listen_only_shared is best-effort (documented).
                bus = can.Bus(
                    interface="socketcan",
                    channel=name,
                    receive_own_messages=True,
                    fd=False,
                )
                backend = "socketcan"
        except RuntimeError:
            raise
        except Exception as exc:
            self.last_error = str(exc)
            raise RuntimeError(self._hint(name, exc)) from exc
        self._bus = bus
        self.channel = name
        self.backend = backend
        self.last_error = ""
        self._stop.clear()
        self._rx_thread = threading.Thread(target=self._rx_loop, name="can-rx", daemon=True)
        self._rx_thread.start()

    def disconnect(self) -> None:
        self._stop.set()
        bus, self._bus = self._bus, None
        if bus is not None:
            try:
                bus.shutdown()
            except Exception:
                pass
        t = self._rx_thread
        if t and t.is_alive() and t is not threading.current_thread():
            t.join(timeout=1.0)
        self._rx_thread = None
        self.channel = ""
        self.backend = ""
        self.listen_only_shared = False
        with self._recent_tx_lock:
            self._recent_tx.clear()

    def send(self, can_id: int, data: bytes, extended: bool) -> CanFrame:
        with self._send_lock:
            if self._bus is None:
                raise RuntimeError("未连接接口")
            if self.listen_only_shared and self.backend == "socketcan":
                # Soft gate: 「只监听不独占」— refuse TX so we do not fight the
                # other app on the wire; RX continues. Virtual is unaffected.
                raise RuntimeError(
                    "当前为「只监听不独占」模式，已禁止发送；"
                    "请在设置中关闭该选项后再发"
                )
            self._bus.send(
                can.Message(
                    arbitration_id=can_id,
                    data=data,
                    is_extended_id=extended,
                    is_rx=False,
                )
            )
            payload = bytes(data)
            with self._recent_tx_lock:
                self._recent_tx.append(
                    (int(can_id), bool(extended), payload, time.time())
                )
        return CanFrame("TX", time.time(), can_id, extended, bytes(data))

    def consume_self_echo(
        self,
        can_id: int,
        extended: bool,
        data: bytes,
        *,
        max_age_s: float = 3.0,
    ) -> bool:
        """Match RX against a recent local TX (socketcan/virtual own-message echo).

        Consumes at most one pending TX stamp so identical frames still pair 1:1.
        """
        now = time.time()
        key = (int(can_id), bool(extended), bytes(data))
        with self._recent_tx_lock:
            kept: deque[tuple[int, bool, bytes, float]] = deque(maxlen=128)
            found = False
            for cid, ext, payload, ts in self._recent_tx:
                if (now - ts) > max_age_s:
                    continue
                if not found and (cid, ext, payload) == key:
                    found = True
                    continue
                kept.append((cid, ext, payload, ts))
            self._recent_tx = kept
            return found

    def drain_rx(self, limit: int = 200) -> list[CanFrame]:
        out: list[CanFrame] = []
        for _ in range(limit):
            try:
                out.append(self.rx_queue.get_nowait())
            except queue.Empty:
                break
        return out

    def _rx_loop(self) -> None:
        while not self._stop.is_set():
            bus = self._bus
            if bus is None:
                break
            try:
                msg = bus.recv(timeout=0.2)
            except Exception as exc:
                if not self._stop.is_set():
                    self.last_error = str(exc)
                    self.error_queue.put(format_bus_error(self.channel, exc, prefix="接收错误"))
                break
            if msg is None:
                continue
            self.rx_queue.put(
                CanFrame(
                    "RX",
                    float(msg.timestamp) if msg.timestamp else time.time(),
                    int(msg.arbitration_id),
                    bool(msg.is_extended_id),
                    bytes(msg.data),
                )
            )

    @staticmethod
    def _hint(channel: str, exc: Exception) -> str:
        if is_occupancy_error(exc):
            return OCCUPANCY_HINT
        if is_iface_down_error(exc):
            return can_down_hint(channel)
        if (
            channel
            and channel != VIRTUAL_IFACE
            and is_socketcan_present(channel)
            and not is_socketcan_up(channel)
        ):
            return can_down_hint(channel)
        d = str(exc)
        if OCCUPANCY_HINT in d:
            return OCCUPANCY_HINT
        if "network is down" in d.lower():
            return can_down_hint(channel)
        if channel == VIRTUAL_IFACE:
            return f"无法打开软件回环：{d}"
        return (
            f"无法打开 SocketCAN 接口 {channel}：{d}\n"
            "请确认已创建并启动，例如：\n"
            f"  sudo ip link set {channel} up type can bitrate 125000\n"
            "  sudo modprobe vcan && sudo ip link add dev vcan0 type vcan && sudo ip link set up vcan0\n"
            "或改选 virtual 做本机自发自收。\n"
            f"若接口在线但仍失败，也可能是：{OCCUPANCY_HINT}"
        )

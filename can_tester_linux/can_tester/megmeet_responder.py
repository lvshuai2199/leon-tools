"""Megmeet welder auto-reply helpers (from elite-notes megmeet_can_responder.py).

Listen EXT ROBOT_ID (plugin → welder), reply EXT WELDER_ID.
Pure functions + ResponderState for unit tests and engine=megmeet profiles.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

ROBOT_ID = 0x1FD07063
WELDER_ID = 0x1FD08063

CMD_POLL = 0xFF
BIT_WELD = 0  # robot data[2] bit0 (py weld)
BIT_SEEK = 1  # robot data[1] bit1 (py seek)


@dataclass
class RobotFrame:
    cmd: int = 0
    weld: bool = False
    seek: bool = False
    raw: bytes = b""


@dataclass
class ResponderState:
    ready: bool = True
    error: bool = False
    touch_ok: bool = False
    arc_ok: bool = False
    arc_ex: bool = False
    other_ex: bool = False
    stick: bool = False
    current: int = 180
    voltage: float = 22.5
    errcode: int = 0
    auto_reply: bool = True
    auto_arc: bool = True
    _prev_weld: bool = field(default=False, repr=False)

    def reset_edge(self) -> None:
        self._prev_weld = False


def parse_robot_frame(data: bytes) -> RobotFrame:
    """Match megmeet_can_responder.py: weld=data[2].bit0, seek=data[1].bit1."""
    raw = bytes(data or b"")
    cmd = int(raw[0]) if len(raw) >= 1 else 0
    b1 = int(raw[1]) if len(raw) >= 2 else 0
    b2 = int(raw[2]) if len(raw) >= 3 else 0
    return RobotFrame(
        cmd=cmd,
        weld=bool(b2 & (1 << BIT_WELD)),
        seek=bool(b1 & (1 << BIT_SEEK)),
        raw=raw,
    )


def build_reply(state: ResponderState) -> bytes:
    """Pack 8-byte welder reply.

    data[0] bit0 ready, bit1 error, bit2 touch_ok
    data[1] bit0 arc_ok, bit1 arc_ex, bit2 other_ex, bit3 stick
    data[2:4] current uint16 BE
    data[4:6] voltage*10 uint16 BE
    data[6] errcode
    data[7] reserved 0
    """
    b0 = 0
    if state.ready:
        b0 |= 0x01
    if state.error:
        b0 |= 0x02
    if state.touch_ok:
        b0 |= 0x04
    b1 = 0
    if state.arc_ok:
        b1 |= 0x01
    if state.arc_ex:
        b1 |= 0x02
    if state.other_ex:
        b1 |= 0x04
    if state.stick:
        b1 |= 0x08
    cur = max(0, min(0xFFFF, int(state.current)))
    volt_x10 = max(0, min(0xFFFF, int(round(float(state.voltage) * 10.0))))
    err = max(0, min(0xFF, int(state.errcode)))
    return bytes([
        b0 & 0xFF,
        b1 & 0xFF,
        (cur >> 8) & 0xFF,
        cur & 0xFF,
        (volt_x10 >> 8) & 0xFF,
        volt_x10 & 0xFF,
        err & 0xFF,
        0x00,
    ])


def apply_auto_arc(state: ResponderState, robot: RobotFrame) -> None:
    """Hardcoded megmeet_can_responder.py auto behavior (not auto_rules).

    - Seek level true → latch 寻位成功 (touch_ok); never auto-clear (match py).
      Seek runs even when auto_arc is off (same as py).
    - When auto_arc and CMD_POLL: weld rising → 起弧成功; falling → clear.
    """
    # Match py: `if seek: var_touch_ok.set(True)` — outside auto_arc gate.
    if robot.seek:
        state.touch_ok = True
    if not state.auto_arc:
        return
    if robot.cmd != CMD_POLL:
        return
    if robot.weld and not state._prev_weld:
        state.arc_ok = True
    elif (not robot.weld) and state._prev_weld:
        state.arc_ok = False
    state._prev_weld = bool(robot.weld)


def on_robot_frame(state: ResponderState, data: bytes) -> Optional[bytes]:
    robot = parse_robot_frame(data)
    apply_auto_arc(state, robot)
    if not state.auto_reply:
        return None
    return build_reply(state)


def state_from_mapping(raw: dict | None) -> ResponderState:
    d = raw if isinstance(raw, dict) else {}
    return ResponderState(
        ready=bool(d.get("ready", True)),
        error=bool(d.get("error", False)),
        touch_ok=bool(d.get("touch_ok", False)),
        arc_ok=bool(d.get("arc_ok", False)),
        arc_ex=bool(d.get("arc_ex", False)),
        other_ex=bool(d.get("other_ex", False)),
        stick=bool(d.get("stick", False)),
        current=int(d.get("current", 180)),
        voltage=float(d.get("voltage", 22.5)),
        errcode=int(d.get("errcode", 0)),
        auto_reply=bool(d.get("auto_reply", True)),
        auto_arc=bool(d.get("auto_arc", True)),
    )

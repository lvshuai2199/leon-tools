"""Megmeet Ehave2 + 固高 GOO combo: one profile, two roles.

Client（给焊机发）: robot TX 0x1FD07063, init F0–F5 then poll 0xFF.
Welder（模拟焊机）: listen for the robot frame, reply 0x1FD08063.

Byte layout follows 《固高定制 CAN》 and megmeet_can_responder.py
(Byte1 = data[0]).
"""
from __future__ import annotations

from can_tester.megmeet import PLUGIN_TX_ID, WELDER_RX_ID

COMBO_MEGMEET_GOO = "Megmeet - GOO"
ROLE_CLIENT = "给焊机发"
ROLE_WELDER = "模拟焊机"
INIT_MODULE_NAME = "固高 GOO"
RESPONDER_MODULE_NAME = "Megmeet 焊机应答"

ROBOT_ID = PLUGIN_TX_ID
WELDER_ID = WELDER_RX_ID

JOB_MODES = {0: "直流一元", 1: "脉冲一元", 2: "JOB"}
MODE_BY_NAME = {name: code for code, name in JOB_MODES.items()}


def pack_robot_poll(
    *,
    robot_err: bool = False,
    seek: bool = False,
    weld: bool = False,
    gas: bool = False,
    feed: bool = False,
    retract: bool = False,
    mode: int = 0,
    job: int = 0,
    current: int = 0,
    voltage: float = 0.0,
) -> bytes:
    """Robot → welder poll. Byte1 = 0xFF."""
    b1 = (0x01 if robot_err else 0) | (0x02 if seek else 0)
    b2 = (
        (0x01 if weld else 0)
        | (0x02 if gas else 0)
        | (0x04 if feed else 0)
        | (0x08 if retract else 0)
        | ((int(mode) & 0x07) << 5)
    )
    cur = max(0, min(0xFFFF, int(current)))
    volt_x10 = max(0, min(0xFFFF, int(round(float(voltage) * 10.0))))
    return bytes(
        [
            0xFF,
            b1 & 0xFF,
            b2 & 0xFF,
            int(job) & 0xFF,
            (cur >> 8) & 0xFF,
            cur & 0xFF,
            (volt_x10 >> 8) & 0xFF,
            volt_x10 & 0xFF,
        ]
    )


def describe_robot(data: bytes) -> dict:
    """Decode robot → welder (shown when this tool simulates the welder)."""
    raw = bytes(data or b"") + bytes(8)
    raw = raw[:8]
    cmd = raw[0]
    robot_err = bool(raw[1] & 0x01)
    seek = bool(raw[1] & 0x02)
    weld = bool(raw[2] & 0x01)
    gas = bool(raw[2] & 0x02)
    feed = bool(raw[2] & 0x04)
    retract = bool(raw[2] & 0x08)
    mode = (raw[2] >> 5) & 0x07
    job = raw[3]
    current = (raw[4] << 8) | raw[5]
    voltage = ((raw[6] << 8) | raw[7]) / 10.0
    if cmd in (0xF0, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5):
        line = f"初始化 F{cmd & 0x0F:X} → {raw[1]}"
        kind = "init"
    elif cmd == 0xFF:
        bits = [
            name
            for name, on in (
                ("起焊", weld),
                ("寻位", seek),
                ("检气", gas),
                ("送丝", feed),
                ("回抽", retract),
                ("机故", robot_err),
            )
            if on
        ]
        line = (
            f"轮询 JOB={job} {JOB_MODES.get(mode, str(mode))} "
            f"{current}A {voltage:.1f}V {' '.join(bits) or '空闲'}"
        )
        kind = "poll"
    else:
        line = f"命令 0x{cmd:02X}"
        kind = "other"
    return {
        "kind": kind,
        "cmd": cmd,
        "job": job,
        "mode": JOB_MODES.get(mode, str(mode)),
        "current": current,
        "voltage": voltage,
        "chips": [
            ("起焊", weld),
            ("寻位", seek),
            ("检气", gas),
            ("送丝", feed),
            ("回抽", retract),
            ("机故", robot_err),
        ],
        "line": line,
        "raw": raw,
    }


def describe_welder(data: bytes) -> dict:
    """Decode welder → robot (shown when this tool sends to the welder)."""
    raw = bytes(data or b"") + bytes(8)
    raw = raw[:8]
    ready = bool(raw[1] & 0x01)
    error = bool(raw[1] & 0x02)
    touch = bool(raw[1] & 0x04)
    arc = bool(raw[2] & 0x01)
    arc_ex = bool(raw[2] & 0x02)
    gas_ex = bool(raw[2] & 0x04)
    stick = bool(raw[2] & 0x08)
    over = bool(raw[2] & 0x80)
    current = (raw[3] << 8) | raw[4]
    voltage = ((raw[5] << 8) | raw[6]) / 10.0
    err = raw[7]
    chips = [
        ("就绪", ready),
        ("故障", error),
        ("寻位成功", touch),
        ("起弧成功", arc),
        ("电弧异常", arc_ex),
        ("气体流量异常", gas_ex),
        ("粘丝", stick),
        ("给定超范围", over),
    ]
    on = [name for name, flag in chips if flag]
    err_txt = f" 错{err}" if err else ""
    line = f"焊机 {' '.join(on) or '全关'} {current}A {voltage:.1f}V{err_txt}"
    return {
        "current": current,
        "voltage": voltage,
        "errcode": err,
        "chips": chips,
        "line": line,
        "raw": raw,
    }


def note_for_frame(can_id: int, data: bytes) -> str:
    if int(can_id) == ROBOT_ID:
        return describe_robot(data)["line"]
    if int(can_id) == WELDER_ID:
        return describe_welder(data)["line"]
    return ""

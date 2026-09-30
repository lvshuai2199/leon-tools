"""Jasic NBM-500R DeviceNet I/O (协议 V100，2025-08-29).

主站 MAC ID = 1，从站 MAC ID = 2。焊接信号在 Poll I/O 里，不在显式报文里：

- 焊机输入（机器人 → 焊机）12 字节，主站轮询 ID ``0x0415``
- 焊机输出（焊机 → 机器人）13 字节，从站应答 ID ``0x03C2``

两路都超过 8 字节，CAN 数据首字节是 DeviceNet 分段标志，后面才是有效字节。
多字节数值为小端。模式占输入字节 0 的 bit2–bit4，bit2 为低位。
"""
from __future__ import annotations

from dataclasses import dataclass

COMBO_JASIC_NBM = "Jasic NBM-500R"

MASTER_MAC = 1
SLAVE_MAC = 2

RAW_MAX = 65535
IN_CURRENT_A = 550.0
IN_WFS_M_MIN = 28.0
IN_VOLT = 50.0
OUT_CURRENT_A = 1000.0
OUT_VOLT = 100.0
INPUT_LEN = 12
OUTPUT_LEN = 13

MODE_ORDER = (
    "直流一元化",
    "脉冲一元化",
    "JOB模式",
    "近控模式",
    "分别模式",
)

INPUT_CHIP_NAMES = (
    "开始焊接",
    "机器人就绪",
    "气体检测",
    "点动送丝",
    "反抽送丝",
    "故障复位",
    "寻位使能",
)
OUTPUT_CHIP_NAMES = (
    "起弧成功",
    "焊接状态",
    "焊机故障",
    "通讯就绪",
    "寻位成功",
    "送丝机正常",
    "给定超范围",
)


def _group2(mac: int, message: int) -> int:
    """Group 2：bit10-9=10，bit8-3=MAC，bit2-0=Message ID。"""
    return 0x400 | ((int(mac) & 0x3F) << 3) | (int(message) & 0x07)


def _group1(message: int, mac: int) -> int:
    """Group 1：bit10=0，bit9-6=Message ID，bit5-0=MAC。"""
    return ((int(message) & 0x0F) << 6) | (int(mac) & 0x3F)


# 手册示例：主站轮询 / 从站 I/O 应答 / 显式请求与应答。
POLL_ID = _group2(SLAVE_MAC, 5)  # 0x0415
POLL_RSP_ID = _group1(15, SLAVE_MAC)  # 0x03C2
EXPLICIT_REQ_ID = _group2(SLAVE_MAC, 4)  # 0x0414
EXPLICIT_UNC_ID = _group2(SLAVE_MAC, 6)  # 0x0416
EXPLICIT_RSP_ID = _group2(SLAVE_MAC, 3)  # 0x0413
DUP_MAC_ID = _group2(MASTER_MAC, 7)  # 0x040F
UCMM_OPEN_ID = 0x780 | MASTER_MAC  # 0x0781

EXPLICIT_REQ_IDS = (EXPLICIT_REQ_ID, EXPLICIT_UNC_ID)

# Get_Attribute_Single 返回值，按手册示例帧（小端）。
_GET_ATTR = {
    (0x01, 0x01, 0x01): bytes.fromhex("6C00"),  # 供应商 ID 0x006C
    (0x01, 0x01, 0x02): bytes.fromhex("0C00"),  # 设备类型 0x000C
    (0x01, 0x01, 0x03): bytes.fromhex("8214"),  # 产品代码 0x1482
    (0x05, 0x02, 0x07): bytes.fromhex("0D00"),  # 生产长度 13（焊机输出）
    (0x05, 0x02, 0x08): bytes.fromhex("0C00"),  # 消费长度 12（焊机输入）
}


@dataclass(frozen=True)
class CanStep:
    can_id: int
    data: bytes
    wait_rsp: bool = False


# 手册第 3 节主站帧。重复 MAC 检测发两次且不应收到应答。
MASTER_SETUP_FRAMES: tuple[CanStep, ...] = (
    CanStep(DUP_MAC_ID, bytes.fromhex("00080006F58912"), False),
    CanStep(DUP_MAC_ID, bytes.fromhex("00080006F58912"), False),
    CanStep(UCMM_OPEN_ID, bytes.fromhex("014B0203"), False),
    CanStep(EXPLICIT_UNC_ID, bytes.fromhex("414B03010101"), True),
    CanStep(EXPLICIT_REQ_ID, bytes.fromhex("411005010C03"), True),
    CanStep(EXPLICIT_REQ_ID, bytes.fromhex("010E010101"), True),
    CanStep(EXPLICIT_REQ_ID, bytes.fromhex("410E010102"), True),
    CanStep(EXPLICIT_REQ_ID, bytes.fromhex("010E010103"), True),
    CanStep(EXPLICIT_REQ_ID, bytes.fromhex("414B03010201"), True),
    CanStep(EXPLICIT_REQ_ID, bytes.fromhex("010E050207"), True),
    CanStep(EXPLICIT_REQ_ID, bytes.fromhex("410E050208"), True),
    CanStep(EXPLICIT_REQ_ID, bytes.fromhex("0110050209FA00"), True),
)


def mode_code(name: str) -> int:
    try:
        return MODE_ORDER.index((name or "").strip())
    except ValueError:
        return 0


def _to_raw(value: float, full_scale: float) -> int:
    try:
        number = float(value)
    except (TypeError, ValueError):
        number = 0.0
    if full_scale <= 0:
        return 0
    if number < 0:
        number = 0.0
    if number > full_scale:
        number = full_scale
    return int(round(number / full_scale * RAW_MAX))


def _from_raw(raw: int, full_scale: float) -> float:
    try:
        number = int(raw)
    except (TypeError, ValueError):
        number = 0
    if number < 0:
        number = 0
    if number > RAW_MAX:
        number = RAW_MAX
    return number / RAW_MAX * full_scale


def current_a_to_raw(amps: float) -> int:
    return _to_raw(amps, IN_CURRENT_A)


def raw_to_current_a(raw: int) -> float:
    return _from_raw(raw, IN_CURRENT_A)


def wfs_to_raw(speed: float) -> int:
    return _to_raw(speed, IN_WFS_M_MIN)


def raw_to_wfs(raw: int) -> float:
    return _from_raw(raw, IN_WFS_M_MIN)


def voltage_in_to_raw(volts: float) -> int:
    return _to_raw(volts, IN_VOLT)


def raw_to_voltage_in(raw: int) -> float:
    return _from_raw(raw, IN_VOLT)


def trim_to_raw(percent: float) -> int:
    try:
        number = float(percent)
    except (TypeError, ValueError):
        number = 0.0
    if number < -30.0:
        number = -30.0
    if number > 30.0:
        number = 30.0
    return _to_raw(number + 30.0, 60.0)


def raw_to_trim(raw: int) -> float:
    return _from_raw(raw, 60.0) - 30.0


def current_out_to_raw(amps: float) -> int:
    return _to_raw(amps, OUT_CURRENT_A)


def raw_to_current_out(raw: int) -> float:
    return _from_raw(raw, OUT_CURRENT_A)


def voltage_out_to_raw(volts: float) -> int:
    return _to_raw(volts, OUT_VOLT)


def raw_to_voltage_out(raw: int) -> float:
    return _from_raw(raw, OUT_VOLT)


def fmt_trim(percent: float) -> str:
    if abs(float(percent)) < 0.05:
        percent = 0.0
    return f"{float(percent):.1f}"


def _u16_le(raw: int) -> tuple[int, int]:
    number = max(0, min(RAW_MAX, int(raw)))
    return number & 0xFF, (number >> 8) & 0xFF


def _read_u16_le(data: bytes, offset: int) -> int:
    lo = data[offset] if offset < len(data) else 0
    hi = data[offset + 1] if offset + 1 < len(data) else 0
    return lo | (hi << 8)


def _fit(data: bytes, size: int) -> bytes:
    raw = bytes(data or b"")
    if len(raw) >= size:
        return raw[:size]
    return raw + bytes(size - len(raw))


def pack_input(
    *,
    weld: bool = False,
    robot_ready: bool = False,
    mode: int = 0,
    gas: bool = False,
    inch: bool = False,
    retract: bool = False,
    fault_reset: bool = False,
    touch_enable: bool = False,
    given_raw: int = 0,
    arc_raw: int = 0,
    job: int = 0,
) -> bytes:
    """12-byte welder input. Byte 4 is the low byte of the given value."""
    mode_i = max(0, min(7, int(mode)))
    b0 = (0x01 if weld else 0) | (0x02 if robot_ready else 0) | ((mode_i & 0x07) << 2)
    b1 = (
        (0x01 if gas else 0)
        | (0x02 if inch else 0)
        | (0x04 if retract else 0)
        | (0x08 if fault_reset else 0)
        | (0x10 if touch_enable else 0)
    )
    given_lo, given_hi = _u16_le(given_raw)
    arc_lo, arc_hi = _u16_le(arc_raw)
    job_b = max(0, min(0xFF, int(job)))
    return bytes(
        [b0 & 0xFF, b1 & 0xFF, 0, 0, given_lo, given_hi, arc_lo, arc_hi, job_b, 0, 0, 0]
    )


def pack_output(
    *,
    arc_ok: bool = False,
    welding: bool = False,
    fault: bool = False,
    comm_ready: bool = True,
    touch_ok: bool = False,
    feeder_ok: bool = True,
    range_over: bool = False,
    alarm: int = 0,
    current_raw: int = 0,
    voltage_raw: int = 0,
) -> bytes:
    """13-byte welder output. Defaults match the manual idle example."""
    b0 = (
        (0x01 if arc_ok else 0)
        | (0x04 if welding else 0)
        | (0x20 if fault else 0)
        | (0x40 if comm_ready else 0)
    )
    b3 = (0x01 if touch_ok else 0) | (0x08 if feeder_ok else 0) | (0x80 if range_over else 0)
    cur_lo, cur_hi = _u16_le(current_raw)
    vol_lo, vol_hi = _u16_le(voltage_raw)
    alarm_b = max(0, min(0xFF, int(alarm)))
    return bytes(
        [
            b0 & 0xFF,
            alarm_b,
            0,
            b3 & 0xFF,
            cur_lo,
            cur_hi,
            vol_lo,
            vol_hi,
            0,
            0,
            0,
            0,
            0,
        ]
    )


@dataclass(frozen=True)
class RobotCommand:
    weld: bool
    robot_ready: bool
    mode: int
    gas: bool
    inch: bool
    retract: bool
    fault_reset: bool
    touch_enable: bool
    given_raw: int
    arc_raw: int
    job: int


@dataclass(frozen=True)
class WelderStatus:
    arc_ok: bool
    welding: bool
    fault: bool
    comm_ready: bool
    touch_ok: bool
    feeder_ok: bool
    range_over: bool
    alarm: int
    current_raw: int
    voltage_raw: int


def parse_input(data: bytes) -> RobotCommand:
    raw = _fit(data, INPUT_LEN)
    b0, b1 = raw[0], raw[1]
    return RobotCommand(
        weld=bool(b0 & 0x01),
        robot_ready=bool(b0 & 0x02),
        mode=(b0 >> 2) & 0x07,
        gas=bool(b1 & 0x01),
        inch=bool(b1 & 0x02),
        retract=bool(b1 & 0x04),
        fault_reset=bool(b1 & 0x08),
        touch_enable=bool(b1 & 0x10),
        given_raw=_read_u16_le(raw, 4),
        arc_raw=_read_u16_le(raw, 6),
        job=raw[8],
    )


def parse_output(data: bytes) -> WelderStatus:
    raw = _fit(data, OUTPUT_LEN)
    b0, b3 = raw[0], raw[3]
    return WelderStatus(
        arc_ok=bool(b0 & 0x01),
        welding=bool(b0 & 0x04),
        fault=bool(b0 & 0x20),
        comm_ready=bool(b0 & 0x40),
        touch_ok=bool(b3 & 0x01),
        feeder_ok=bool(b3 & 0x08),
        range_over=bool(b3 & 0x80),
        alarm=raw[1],
        current_raw=_read_u16_le(raw, 4),
        voltage_raw=_read_u16_le(raw, 6),
    )


def describe_input(data: bytes) -> dict:
    cmd = parse_input(data)
    chips = list(
        zip(
            INPUT_CHIP_NAMES,
            (
                cmd.weld,
                cmd.robot_ready,
                cmd.gas,
                cmd.inch,
                cmd.retract,
                cmd.fault_reset,
                cmd.touch_enable,
            ),
        )
    )
    mode_name = MODE_ORDER[cmd.mode] if 0 <= cmd.mode < len(MODE_ORDER) else str(cmd.mode)
    amps = raw_to_current_a(cmd.given_raw)
    wfs = raw_to_wfs(cmd.given_raw)
    volts = raw_to_voltage_in(cmd.arc_raw)
    trim = raw_to_trim(cmd.arc_raw)
    on = " ".join(name for name, flag in chips if flag)
    line = (
        f"{mode_name} JOB={cmd.job} "
        f"{amps:.1f}A {wfs:.2f}m/min {volts:.1f}V 修正{fmt_trim(trim)}%"
    )
    if on:
        line = f"{line} {on}"
    return {"chips": chips, "line": line, "raw": _fit(data, INPUT_LEN), "cmd": cmd}


def describe_output(data: bytes) -> dict:
    status = parse_output(data)
    chips = list(
        zip(
            OUTPUT_CHIP_NAMES,
            (
                status.arc_ok,
                status.welding,
                status.fault,
                status.comm_ready,
                status.touch_ok,
                status.feeder_ok,
                status.range_over,
            ),
        )
    )
    amps = raw_to_current_out(status.current_raw)
    volts = raw_to_voltage_out(status.voltage_raw)
    on = " ".join(name for name, flag in chips if flag) or "全关"
    line = f"{on} {amps:.1f}A {volts:.1f}V 报警{status.alarm}"
    return {"chips": chips, "line": line, "raw": _fit(data, OUTPUT_LEN), "status": status}


def fragment_io(payload: bytes) -> list[bytes]:
    """Split an I/O payload into DeviceNet fragments (7 data bytes each).

    Header bits 7-6: 00 first, 01 middle, 10 last. Bits 5-0: fragment index.
    A payload that already fits in one CAN frame is returned unchanged.
    """
    data = bytes(payload or b"")
    if not data:
        return []
    if len(data) <= 7:
        return [data]
    frames: list[bytes] = []
    offset = 0
    index = 0
    while offset < len(data):
        chunk = data[offset : offset + 7]
        offset += 7
        last = offset >= len(data)
        if index == 0:
            kind = 0
        elif last:
            kind = 2
        else:
            kind = 1
        header = ((kind & 0x3) << 6) | (index & 0x3F)
        frames.append(bytes([header]) + chunk)
        index += 1
    return frames


class IoReassembler:
    """Reassemble one DeviceNet fragmented I/O payload."""

    def __init__(self) -> None:
        self._buf = bytearray()
        self._next = 0
        self._open = False

    def reset(self) -> None:
        self._buf.clear()
        self._next = 0
        self._open = False

    def feed(self, frame: bytes) -> bytes | None:
        raw = bytes(frame or b"")
        if not raw:
            return None
        kind = (raw[0] >> 6) & 0x3
        count = raw[0] & 0x3F
        body = raw[1:]
        if kind == 0:
            self._buf = bytearray(body)
            self._next = 1
            self._open = True
            return None
        if not self._open or count != self._next or kind not in (1, 2):
            self.reset()
            return None
        self._buf.extend(body)
        self._next = count + 1
        if kind == 1:
            return None
        out = bytes(self._buf)
        self.reset()
        return out


# WeldingTools Jasic JTE / EVOLVE（Classic CAN，125 kbit/s）。
# 机器人每 20 ms：0x202 RPDO1、0x302 RPDO2、0x080 SYNC。
# 焊机须在 SYNC 后回 0x182 TPDO1（8 字节）和 0x282 TPDO2，否则 3 秒判断开。
SYNC_ID = 0x080
TPDO1_ID = 0x182
TPDO2_ID = 0x282
RPDO1_ID = 0x202
RPDO2_ID = 0x302

JTE_MODES = {
    0: "直流一元化",
    1: "脉冲一元化",
    2: "JOB模式",
    3: "近控模式",
    4: "分别模式",
    5: "双脉冲",
}
JTE_ROBOT_CHIPS = (
    "起弧",
    "机器人就绪",
    "检气",
    "送丝",
    "退丝",
    "故障复位",
    "寻位",
)
JTE_STATUS_CHIPS = ("就绪", "在焊")


def _word_le(data: bytes, offset: int) -> int:
    raw = bytes(data or b"")
    lo = raw[offset] if offset < len(raw) else 0
    hi = raw[offset + 1] if offset + 1 < len(raw) else 0
    return lo | (hi << 8)


def _put_word_le(buf: bytearray, offset: int, value: int) -> None:
    number = int(value) & 0xFFFF
    buf[offset] = number & 0xFF
    buf[offset + 1] = (number >> 8) & 0xFF


def pack_rpdo1(
    *,
    weld: bool = False,
    robot_ready: bool = True,
    mode: int = 0,
    gas: bool = False,
    feed: bool = False,
    retract: bool = False,
    fault_reset: bool = False,
    touch: bool = False,
    program: int = 0,
    job: int = 0,
    parameter1: int = 0,
) -> bytes:
    """Robot RPDO1. Bit layout matches WeldingTools JasicJteCanProtocol.rpdo1."""
    control = 0
    if weld:
        control |= 1 << 0
    if robot_ready:
        control |= 1 << 1
    control |= (int(mode) & 0x1F) << 2
    control |= 1 << 7
    if gas:
        control |= 1 << 8
    if feed:
        control |= 1 << 9
    if retract:
        control |= 1 << 10
    if fault_reset:
        control |= 1 << 11
    if touch:
        control |= 1 << 12
    buf = bytearray(8)
    _put_word_le(buf, 0, control)
    _put_word_le(buf, 2, program)
    _put_word_le(buf, 4, job)
    _put_word_le(buf, 6, parameter1)
    return bytes(buf)


def pack_rpdo2(parameter2: int = 0, parameter3: int = 0) -> bytes:
    buf = bytearray(8)
    _put_word_le(buf, 0, parameter2)
    _put_word_le(buf, 2, parameter3)
    return bytes(buf)


def jte_robot_cycle(
    *,
    weld: bool = False,
    robot_ready: bool = True,
    mode: int = 0,
    gas: bool = False,
    feed: bool = False,
    retract: bool = False,
    fault_reset: bool = False,
    touch: bool = False,
    program: int = 0,
    job: int = 0,
    parameter1: int = 0,
    parameter2: int = 0,
    parameter3: int = 0,
) -> list[tuple[int, bytes]]:
    return [
        (RPDO1_ID, pack_rpdo1(
            weld=weld,
            robot_ready=robot_ready,
            mode=mode,
            gas=gas,
            feed=feed,
            retract=retract,
            fault_reset=fault_reset,
            touch=touch,
            program=program,
            job=job,
            parameter1=parameter1,
        )),
        (RPDO2_ID, pack_rpdo2(parameter2, parameter3)),
        (SYNC_ID, b"\x00"),
    ]


def parse_rpdo1(data: bytes) -> dict:
    control = _word_le(data, 0)
    mode = (control >> 2) & 0x1F
    return {
        "weld": bool(control & 0x01),
        "robot_ready": bool(control & 0x02),
        "mode": mode,
        "mode_name": JTE_MODES.get(mode, str(mode)),
        "gas": bool(control & (1 << 8)),
        "feed": bool(control & (1 << 9)),
        "retract": bool(control & (1 << 10)),
        "fault_reset": bool(control & (1 << 11)),
        "touch": bool(control & (1 << 12)),
        "program": _word_le(data, 2),
        "job": _word_le(data, 4),
        "parameter1": _word_le(data, 6),
    }


def describe_jte_robot(rpdo1: bytes, rpdo2: bytes = b"") -> dict:
    cmd = parse_rpdo1(rpdo1)
    chips = list(
        zip(
            JTE_ROBOT_CHIPS,
            (
                cmd["weld"],
                cmd["robot_ready"],
                cmd["gas"],
                cmd["feed"],
                cmd["retract"],
                cmd["fault_reset"],
                cmd["touch"],
            ),
        )
    )
    param2 = _word_le(rpdo2, 0) if rpdo2 else 0
    if param2 >= 0x8000:
        param2 -= 0x10000
    on = " ".join(name for name, flag in chips if flag)
    line = (
        f"{cmd['mode_name']} 程序={cmd['program']} JOB={cmd['job']} "
        f"P1={cmd['parameter1']} P2={param2}"
    )
    if on:
        line = f"{line} {on}"
    return {"chips": chips, "line": line, "raw": _fit(rpdo1, 8), "cmd": cmd}


def pack_tpdo1(
    *,
    ready: bool = True,
    in_weld: bool = False,
    current_a: int = 0,
    voltage_v: float = 0.0,
    alarm: int = 0,
) -> bytes:
    """TPDO1. WeldingTools: bit0 就绪, bit4 在焊, 电流原值, 电压×10, 报警。"""
    status = (0x01 if ready else 0) | (0x10 if in_weld else 0)
    try:
        current = int(round(float(current_a)))
    except (TypeError, ValueError):
        current = 0
    try:
        volts = int(round(float(voltage_v) * 10.0))
    except (TypeError, ValueError):
        volts = 0
    buf = bytearray(8)
    _put_word_le(buf, 0, status)
    _put_word_le(buf, 2, current)
    _put_word_le(buf, 4, volts)
    _put_word_le(buf, 6, alarm)
    return bytes(buf)


def jte_welder_replies(
    *,
    ready: bool = True,
    in_weld: bool = False,
    current_a: int = 0,
    voltage_v: float = 0.0,
    alarm: int = 0,
) -> list[tuple[int, bytes]]:
    return [
        (TPDO1_ID, pack_tpdo1(
            ready=ready,
            in_weld=in_weld,
            current_a=current_a,
            voltage_v=voltage_v,
            alarm=alarm,
        )),
        (TPDO2_ID, bytes(8)),
    ]


def describe_jte_status(data: bytes) -> dict:
    status = _word_le(data, 0)
    ready = bool(status & 0x01)
    in_weld = bool(status & 0x10)
    current = _word_le(data, 2)
    voltage = _word_le(data, 4) / 10.0
    alarm = _word_le(data, 6)
    chips = [("就绪", ready), ("在焊", in_weld)]
    on = " ".join(name for name, flag in chips if flag) or "全关"
    line = f"{on} {current}A {voltage:.1f}V 报警{alarm}"
    return {"chips": chips, "line": line, "raw": _fit(data, 8)}


def note_for_frame(can_id: int, data: bytes) -> str:
    cid = int(can_id)
    if cid == RPDO1_ID:
        return "JTE RPDO1"
    if cid == RPDO2_ID:
        return "JTE RPDO2"
    if cid == SYNC_ID:
        return "JTE SYNC"
    if cid == TPDO1_ID:
        if len(data) >= 8:
            return "JTE TPDO1 " + describe_jte_status(data)["line"]
        return "JTE TPDO1"
    if cid == TPDO2_ID:
        return "JTE TPDO2"
    if cid == POLL_ID:
        who = "主站轮询"
    elif cid == POLL_RSP_ID:
        who = "从站应答"
    elif cid == EXPLICIT_RSP_ID:
        who = "显式应答"
    elif cid in EXPLICIT_REQ_IDS or cid == UCMM_OPEN_ID:
        who = "显式请求"
    elif cid == DUP_MAC_ID:
        who = "重复MAC检测"
    else:
        return ""
    if not data:
        return f"NBM {who}"
    kind = (data[0] >> 6) & 0x3
    index = data[0] & 0x3F
    if cid in (POLL_ID, POLL_RSP_ID):
        tag = ("首段", "中段", "末段", "ACK")[kind]
        return f"NBM {who} {tag}{index}"
    return f"NBM {who}"


def explicit_reply(can_id: int, data: bytes) -> tuple[int, bytes] | None:
    """Slave explicit response for the manual's connection setup.

    The first data byte (fragment / XID / master MAC) is copied back.
    Unknown gets are ignored. Set_Attribute_Single is ACKed; attribute 9
    echoes the written bytes, matching the 250 ms timer example.
    """
    if int(can_id) not in EXPLICIT_REQ_IDS or len(data) < 2:
        return None
    header = data[0]
    body = bytes(data[1:])
    service = body[0]
    if service == 0x4B and len(body) >= 5 and body[1] == 0x03 and body[2] == 0x01:
        return (EXPLICIT_RSP_ID, bytes([header, 0xCB, 0x00]))
    if service == 0x0E and len(body) >= 4:
        attr = _GET_ATTR.get((body[1], body[2], body[3]))
        if attr is None:
            return None
        return (EXPLICIT_RSP_ID, bytes([header, 0x8E]) + attr)
    if service == 0x10 and len(body) >= 4:
        extra = body[4:] if body[3] == 0x09 else b""
        return (EXPLICIT_RSP_ID, bytes([header, 0x90]) + extra)
    return None

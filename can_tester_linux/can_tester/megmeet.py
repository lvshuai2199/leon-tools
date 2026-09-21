from __future__ import annotations

# Product / panel: 插件下发 0x1FD07063，焊机回帧 0x1FD08063（29-bit 扩展帧）
PLUGIN_TX_ID = 0x1FD07063
WELDER_RX_ID = 0x1FD08063

PROFILE_NONE = "通用（仅原始 HEX）"
PROFILE_MEGMEET = "Megmeet 焊机"
PROFILES = (PROFILE_NONE, PROFILE_MEGMEET)

MODE_NAMES = {
    0x00: "直流一元",
    0x20: "脉冲一元",
    0x01: "直流一元(焊)",
    0x21: "脉冲一元(焊)",
}


def decode_frame(can_id: int, data: bytes) -> str:
    if can_id == PLUGIN_TX_ID:
        return _decode_plugin(data)
    if can_id == WELDER_RX_ID:
        return _decode_welder(data)
    return ""


def _decode_plugin(data: bytes) -> str:
    if len(data) < 8:
        return f"插件下发(短帧 {len(data)}B)"
    cmd, job, mode = data[0], data[1], data[2]
    mode_name = MODE_NAMES.get(mode, f"模式0x{mode:02X}")
    return f"插件下发 命令0x{cmd:02X} JOB{job} {mode_name} 尾字节0x{data[7]:02X}"


def _decode_welder(data: bytes) -> str:
    """Match megmeet_responder.build_reply layout (BE current/voltage)."""
    if len(data) < 7:
        return f"焊机回帧(短帧 {len(data)}B)"
    b0, b1 = data[0], data[1]
    amp = (data[2] << 8) | data[3]
    volt_x10 = (data[4] << 8) | data[5]
    err = data[6]
    bits = []
    if b0 & 0x01:
        bits.append("就绪")
    if b0 & 0x02:
        bits.append("故障")
    if b0 & 0x04:
        bits.append("寻位成功")
    if b1 & 0x01:
        bits.append("起弧")
    if b1 & 0x02:
        bits.append("起弧异常")
    if b1 & 0x04:
        bits.append("其它异常")
    if b1 & 0x08:
        bits.append("粘丝")
    flag_txt = ",".join(bits) if bits else f"标志0x{b0:02X}/0x{b1:02X}"
    err_txt = f" 错误码{err}" if err else ""
    return f"焊机回帧 {flag_txt} {amp}A {volt_x10 / 10:.1f}V{err_txt}"



def apply_preset_tx() -> tuple[str, str, bool]:
    return (f"0x{PLUGIN_TX_ID:08X}", "FF 00 20 00 00 00 00 1E", True)

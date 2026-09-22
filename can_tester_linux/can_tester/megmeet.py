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
    from can_tester.goo_combo import note_for_frame

    return note_for_frame(can_id, data)



def apply_preset_tx() -> tuple[str, str, bool]:
    return (f"0x{PLUGIN_TX_ID:08X}", "FF 00 20 00 00 00 00 1E", True)

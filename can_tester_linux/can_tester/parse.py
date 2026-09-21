from __future__ import annotations

STD_ID_MAX = 0x7FF
EXT_ID_MAX = 0x1FFFFFFF
MAX_DLC = 8


def parse_can_id(text: str, extended: bool) -> int:
    value, _ = parse_can_id_auto(text, extended)
    return value


def parse_can_id_auto(text: str, extended: bool | None = None) -> tuple[int, bool]:
    """Parse ID. extended=None => auto (value > 0x7FF => extended). Cap always 0x1FFFFFFF."""
    raw = text.strip().lower()
    if not raw:
        raise ValueError("CAN ID 不能为空")
    if raw.startswith("0x"):
        raw = raw[2:]
    if not raw or any(c not in "0123456789abcdef" for c in raw):
        raise ValueError("CAN ID 必须是十六进制")
    value = int(raw, 16)
    if value < 0 or value > EXT_ID_MAX:
        raise ValueError(f"CAN ID 超出 29 位上限（最大 0x{EXT_ID_MAX:X}）")
    if extended is None:
        return value, value > STD_ID_MAX
    if not extended and value > STD_ID_MAX:
        raise ValueError(
            f"标准帧最大 0x{STD_ID_MAX:X}；0x{value:X} 请改用扩展帧（上限 0x{EXT_ID_MAX:X}）"
        )
    return value, bool(extended)


def parse_can_data(text: str) -> bytes:
    raw = text.strip()
    if not raw:
        return b""
    cleaned = raw.replace("-", " ").replace(",", " ").replace(":", " ")
    tokens = cleaned.split()
    compact = len(tokens) == 1 and not tokens[0].lower().startswith("0x")
    if compact:
        h = tokens[0]
        if h.lower().startswith("0x"):
            h = h[2:]
        if any(c not in "0123456789abcdefABCDEF" for c in h) or len(h) % 2:
            raise ValueError("数据必须是偶数位十六进制")
        parts = [h[i : i + 2] for i in range(0, len(h), 2)]
    else:
        parts = []
        for tok in tokens:
            p = tok.lower()[2:] if tok.lower().startswith("0x") else tok.lower()
            if not p or any(c not in "0123456789abcdef" for c in p) or len(p) > 2:
                raise ValueError("数据必须是十六进制字节")
            parts.append(p)
    if len(parts) > MAX_DLC:
        raise ValueError("标准 CAN 数据最长 8 字节")
    return bytes(int(p, 16) for p in parts)


def format_can_id(can_id: int, extended: bool) -> str:
    return f"0x{can_id:0{8 if extended else 3}X}"


def format_data_hex(data: bytes) -> str:
    return " ".join(f"{b:02X}" for b in data)

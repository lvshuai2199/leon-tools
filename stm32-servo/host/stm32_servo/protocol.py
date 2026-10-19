from stm32_servo.angle import format_cdeg, parse_cdeg_token, parse_int_token


class ProtocolError(ValueError):
    pass


class Reply:
    def __init__(self, kind, text, current=None, target=None, min_us=None, max_us=None):
        self.kind = kind
        self.text = text
        self.current = current
        self.target = target
        self.min_us = min_us
        self.max_us = max_us


def encode_set(cdeg, speed_dps):
    return f"SET {format_cdeg(cdeg)} {int(speed_dps)}"


def encode_cfg(min_us, max_us):
    return f"CFG {int(min_us)} {int(max_us)}"


def parse_reply(line):
    text = line.strip()
    parts = text.split()
    if not parts:
        raise ProtocolError("empty")
    cmd = parts[0].upper()
    if cmd == "PONG" and len(parts) == 1:
        return Reply("pong", text)
    if cmd == "ERR":
        return Reply("err", " ".join(parts[1:]) or "ERR")
    if cmd == "OK" and len(parts) == 1:
        return Reply("ok", text)
    if cmd == "OK" and len(parts) == 2:
        return Reply("ok", text, current=parse_cdeg_token(parts[1]))
    if cmd == "ANG" and len(parts) == 3:
        return Reply(
            "ang",
            text,
            current=parse_cdeg_token(parts[1]),
            target=parse_cdeg_token(parts[2]),
        )
    if cmd == "CFG" and len(parts) == 3:
        return Reply(
            "cfg",
            text,
            min_us=parse_int_token(parts[1]),
            max_us=parse_int_token(parts[2]),
        )
    raise ProtocolError(text)

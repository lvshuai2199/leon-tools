import math
import re
from decimal import Decimal, ROUND_HALF_UP

ANGLE_MAX_CDEG = 18000
DEFAULT_MIN_US = 500
DEFAULT_MAX_US = 2500
TICK_MS = 20

_ANGLE_TOKEN = re.compile(r"\d+(\.\d+)?")
_INT_TOKEN = re.compile(r"\d+")


def format_cdeg(cdeg):
    value = 0 if cdeg < 0 else int(cdeg)
    return f"{value // 100}.{value % 100:02d}"


def cdeg_to_deg(cdeg):
    return (int(cdeg) // 100) + (int(cdeg) % 100) / 100


def parse_cdeg_token(token):
    if not _ANGLE_TOKEN.fullmatch(token):
        raise ValueError("syntax")
    if "." in token:
        whole, frac = token.split(".", 1)
    else:
        whole, frac = token, ""
    digits = (frac + "000")[:3]
    centi = int(digits[:2])
    if int(digits[2]) >= 5:
        centi += 1
    return int(whole) * 100 + centi


def deg_to_cdeg(deg):
    if isinstance(deg, bool) or not isinstance(deg, (int, float)):
        raise ValueError("角度无效")
    if isinstance(deg, float) and (math.isnan(deg) or math.isinf(deg)):
        raise ValueError("角度无效")
    cdeg = int((Decimal(str(deg)) * 100).quantize(Decimal("1"), rounding=ROUND_HALF_UP))
    if cdeg < 0 or cdeg > ANGLE_MAX_CDEG:
        raise ValueError("角度要在 0 到 180 之间")
    return cdeg


def parse_speed(speed):
    if isinstance(speed, bool) or not isinstance(speed, (int, float)):
        raise ValueError("速度无效")
    if isinstance(speed, float) and (math.isnan(speed) or math.isinf(speed)):
        raise ValueError("速度无效")
    value = int((Decimal(str(speed))).quantize(Decimal("1"), rounding=ROUND_HALF_UP))
    if value < 0 or value > 360:
        raise ValueError("速度要在 0 到 360 之间")
    return value


def parse_int_token(token, limit=100000):
    if not _INT_TOKEN.fullmatch(token):
        raise ValueError("syntax")
    value = int(token)
    if value > limit:
        raise ValueError("syntax")
    return value


def validate_pulse_range(min_us, max_us):
    if isinstance(min_us, bool) or isinstance(max_us, bool):
        raise ValueError("脉宽范围无效")
    if not isinstance(min_us, int) or not isinstance(max_us, int):
        raise ValueError("脉宽范围无效")
    if min_us < 100 or max_us > 3000 or max_us <= min_us or (max_us - min_us) < 100:
        raise ValueError("脉宽范围无效")
    return min_us, max_us


def pulse_us(cdeg, min_us=DEFAULT_MIN_US, max_us=DEFAULT_MAX_US):
    span = max_us - min_us
    return min_us + int(cdeg) * span // ANGLE_MAX_CDEG


def step_cdeg(speed_dps):
    if speed_dps <= 0:
        return 0
    step = int(speed_dps) * 2
    return 1 if step < 1 else step

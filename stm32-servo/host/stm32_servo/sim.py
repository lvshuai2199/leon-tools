import threading

from stm32_servo.angle import (
    ANGLE_MAX_CDEG,
    DEFAULT_MAX_US,
    DEFAULT_MIN_US,
    format_cdeg,
    parse_cdeg_token,
    parse_int_token,
    pulse_us,
    step_cdeg,
    validate_pulse_range,
)


class SimBoard:
    """与固件相同的整数角度和 20ms 步进。上电停在 90°。"""

    def __init__(self):
        self.current = 9000
        self.target = 9000
        self.speed_dps = 0
        self.min_us = DEFAULT_MIN_US
        self.max_us = DEFAULT_MAX_US
        self._acc_ms = 0

    @property
    def pulse(self):
        return pulse_us(self.current, self.min_us, self.max_us)

    def tick(self, ms):
        self._acc_ms += int(ms)
        steps = self._acc_ms // 20
        self._acc_ms %= 20
        for _ in range(steps):
            self._step()

    def handle(self, line):
        parts = line.strip().split()
        if not parts:
            return "ERR SYNTAX"
        cmd = parts[0].upper()
        if cmd == "PING" and len(parts) == 1:
            return "PONG"
        if cmd == "GET" and len(parts) == 1:
            return f"ANG {format_cdeg(self.current)} {format_cdeg(self.target)}"
        if cmd == "STOP" and len(parts) == 1:
            self.target = self.current
            self.speed_dps = 0
            return f"OK {format_cdeg(self.current)}"
        if cmd == "CFG?" and len(parts) == 1:
            return f"CFG {self.min_us} {self.max_us}"
        if cmd == "CFG" and len(parts) == 3:
            return self._cfg(parts[1], parts[2])
        if cmd == "SET" and len(parts) in (2, 3):
            return self._set(parts)
        return "ERR SYNTAX"

    def _set(self, parts):
        try:
            cdeg = parse_cdeg_token(parts[1])
            speed = 0 if len(parts) == 2 else parse_int_token(parts[2])
        except ValueError:
            return "ERR SYNTAX"
        if cdeg < 0 or cdeg > ANGLE_MAX_CDEG or speed > 360:
            return "ERR RANGE"
        self.target = cdeg
        self.speed_dps = speed
        if speed == 0:
            self.current = cdeg
        return f"OK {format_cdeg(cdeg)}"

    def _cfg(self, min_token, max_token):
        try:
            min_us = parse_int_token(min_token)
            max_us = parse_int_token(max_token)
            min_us, max_us = validate_pulse_range(min_us, max_us)
        except ValueError as exc:
            if str(exc) == "syntax":
                return "ERR SYNTAX"
            return "ERR RANGE"
        self.min_us = min_us
        self.max_us = max_us
        return "OK"

    def _step(self):
        if self.current == self.target:
            return
        if self.speed_dps <= 0:
            self.current = self.target
            return
        step = step_cdeg(self.speed_dps)
        if self.target > self.current:
            self.current = min(self.target, self.current + step)
        else:
            self.current = max(self.target, self.current - step)


class SimTransport:
    def __init__(self, autotick=True):
        self.board = SimBoard()
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread = None
        if autotick:
            self._thread = threading.Thread(target=self._run, name="servo-sim", daemon=True)
            self._thread.start()

    def _run(self):
        while not self._stop.wait(0.02):
            with self._lock:
                self.board.tick(20)

    def transact(self, line, timeout=0.4):
        del timeout
        with self._lock:
            return self.board.handle(line)

    def close(self):
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=1)
            self._thread = None

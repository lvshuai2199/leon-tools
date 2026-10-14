from stm32_servo.angle import cdeg_to_deg, deg_to_cdeg, parse_speed, validate_pulse_range
from stm32_servo.protocol import encode_cfg, encode_set, parse_reply


class ServoError(Exception):
    pass


class DeviceError(ServoError):
    pass


class ServoController:
    def __init__(self, transport):
        self.transport = transport
        self.angle_cdeg = None
        self.target_cdeg = None
        self.min_us = 500
        self.max_us = 2500

    def close(self):
        self.transport.close()

    def ping(self):
        reply = self._exchange("PING")
        return reply.kind == "pong"

    def read_state(self):
        reply = self._exchange("GET")
        if reply.kind != "ang":
            raise ServoError(reply.text)
        self.angle_cdeg = reply.current
        self.target_cdeg = reply.target
        return self.angle_cdeg, self.target_cdeg

    def read_config(self):
        reply = self._exchange("CFG?")
        if reply.kind != "cfg":
            raise ServoError(reply.text)
        self.min_us = reply.min_us
        self.max_us = reply.max_us
        return self.min_us, self.max_us

    def write_config(self, min_us, max_us):
        min_us, max_us = validate_pulse_range(min_us, max_us)
        reply = self._exchange(encode_cfg(min_us, max_us))
        if reply.kind != "ok":
            raise ServoError(reply.text)
        self.min_us = min_us
        self.max_us = max_us
        return self.min_us, self.max_us

    def move_to(self, deg, speed_dps=0):
        cdeg = deg_to_cdeg(deg)
        speed = parse_speed(speed_dps)
        reply = self._exchange(encode_set(cdeg, speed))
        if reply.kind != "ok" or reply.current is None:
            raise ServoError(reply.text)
        self.target_cdeg = reply.current
        if speed == 0:
            self.angle_cdeg = reply.current
        return cdeg_to_deg(self.target_cdeg)

    def stop(self):
        reply = self._exchange("STOP")
        if reply.kind != "ok" or reply.current is None:
            raise ServoError(reply.text)
        self.angle_cdeg = reply.current
        self.target_cdeg = reply.current
        return cdeg_to_deg(self.angle_cdeg)

    def snapshot(self):
        moving = (
            self.angle_cdeg is not None
            and self.target_cdeg is not None
            and self.angle_cdeg != self.target_cdeg
        )
        return {
            "angle": None if self.angle_cdeg is None else cdeg_to_deg(self.angle_cdeg),
            "target": None if self.target_cdeg is None else cdeg_to_deg(self.target_cdeg),
            "moving": moving,
            "minUs": self.min_us,
            "maxUs": self.max_us,
        }

    def _exchange(self, line):
        try:
            raw = self.transport.transact(line)
        except TimeoutError as exc:
            raise ServoError(str(exc)) from exc
        reply = parse_reply(raw)
        if reply.kind == "err":
            raise DeviceError(reply.text)
        return reply

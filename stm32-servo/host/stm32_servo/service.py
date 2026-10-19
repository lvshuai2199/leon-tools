import threading

from stm32_servo.controller import DeviceError, ServoController, ServoError
from stm32_servo.link import SerialTransport, list_serial_ports
from stm32_servo.sim import SimTransport


class NotConnected(ServoError):
    pass


def _whole_int(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("脉宽范围无效")
    if isinstance(value, float) and not value.is_integer():
        raise ValueError("脉宽范围无效")
    return int(value)


class ServoService:
    def __init__(self):
        self._lock = threading.Lock()
        self._ctrl = None
        self._mode = None
        self._port = None
        self._reply = ""
        self._error = ""

    def list_ports(self):
        return list_serial_ports()

    def connect_sim(self):
        self._connect(SimTransport(autotick=True), "sim", "SIM")

    def connect_serial(self, port, baud=115200):
        if not port or not str(port).strip():
            raise ValueError("缺少串口")
        self._connect(SerialTransport(str(port).strip(), baud), "serial", str(port).strip())

    def disconnect(self):
        with self._lock:
            self._close_locked()

    def state(self):
        with self._lock:
            if self._ctrl is None:
                return self._payload(False)
            try:
                self._ctrl.read_state()
                self._error = ""
            except ServoError as exc:
                self._error = str(exc)
                if not isinstance(exc, DeviceError):
                    self._close_locked()
                    return self._payload(False)
            return self._payload(self._ctrl is not None)

    def move(self, angle, speed):
        with self._lock:
            ctrl = self._require()
            try:
                ctrl.move_to(angle, speed)
                self._reply = "OK"
                self._error = ""
            except ServoError as exc:
                self._error = str(exc)
                raise
            return self._payload(True)

    def stop(self):
        with self._lock:
            ctrl = self._require()
            try:
                ctrl.stop()
                self._reply = "STOP"
                self._error = ""
            except ServoError as exc:
                self._error = str(exc)
                raise
            return self._payload(True)

    def configure(self, min_us, max_us):
        with self._lock:
            ctrl = self._require()
            try:
                ctrl.write_config(_whole_int(min_us), _whole_int(max_us))
                self._reply = "CFG"
                self._error = ""
            except (ServoError, ValueError, TypeError) as exc:
                self._error = str(exc)
                raise
            return self._payload(True)

    def _connect(self, transport, mode, port):
        with self._lock:
            self._close_locked()
            ctrl = ServoController(transport)
            try:
                if not ctrl.ping():
                    raise ServoError("板子没有回应 PING")
                ctrl.read_config()
                ctrl.read_state()
            except Exception:
                ctrl.close()
                raise
            self._ctrl = ctrl
            self._mode = mode
            self._port = port
            self._reply = "PONG"
            self._error = ""

    def _require(self):
        if self._ctrl is None:
            raise NotConnected("还没有连接")
        return self._ctrl

    def _close_locked(self):
        if self._ctrl is not None:
            self._ctrl.close()
        self._ctrl = None
        self._mode = None
        self._port = None

    def _payload(self, connected):
        snap = {
            "angle": None,
            "target": None,
            "moving": False,
            "minUs": 500,
            "maxUs": 2500,
        }
        if connected and self._ctrl is not None:
            snap = self._ctrl.snapshot()
        return {
            "connected": connected,
            "mode": self._mode,
            "port": self._port,
            "reply": self._reply,
            "error": self._error,
            **snap,
        }

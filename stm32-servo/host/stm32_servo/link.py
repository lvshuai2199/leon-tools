from stm32_servo.controller import ServoError


class SerialTransport:
    def __init__(self, port, baud=115200):
        try:
            import serial
        except ImportError as exc:
            raise ServoError("缺少 pyserial，先执行 pip install -r requirements.txt") from exc
        try:
            self.ser = serial.Serial(port, baud, timeout=0.4)
        except Exception as exc:
            raise ServoError(f"打不开串口 {port}：{exc}") from exc
        self.ser.reset_input_buffer()

    def transact(self, line, timeout=0.4):
        try:
            self.ser.timeout = timeout
            self.ser.write((line.strip() + "\n").encode("ascii"))
            raw = self.ser.readline()
        except TimeoutError:
            raise
        except Exception as exc:
            raise ServoError(f"串口通信失败：{exc}") from exc
        if not raw:
            raise TimeoutError("板子没有应答")
        return raw.decode("ascii", errors="replace").strip()

    def close(self):
        self.ser.close()


def list_serial_ports():
    try:
        from serial.tools import list_ports
    except ImportError:
        return []
    return [port.device for port in list_ports.comports()]

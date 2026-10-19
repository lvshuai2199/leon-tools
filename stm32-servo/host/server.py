#!/usr/bin/env python3
"""本机舵机控制页。"""

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from stm32_servo.controller import DeviceError, ServoError
from stm32_servo.service import NotConnected, ServoService

PUBLIC = ROOT / "public"
HOST = "127.0.0.1"
PORT = 8766

MIME = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
}


def build_handler(service):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            path = urlparse(self.path).path
            if path == "/api/state":
                self._send(200, service.state())
                return
            if path == "/api/ports":
                self._send(200, {"ports": service.list_ports()})
                return
            self._file(path)

        def do_POST(self):
            path = urlparse(self.path).path
            try:
                body = self._body()
                if path == "/api/connect":
                    self._connect(body)
                elif path == "/api/disconnect":
                    service.disconnect()
                    self._send(200, service.state())
                elif path == "/api/move":
                    self._send(200, service.move(body.get("angle"), body.get("speed", 0)))
                elif path == "/api/stop":
                    self._send(200, service.stop())
                elif path == "/api/config":
                    self._send(200, service.configure(body.get("minUs"), body.get("maxUs")))
                else:
                    self._send(404, {"error": "没有这个接口"})
            except NotConnected as exc:
                self._send(409, {"error": str(exc)})
            except (ValueError, DeviceError, TypeError) as exc:
                self._send(400, {"error": str(exc)})
            except ServoError as exc:
                self._send(502, {"error": str(exc)})
            except json.JSONDecodeError:
                self._send(400, {"error": "请求不是 JSON"})

        def _connect(self, body):
            mode = body.get("mode")
            if mode == "sim":
                service.connect_sim()
            elif mode == "serial":
                service.connect_serial(body.get("port", ""), int(body.get("baud", 115200)))
            else:
                raise ValueError("mode 只能是 sim 或 serial")
            self._send(200, service.state())

        def _body(self):
            length = int(self.headers.get("Content-Length", "0") or 0)
            if length > 4096:
                raise ValueError("请求太大")
            raw = self.rfile.read(length) if length else b""
            if not raw:
                return {}
            data = json.loads(raw.decode("utf-8"))
            if not isinstance(data, dict):
                raise ValueError("请求不是对象")
            return data

        def _file(self, path):
            rel = "index.html" if path == "/" else path.lstrip("/")
            if ".." in rel.split("/"):
                self._send(404, {"error": "找不到页面"})
                return
            file = (PUBLIC / rel).resolve()
            if not file.is_relative_to(PUBLIC.resolve()) or not file.is_file():
                self._send(404, {"error": "找不到页面"})
                return
            data = file.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", MIME.get(file.suffix, "application/octet-stream"))
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def _send(self, status, payload):
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, fmt, *args):
            if args and "/api/state" in str(args[0]):
                return
            super().log_message(fmt, *args)

    return Handler


def serve(port=PORT):
    service = ServoService()
    httpd = ThreadingHTTPServer((HOST, port), build_handler(service))
    print(f"舵机控制：http://{HOST}:{httpd.server_address[1]}", flush=True)
    return httpd


if __name__ == "__main__":
    server = serve()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

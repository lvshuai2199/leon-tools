import json
import sys
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from server import build_handler
from stm32_servo.angle import deg_to_cdeg, format_cdeg, parse_cdeg_token, pulse_us, step_cdeg
from stm32_servo.controller import DeviceError, ServoController
from stm32_servo.service import ServoService
from stm32_servo.sim import SimBoard
from http.server import ThreadingHTTPServer


class AngleTests(unittest.TestCase):
    def test_token_matches_centi_degree_rounding(self):
        self.assertEqual(parse_cdeg_token("90"), 9000)
        self.assertEqual(parse_cdeg_token("90.2"), 9020)
        self.assertEqual(parse_cdeg_token("90.25"), 9025)
        self.assertEqual(parse_cdeg_token("90.256"), 9026)
        self.assertEqual(parse_cdeg_token("180.004"), 18000)
        self.assertEqual(parse_cdeg_token("180.009"), 18001)
        self.assertEqual(format_cdeg(9026), "90.26")

    def test_deg_limits_and_pulse_ends(self):
        self.assertEqual(deg_to_cdeg(0), 0)
        self.assertEqual(deg_to_cdeg(180), 18000)
        self.assertEqual(pulse_us(0), 500)
        self.assertEqual(pulse_us(9000), 1500)
        self.assertEqual(pulse_us(18000), 2500)
        self.assertEqual(pulse_us(0, 1000, 2000), 1000)
        self.assertEqual(pulse_us(18000, 1000, 2000), 2000)
        self.assertEqual(step_cdeg(100), 200)
        with self.assertRaises(ValueError):
            deg_to_cdeg(180.1)
        with self.assertRaises(ValueError):
            deg_to_cdeg(-0.1)


class BoardTests(unittest.TestCase):
    def test_motion_stop_and_calibration(self):
        board = SimBoard()
        self.assertEqual(board.handle("PING"), "PONG")
        self.assertEqual(board.handle("SET 0 0"), "OK 0.00")
        self.assertEqual(board.pulse, 500)
        self.assertEqual(board.handle("SET 90 100"), "OK 90.00")
        board.tick(500)
        self.assertEqual(board.current, 5000)
        board.tick(100)
        self.assertEqual(board.handle("STOP"), "OK 60.00")
        board.tick(500)
        self.assertEqual(board.current, 6000)
        self.assertEqual(board.handle("CFG 1000 2000"), "OK")
        self.assertEqual(board.pulse, pulse_us(6000, 1000, 2000))
        self.assertEqual(board.handle("CFG 2000 1000"), "ERR RANGE")
        self.assertEqual(board.min_us, 1000)
        self.assertEqual(board.handle("SET 181"), "ERR RANGE")
        self.assertEqual(board.handle("SET 90."), "ERR SYNTAX")
        self.assertEqual(board.handle("NOPE"), "ERR SYNTAX")

    def test_controller_rejects_bad_angle_before_send(self):
        board = SimBoard()

        class Direct:
            def transact(self, line, timeout=0.4):
                del timeout
                return board.handle(line)

            def close(self):
                pass

        ctrl = ServoController(Direct())
        self.assertTrue(ctrl.ping())
        ctrl.move_to(0, 0)
        with self.assertRaises(ValueError):
            ctrl.move_to(200, 10)
        with self.assertRaises(DeviceError):
            board.handle("SET 10 0")
            ctrl.move_to(10, 0)
            ctrl.transport.transact = lambda line, timeout=0.4: "ERR RANGE"
            ctrl.move_to(20, 0)


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.service = ServoService()
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), build_handler(self.service))
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.httpd.server_address[1]}"

    def tearDown(self):
        self.service.disconnect()
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join(timeout=2)

    def request(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(self.base + path, data=data, method=method)
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req) as response:
                return response.status, json.loads(response.read().decode())
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read().decode())

    def test_page(self):
        with urllib.request.urlopen(self.base + "/") as response:
            html = response.read().decode("utf-8")
            self.assertEqual(response.status, 200)
        self.assertIn("舵机控制", html)
        self.assertIn("仿真", html)

    def test_sim_commands(self):
        status, state = self.request("POST", "/api/connect", {"mode": "sim"})
        self.assertEqual(status, 200)
        self.assertTrue(state["connected"])
        self.assertEqual(state["angle"], 90)
        status, state = self.request("POST", "/api/move", {"angle": 30, "speed": 0})
        self.assertEqual(status, 200)
        self.assertEqual(state["angle"], 30)
        self.assertFalse(state["moving"])
        status, state = self.request("POST", "/api/move", {"angle": 100, "speed": 10})
        self.assertEqual(status, 200)
        self.assertEqual(state["target"], 100)
        self.assertTrue(state["moving"])
        status, state = self.request("POST", "/api/stop", {})
        self.assertEqual(status, 200)
        self.assertEqual(state["angle"], state["target"])
        self.assertFalse(state["moving"])
        status, state = self.request("POST", "/api/move", {"angle": 190, "speed": 10})
        self.assertEqual(status, 400)
        status, state = self.request("POST", "/api/disconnect", {})
        self.assertEqual(status, 200)
        self.assertFalse(state["connected"])
        status, _ = self.request("POST", "/api/stop", {})
        self.assertEqual(status, 409)


if __name__ == "__main__":
    unittest.main()

# -*- coding: utf-8 -*-
import errno
import unittest

from can_tester.bus import (
    can_down_hint,
    format_bus_error,
    is_iface_down_error,
)


class TestBusDownHint(unittest.TestCase):
    def test_network_is_down_phrase(self):
        exc = OSError(errno.ENETDOWN, "Network is down")
        self.assertTrue(is_iface_down_error(exc))
        msg = format_bus_error("can0", exc, prefix="接收错误")
        self.assertIn("CAN 口未启动", msg)
        self.assertIn("can0", msg)
        self.assertIn("ip link set can0 up", msg)
        self.assertNotIn("网络失败", msg)
        self.assertNotIn("Network is down", msg)

    def test_error_code_100(self):
        exc = Exception("Error receiving: Network is down [Error Code 100]")
        self.assertTrue(is_iface_down_error(exc))
        msg = format_bus_error("can0", exc)
        self.assertIn("CAN 口未启动", msg)

    def test_hint_text(self):
        h = can_down_hint("can0", "125000")
        self.assertIn("bitrate 125000", h)


if __name__ == "__main__":
    unittest.main()

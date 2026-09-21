"""Unit tests for occupancy detection / hints (no real CAN hardware)."""
from __future__ import annotations

import errno

from can_tester.bus import (
    OCCUPANCY_HINT,
    CanSession,
    is_occupancy_error,
)


def test_occupancy_hint_constant():
    assert "FullFunctionWelding" in OCCUPANCY_HINT
    assert "占用" in OCCUPANCY_HINT


def test_is_occupancy_error_errno():
    assert is_occupancy_error(OSError(errno.EBUSY, "Device or resource busy"))
    assert is_occupancy_error(OSError(errno.EACCES, "Permission denied"))
    assert is_occupancy_error(OSError(errno.EPERM, "Operation not permitted"))
    assert not is_occupancy_error(OSError(errno.ENODEV, "No such device"))
    assert not is_occupancy_error(ValueError("bad id"))


def test_is_occupancy_error_text():
    assert is_occupancy_error(RuntimeError("resource busy"))
    assert is_occupancy_error(RuntimeError("Permission denied opening can0"))


def test_hint_prefers_occupancy():
    msg = CanSession._hint("can0", OSError(errno.EBUSY, "Device or resource busy"))
    assert msg == OCCUPANCY_HINT


def test_hint_virtual_not_occupancy_by_default():
    msg = CanSession._hint("virtual", RuntimeError("boom"))
    assert "软件回环" in msg


def test_listen_only_shared_blocks_tx_on_socketcan(monkeypatch=None):
    """When flag set and backend socketcan, send() refuses TX."""
    s = CanSession()
    s._bus = object()  # pretend connected
    s.backend = "socketcan"
    s.listen_only_shared = True
    try:
        s.send(0x123, b"\x01", False)
        assert False, "expected RuntimeError"
    except RuntimeError as exc:
        assert "只监听不独占" in str(exc)


def test_listen_only_shared_allows_virtual_tx_path():
    """Virtual backend is not gated by listen_only_shared."""
    s = CanSession()
    s.listen_only_shared = True
    s.backend = "virtual"
    # no bus -> still "未连接"
    try:
        s.send(0x123, b"\x01", False)
        assert False
    except RuntimeError as exc:
        assert "未连接" in str(exc)


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
    print("all passed")

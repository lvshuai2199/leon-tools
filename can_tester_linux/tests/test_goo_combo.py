"""Megmeet - GOO pack/decode matches the manual and megmeet_can_responder.py."""
from can_tester.goo_combo import (
    ROBOT_ID,
    WELDER_ID,
    describe_robot,
    describe_welder,
    pack_robot_poll,
)
from can_tester.megmeet_responder import ResponderState, build_reply


def test_ids():
    assert ROBOT_ID == 0x1FD07063
    assert WELDER_ID == 0x1FD08063


def test_robot_poll_bits():
    data = pack_robot_poll(
        robot_err=False,
        seek=True,
        weld=True,
        gas=True,
        feed=False,
        retract=False,
        mode=1,
        job=3,
        current=180,
        voltage=22.5,
    )
    assert data[0] == 0xFF
    assert data[1] == 0x02  # seek, robot fault clear
    assert data[2] & 0x01 and data[2] & 0x02  # weld + gas
    assert (data[2] >> 5) & 0x07 == 1  # 脉冲一元
    assert data[3] == 3
    assert (data[4] << 8 | data[5]) == 180
    assert (data[6] << 8 | data[7]) == 225
    view = describe_robot(data)
    assert view["kind"] == "poll"
    assert ("起焊", True) in view["chips"]
    assert "180A" in view["line"]


def test_welder_reply_manual_bytes():
    data = build_reply(
        ResponderState(ready=True, arc_ok=True, current=180, voltage=22.5, range_over=True)
    )
    assert data[0] == 0
    assert data[1] & 0x01
    assert data[2] & 0x01 and data[2] & 0x80
    view = describe_welder(data)
    assert ("就绪", True) in view["chips"]
    assert ("给定超范围", True) in view["chips"]
    assert "180A" in view["line"] and "22.5V" in view["line"]


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
    print("all passed")

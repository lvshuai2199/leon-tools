"""Unit tests for Megmeet welder reply packing and auto-arc edges."""
from can_tester.megmeet_responder import (
    CMD_POLL,
    ROBOT_ID,
    WELDER_ID,
    ResponderState,
    build_reply,
    on_robot_frame,
)


def test_ids():
    assert ROBOT_ID == 0x1FD07063
    assert WELDER_ID == 0x1FD08063


def test_build_reply_bit_packing():
    st = ResponderState(
        ready=True,
        error=False,
        touch_ok=True,
        arc_ok=True,
        arc_ex=False,
        other_ex=False,
        stick=False,
        current=180,
        voltage=22.5,
        errcode=0,
    )
    data = build_reply(st)
    assert len(data) == 8
    assert data[0] & 0x01  # ready
    assert data[0] & 0x04  # touch_ok
    assert data[1] & 0x01  # arc_ok
    assert data[2] == 0x00 and data[3] == 0xB4  # 180 BE
    assert data[4] == 0x00 and data[5] == 0xE1  # 225 BE (22.5*10)
    assert data[6] == 0
    assert data[7] == 0


def test_build_reply_defaults():
    data = build_reply(ResponderState())
    assert data[0] & 0x01
    assert data[3] == 180
    assert (data[4] << 8 | data[5]) == 225


def test_auto_arc_rising_falling_edge():
    st = ResponderState(auto_arc=True, auto_reply=True, arc_ok=False)
    # poll without weld
    on_robot_frame(st, bytes([CMD_POLL, 0, 0x00, 0, 0, 0, 0, 0]))
    assert st.arc_ok is False
    # rising edge weld bit0
    on_robot_frame(st, bytes([CMD_POLL, 0, 0x01, 0, 0, 0, 0, 0]))
    assert st.arc_ok is True
    # hold
    on_robot_frame(st, bytes([CMD_POLL, 0, 0x01, 0, 0, 0, 0, 0]))
    assert st.arc_ok is True
    # falling
    on_robot_frame(st, bytes([CMD_POLL, 0, 0x00, 0, 0, 0, 0, 0]))
    assert st.arc_ok is False


def test_seek_sets_touch_ok():
    # py: seek = data[1] bit1; level true → latch touch_ok (no auto-clear)
    st = ResponderState(auto_arc=True, touch_ok=False)
    on_robot_frame(st, bytes([CMD_POLL, 0x02, 0, 0, 0, 0, 0, 0]))  # seek data[1].bit1
    assert st.touch_ok is True
    on_robot_frame(st, bytes([CMD_POLL, 0x00, 0, 0, 0, 0, 0, 0]))
    assert st.touch_ok is True  # latch — match py
    # seek still works when auto_arc off
    st2 = ResponderState(auto_arc=False, touch_ok=False)
    on_robot_frame(st2, bytes([CMD_POLL, 0x02, 0, 0, 0, 0, 0, 0]))
    assert st2.touch_ok is True
    assert st2.arc_ok is False


def test_auto_reply_off_returns_none():
    st = ResponderState(auto_reply=False)
    assert on_robot_frame(st, bytes([CMD_POLL, 0, 0, 0, 0, 0, 0, 0])) is None


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
    print("all passed")

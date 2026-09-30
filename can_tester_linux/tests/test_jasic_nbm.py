"""NBM-500R DeviceNet V100 signal pack, fragment, and explicit reply."""
from can_tester.jasic_nbm import (
    DUP_MAC_ID,
    EXPLICIT_RSP_ID,
    POLL_ID,
    POLL_RSP_ID,
    SYNC_ID,
    TPDO1_ID,
    IoReassembler,
    MASTER_SETUP_FRAMES,
    current_a_to_raw,
    describe_input,
    describe_jte_robot,
    describe_jte_status,
    describe_output,
    explicit_reply,
    fragment_io,
    jte_welder_replies,
    pack_input,
    pack_output,
    pack_rpdo1,
    parse_input,
    trim_to_raw,
    voltage_out_to_raw,
)


def test_ids_match_manual():
    assert POLL_ID == 0x0415
    assert POLL_RSP_ID == 0x03C2
    assert DUP_MAC_ID == 0x040F
    assert EXPLICIT_RSP_ID == 0x0413


def test_manual_poll_and_response_frames():
    payload = pack_input(robot_ready=True, arc_raw=0x7FFF, job=0)
    frames = fragment_io(payload)
    assert frames == [
        bytes.fromhex("00020000000000FF"),
        bytes.fromhex("817F00000000"),
    ]
    assert [len(fr) for fr in frames] == [8, 6]
    back = IoReassembler()
    assert back.feed(frames[0]) is None
    assert back.feed(frames[1]) == payload

    out = pack_output()
    out_frames = fragment_io(out)
    assert out_frames == [
        bytes.fromhex("0040000008000000"),
        bytes.fromhex("81000000000000"),
    ]
    assert [len(fr) for fr in out_frames] == [8, 7]
    asm = IoReassembler()
    assert asm.feed(out_frames[0]) is None
    assert asm.feed(out_frames[1]) == out


def test_mode_bits_and_little_endian_given():
    raw = current_a_to_raw(550)
    assert raw == 65535
    data = pack_input(robot_ready=True, mode=1, given_raw=0x1234, job=20)
    assert data[0] == 0x06  # bit1 ready, bit2 mode=1
    assert data[4] == 0x34 and data[5] == 0x12
    assert data[8] == 20
    cmd = parse_input(data)
    assert cmd.mode == 1 and cmd.given_raw == 0x1234 and cmd.robot_ready


def test_scales_and_trim_zero():
    assert trim_to_raw(0) in (32767, 32768)
    assert trim_to_raw(-30) == 0
    assert trim_to_raw(30) == 65535
    assert current_a_to_raw(0) == 0
    assert voltage_out_to_raw(100) == 65535
    view = describe_input(pack_input(weld=True, mode=4, given_raw=0, arc_raw=trim_to_raw(0)))
    assert ("开始焊接", True) in view["chips"]
    assert "分别模式" in view["line"]
    assert "修正0.0%" in view["line"]


def test_output_bits_and_alarm():
    data = pack_output(
        arc_ok=True,
        welding=True,
        fault=True,
        comm_ready=True,
        touch_ok=True,
        feeder_ok=True,
        range_over=True,
        alarm=0x15,
        current_raw=0,
        voltage_raw=0,
    )
    assert data[0] == 0x01 | 0x04 | 0x20 | 0x40
    assert data[1] == 0x15
    assert data[3] == 0x01 | 0x08 | 0x80
    view = describe_output(data)
    assert ("起弧成功", True) in view["chips"]
    assert ("给定超范围", True) in view["chips"]
    assert "报警21" in view["line"]


def test_explicit_reply_copies_xid_and_manual_values():
    assert explicit_reply(0x040F, bytes.fromhex("00080006F58912")) is None
    rsp = explicit_reply(0x0416, bytes.fromhex("414B03010101"))
    assert rsp == (0x0413, bytes.fromhex("41CB00"))
    assert explicit_reply(0x0414, bytes.fromhex("010E010101")) == (
        0x0413,
        bytes.fromhex("018E6C00"),
    )
    assert explicit_reply(0x0414, bytes.fromhex("410E010103")) == (
        0x0413,
        bytes.fromhex("418E8214"),
    )
    assert explicit_reply(0x0414, bytes.fromhex("010E050207")) == (
        0x0413,
        bytes.fromhex("018E0D00"),
    )
    assert explicit_reply(0x0414, bytes.fromhex("411005010C03")) == (
        0x0413,
        bytes.fromhex("4190"),
    )
    assert explicit_reply(0x0414, bytes.fromhex("01100502091027")) == (
        0x0413,
        bytes.fromhex("01901027"),
    )
    assert explicit_reply(0x0414, bytes.fromhex("010E010199")) is None


def test_setup_is_master_only():
    ids = [step.can_id for step in MASTER_SETUP_FRAMES]
    assert ids.count(0x040F) == 2
    assert POLL_ID not in ids
    assert POLL_RSP_ID not in ids
    assert any(step.wait_rsp and step.can_id == 0x0416 for step in MASTER_SETUP_FRAMES)


def test_jte_tpdo1_is_what_weldingtools_reads():
    frames = jte_welder_replies(ready=True, in_weld=False, current_a=180, voltage_v=22.5, alarm=0)
    assert [can_id for can_id, _ in frames] == [0x182, 0x282]
    data = frames[0][1]
    assert len(data) == 8
    assert data[0] & 0x01 and not (data[0] & 0x10)
    assert int.from_bytes(data[2:4], "little") == 180
    assert int.from_bytes(data[4:6], "little") == 225
    view = describe_jte_status(data)
    assert ("就绪", True) in view["chips"]
    assert ("在焊", False) in view["chips"]


def test_jte_rpdo1_matches_weldingtools_bits():
    data = pack_rpdo1(robot_ready=True, weld=True, mode=1, gas=True, job=3, parameter1=180)
    assert data[0] == 0x82 | 0x01 | (1 << 2)
    assert data[1] & 0x01  # gas is bit 8
    view = describe_jte_robot(data, b"")
    assert ("起弧", True) in view["chips"]
    assert "脉冲一元化" in view["line"]
    assert SYNC_ID == 0x080 and TPDO1_ID == 0x182


def test_reassembler_restarts_on_new_first():
    asm = IoReassembler()
    assert asm.feed(bytes.fromhex("000102")) is None
    assert asm.feed(bytes.fromhex("00AABB")) is None
    assert asm.feed(bytes.fromhex("81CC")) == bytes.fromhex("AABBCC")

from can_tester.parse import parse_can_id_auto, parse_can_id, EXT_ID_MAX
from can_tester import megmeet


def test_auto_ext_megmeet():
    v, e = parse_can_id_auto("0x1FD07063", None)
    assert v == 0x1FD07063 and e is True


def test_std():
    v, e = parse_can_id_auto("0x123", None)
    assert v == 0x123 and e is False


def test_reject_over_29bit():
    try:
        parse_can_id_auto("0x3FFFFFFF", None)
        assert False, "should raise"
    except ValueError:
        pass


def test_megmeet_ids():
    assert megmeet.PLUGIN_TX_ID == 0x1FD07063
    assert megmeet.WELDER_RX_ID == 0x1FD08063
    s = megmeet.decode_frame(0x1FD08063, bytes([0x01, 0, 0x00, 0xB4, 0x00, 0xE1, 0, 0]))
    assert "180A" in s and "22.5V" in s


def test_ext_limit():
    assert parse_can_id("0x1FFFFFFF", True) == EXT_ID_MAX

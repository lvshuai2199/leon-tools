"""SignalDef parse / pack round-trip (v1.7.26)."""
from can_tester.megmeet_responder import build_reply, state_from_mapping
from can_tester.responder_module import (
    SignalDef,
    apply_signals_to_buffer,
    load_responder_module,
    package_responder_dir,
    signal_defaults_dict,
    values_from_signals_for_state,
)


def test_megmeet_profile_signals_pack_matches_engine():
    path = package_responder_dir() / "megmeet_welder.json"
    mod = load_responder_module(path)
    assert len(mod.signals) >= 7
    names = [s.name for s in mod.signals]
    assert "就绪" in names and "故障" in names
    assert any(n in ("A", "电流 A") for n in names)
    assert any(n in ("V", "电压 V") for n in names)
    vals = signal_defaults_dict(mod.signals)
    packed = apply_signals_to_buffer(b"", mod.signals, vals)
    ref = build_reply(state_from_mapping(mod.state))
    assert packed == ref


def test_signal_value_override_and_state_keys():
    sigs = [
        SignalDef(name="就绪", kind="bit", byte=0, bit=0, default=True, key="ready"),
        SignalDef(name="A", kind="u16", byte=2, scale=1.0, default=100, key="current"),
        SignalDef(name="V", kind="u16", byte=4, scale=0.1, default=20.0, key="voltage"),
        SignalDef(name="错", kind="u8", byte=6, scale=1.0, default=0, key="errcode"),
    ]
    vals = {"就绪": False, "A": 200, "V": 25.5, "错": 3}
    buf = apply_signals_to_buffer(bytearray(8), sigs, vals)
    assert buf[0] & 0x01 == 0
    assert buf[2] == 0 and buf[3] == 200
    assert (buf[4] << 8 | buf[5]) == 255
    assert buf[6] == 3
    mapped = values_from_signals_for_state(sigs, vals)
    assert mapped["ready"] is False
    assert mapped["current"] == 200
    assert mapped["voltage"] == 25.5
    assert mapped["errcode"] == 3


def test_rule_summary():
    assert SignalDef(kind="bit", byte=0, bit=2).rule_summary() == "b0.2"
    assert SignalDef(kind="u16", byte=4, scale=0.1).rule_summary() == "u16@4×0.1"
    assert SignalDef(kind="u8", byte=6).rule_summary() == "u8@6"


def test_rx_changes_per_id_logic():
    """Mirrors gui._poll change-detection: same id+ext + same data → suppress."""
    last: dict[tuple[int, bool], bytes] = {}

    def should_print(can_id, ext, data, enabled=True):
        id_key = (int(can_id), bool(ext))
        data_b = bytes(data)
        if enabled:
            prev = last.get(id_key)
            if prev is not None and prev == data_b:
                return False
        last[id_key] = data_b
        return True

    assert should_print(1, True, b"\x01") is True
    assert should_print(1, True, b"\x01") is False  # same id+data
    assert should_print(2, True, b"\x01") is True   # other id
    assert should_print(1, True, b"\x01") is False  # still suppressed per-id
    assert should_print(1, True, b"\x02") is True   # data changed
    assert should_print(1, True, b"\x02", enabled=False) is True  # flag off always print

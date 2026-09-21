import json
import tempfile
from pathlib import Path

from can_tester.config import AppSettings, load_settings, parse_rx_id_whitelist, save_settings


def test_defaults():
    s = AppSettings().clamp()
    assert s.print_client_data is False
    assert s.reply_count == 1
    assert s.reply_timeout_ms >= 50
    assert s.enable_init_on_connect is False
    assert s.init_module == ""
    assert s.default_interval_ms == 50
    assert s.rx_id_filter == ""
    assert s.rx_changes_only is True
    assert s.listen_only_shared is False
    assert s.enable_responder is False
    assert s.responder_module == ""
    assert s.show_self_echo is False


def test_clamp_reply_count():
    assert AppSettings(reply_count=0).clamp().reply_count == 1
    assert AppSettings(reply_count=99).clamp().reply_count == 10


def test_roundtrip():
    with tempfile.TemporaryDirectory() as d:
        p = Path(d) / "config.json"
        s = AppSettings(
            print_client_data=True,
            reply_count=3,
            reply_timeout_ms=750,
            enable_init_on_connect=True,
            init_module="goo_init",
            default_interval_ms=40,
            rx_id_filter="1FD07063,1FD08063",
            rx_changes_only=True,
            listen_only_shared=True,
            enable_responder=False,
            responder_module="",  # empty → enable off; listen-only can stay
            show_self_echo=True,
        )
        save_settings(s, p)
        again = load_settings(p)
        assert again.print_client_data is True
        assert again.reply_count == 3
        assert again.reply_timeout_ms == 750
        assert again.enable_init_on_connect is True
        assert again.init_module == "goo_init"
        assert again.default_interval_ms == 40
        assert again.rx_id_filter == "1FD07063,1FD08063"
        assert again.rx_changes_only is True
        assert again.listen_only_shared is True
        assert again.enable_responder is False
        assert again.responder_module == ""
        assert again.show_self_echo is True
        # Non-empty responder_module forces enable on and clears listen-only.
        s2 = AppSettings(
            listen_only_shared=True,
            responder_module="megmeet_welder",
        ).clamp()
        assert s2.enable_responder is True
        assert s2.listen_only_shared is False
        raw = json.loads(p.read_text(encoding="utf-8"))
        assert "enable_init_on_connect" in raw
        assert "init_module" in raw
        assert "default_interval_ms" in raw
        assert "rx_id_filter" in raw
        assert "rx_changes_only" in raw
        assert "listen_only_shared" in raw


def test_is_new_or_changed_payload():
    from can_tester.config import frame_log_key, is_new_or_changed_payload

    last: dict = {}
    k_rx = frame_log_key("RX", 0x123, False)
    k_tx = frame_log_key("TX", 0x123, False)
    assert is_new_or_changed_payload(last, k_rx, b"\x01") is True
    assert is_new_or_changed_payload(last, k_rx, b"\x01") is False
    assert is_new_or_changed_payload(last, k_rx, b"\x02") is True
    assert is_new_or_changed_payload(last, k_rx, b"\x02") is False
    # Same id, different direction is a separate row.
    assert is_new_or_changed_payload(last, k_tx, b"\x02") is True
    assert is_new_or_changed_payload(last, k_tx, b"\x02") is False
    k_ext = frame_log_key("RX", 0x123, True)
    assert is_new_or_changed_payload(last, k_ext, b"\x02") is True


def test_parse_rx_id_whitelist():
    assert parse_rx_id_whitelist("") == frozenset()
    assert parse_rx_id_whitelist("  ") == frozenset()
    ids = parse_rx_id_whitelist("1FD07063,1FD08063")
    assert ids == frozenset({0x1FD07063, 0x1FD08063})
    ids2 = parse_rx_id_whitelist("0x1FD07063 1fd08063;ABC")
    assert 0x1FD07063 in ids2 and 0x1FD08063 in ids2 and 0xABC in ids2


def test_load_settings_defaults_changes_only_true():
    with tempfile.TemporaryDirectory() as d:
        p = Path(d) / "config.json"
        p.write_text("{}\n", encoding="utf-8")
        s = load_settings(p)
        assert s.rx_changes_only is True


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
    print("all passed")

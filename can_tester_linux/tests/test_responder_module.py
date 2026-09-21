"""应答档 load/match smoke tests."""
from can_tester.megmeet_responder import ROBOT_ID, build_reply, ResponderState
from can_tester.responder_module import (
    ResponderModule,
    ResponderRow,
    list_responder_modules,
    matching_rows,
    package_responder_dir,
)


def _inline_megmeet_mod() -> ResponderModule:
    """Package profiles/ may be empty (Megmeet sample removed); build inline."""
    return ResponderModule(
        name="Megmeet 焊机应答",
        engine="megmeet",
        rows=[
            ResponderRow(
                enabled=True,
                match_id=ROBOT_ID,
                extended=True,
                reply=build_reply(ResponderState()),
                note="poll",
            )
        ],
    )


def test_shipped_or_inline_megmeet_profile():
    mods = list_responder_modules()
    if mods:
        mod = mods[0]
    else:
        # Expected when profiles/ has no factory JSON
        assert list(package_responder_dir().glob("*.json")) == []
        mod = _inline_megmeet_mod()
    assert mod.is_megmeet_engine()
    rows = matching_rows(mod, ROBOT_ID)
    assert rows and rows[0].enabled
    assert rows[0].reply == build_reply(ResponderState())


def test_config_mutex():
    from can_tester.config import AppSettings

    s = AppSettings(
        enable_responder=True,
        responder_module="Megmeet 焊机应答",
        listen_only_shared=True,
    ).clamp()
    assert s.enable_responder is True
    assert s.listen_only_shared is False
    # Empty profile → enable off
    s2 = AppSettings(enable_responder=True, responder_module="").clamp()
    assert s2.enable_responder is False



def test_save_signals_reload_roundtrip(tmp_path):
    """Edit signals → save JSON → reload preserves name/kind/default."""
    from can_tester.responder_module import SignalDef, load_responder_module, save_responder_module

    mod = ResponderModule(
        name="ut_signals",
        engine="static",
        rows=[
            ResponderRow(
                enabled=True,
                match_id=0x100,
                extended=False,
                reply=bytes([0] * 8),
                note="r",
            )
        ],
        signals=[
            SignalDef(name="就绪", note="ready", kind="bit", byte=0, bit=0, default=True, key="ready"),
            SignalDef(name="A", note="current", kind="u16", byte=2, scale=1.0, default=120.0, key="current"),
            SignalDef(name="错", note="err", kind="u8", byte=6, default=0, key="errcode"),
        ],
    )
    dest = tmp_path / "ut_signals.json"
    save_responder_module(mod, dest)
    assert mod.path == dest
    loaded = load_responder_module(dest)
    assert loaded.name == "ut_signals"
    assert len(loaded.signals) == 3
    assert loaded.signals[0].name == "就绪"
    assert loaded.signals[0].kind == "bit"
    assert loaded.signals[0].default is True
    assert loaded.signals[1].name == "A"
    assert loaded.signals[1].kind == "u16"
    assert float(loaded.signals[1].default) == 120.0
    assert loaded.signals[2].kind == "u8"


def test_list_prefers_user_over_package(tmp_path, monkeypatch):
    """When user copy exists, list_responder_modules prefers USER path."""
    import can_tester.responder_module as rm
    from can_tester.responder_module import SignalDef, load_responder_module, save_responder_module

    pkg = tmp_path / "profiles"
    user = tmp_path / "user_responder"
    pkg.mkdir()
    user.mkdir()
    factory = ResponderModule(
        name="Demo",
        engine="static",
        rows=[ResponderRow(enabled=True, match_id=1, reply=b"\x00")],
        signals=[SignalDef(name="factory_sig", kind="bit", byte=0, bit=1, default=False)],
    )
    save_responder_module(factory, pkg / "demo.json")
    user_mod = ResponderModule(
        name="Demo",
        engine="static",
        rows=[ResponderRow(enabled=True, match_id=1, reply=b"\x00")],
        signals=[SignalDef(name="user_sig", kind="bit", byte=0, bit=2, default=True)],
    )
    save_responder_module(user_mod, user / "demo.json")

    monkeypatch.setattr(rm, "package_responder_dir", lambda: pkg)
    monkeypatch.setattr(rm, "USER_RESPONDER_DIR", user)
    mods = rm.list_responder_modules()
    stems = {m.path.stem: m for m in mods if m.path}
    assert "demo" in stems
    assert stems["demo"].path == user / "demo.json"
    assert stems["demo"].signals[0].name == "user_sig"

if __name__ == "__main__":
    test_shipped_or_inline_megmeet_profile()
    test_config_mutex()
    import tempfile
    from pathlib import Path as _P
    with tempfile.TemporaryDirectory() as d:
        test_save_signals_reload_roundtrip(_P(d))
    print("all passed")

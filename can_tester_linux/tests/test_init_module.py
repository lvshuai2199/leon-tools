from pathlib import Path
import json
import tempfile

from can_tester.init_module import (
    InitModule,
    InitRow,
    InitModuleError,
    load_module,
    list_modules,
    run_module,
    save_module,
    select_rows,
    package_modules_dir,
)


GOO = package_modules_dir() / "goo_init.json"

def test_megmeet_init_sample_removed():
    """v1.7.22+: Megmeet init sample gone; Megmeet stays in profiles/ only."""
    meg = package_modules_dir() / "megmeet_dc_synergic.json"
    assert not meg.is_file(), f"unexpected leftover {meg}"


def test_goo_sample_exists_and_loads():
    assert GOO.is_file(), f"missing {GOO}"
    mod = load_module(GOO)
    assert mod.name == "固高 GOO"
    assert len(mod.rows) == 6
    assert mod.default_interval_ms == 50
    assert all(r.can_id == 0x1FD07063 for r in mod.rows)
    assert all(r.extended for r in mod.rows)
    assert mod.rows[0].data[0] == 0xF0
    assert mod.rows[5].data[0] == 0xF5
    assert "映射" in (mod.rows[0].note or "")


def test_list_includes_samples():
    mods = list_modules()
    stems = [m.path.stem for m in mods if m.path]
    assert "goo_init" in stems
    assert "megmeet_dc_synergic" not in stems
    pkg_stems = sorted(
        p.stem for p in package_modules_dir().glob("*.json") if p.is_file()
    )
    assert pkg_stems == ["goo_init"]


def test_dry_run_goo():
    mod = load_module(GOO)
    result = run_module(mod, session=None, dry_run=True)
    assert result.ok, result.message
    assert result.completed == 6


def test_dry_run_checked_only():
    mod = load_module(GOO)
    for r in mod.rows:
        r.enabled = False
    mod.rows[0].enabled = True
    mod.rows[2].enabled = True
    result = run_module(mod, session=None, dry_run=True, only_enabled=True)
    assert result.ok, result.message
    assert result.completed == 2


def test_dry_run_single_row():
    mod = load_module(GOO)
    result = run_module(mod, session=None, dry_run=True, indices=[1])
    assert result.ok, result.message
    assert result.completed == 1
    assert result.results[0].row.data[0] == 0xF1


def test_select_rows_empty_checked():
    mod = InitModule(
        name="x",
        rows=[InitRow(enabled=False, can_id=0x123, data=b"\x01")],
    )
    result = run_module(mod, dry_run=True, only_enabled=True)
    assert not result.ok
    assert "没有可执行" in result.message


def test_failure_reports_row():
    mod = InitModule(
        name="fail-test",
        rows=[
            InitRow(can_id=0x123, extended=False, data=b"\x01", note="need-bus"),
        ],
    )
    result = run_module(mod, session=None, dry_run=False)
    assert not result.ok
    assert result.failed_step == 0
    assert "行 1" in result.message


def test_save_roundtrip_rows():
    mod = load_module(GOO)
    mod.rows[0].enabled = False
    mod.rows[0].note = "自定义备注"
    with tempfile.TemporaryDirectory() as d:
        out = Path(d) / "copy.json"
        save_module(mod, out)
        again = load_module(out)
        assert again.name == mod.name
        assert len(again.rows) == 6
        assert again.rows[0].enabled is False
        assert again.rows[0].note == "自定义备注"
        raw = json.loads(out.read_text(encoding="utf-8"))
        assert "rows" in raw
        assert raw["rows"][0].get("enabled") is False
        assert raw["default_interval_ms"] == 50


def test_legacy_steps_conversion():
    legacy = {
        "name": "legacy",
        "mode": "test",
        "description": "old steps",
        "steps": [
            {"type": "delay", "ms": 10, "label": "pre"},
            {
                "type": "send_frame",
                "id": "0x1FD07063",
                "extended": True,
                "data": "AA BB",
                "label": "tx1",
                "enabled": True,
            },
            {"type": "delay", "ms": 30},
            {
                "type": "wait_reply",
                "id": "0x1FD08063",
                "timeout_ms": 100,
                "optional": True,
                "label": "wait note",
            },
            {
                "type": "write_param",
                "key": "k",
                "value": "v",
                "label": "param note",
            },
            {
                "type": "send_frame",
                "id": "0x123",
                "data": "01",
                "label": "tx2",
            },
        ],
    }
    with tempfile.TemporaryDirectory() as d:
        p = Path(d) / "legacy.json"
        p.write_text(json.dumps(legacy), encoding="utf-8")
        mod = load_module(p)
        assert len(mod.rows) == 2
        assert mod.rows[0].data == bytes.fromhex("AA BB")
        assert mod.rows[0].interval_ms == 30
        assert "wait note" in mod.rows[0].note or "param note" in mod.rows[0].note
        assert mod.rows[1].can_id == 0x123


def test_bad_row_json():
    with tempfile.TemporaryDirectory() as d:
        p = Path(d) / "bad.json"
        p.write_text(
            json.dumps({"name": "x", "rows": [{"id": "ZZZ", "data": "01"}]}),
            encoding="utf-8",
        )
        try:
            load_module(p)
            assert False, "should raise"
        except InitModuleError:
            pass


def test_select_rows_indices():
    mod = load_module(GOO)
    sub = select_rows(mod, indices=[5])
    assert len(sub.rows) == 1
    assert sub.rows[0].data[0] == 0xF5


def test_interval_fallback():
    mod = InitModule(
        name="iv",
        default_interval_ms=50,
        rows=[
            InitRow(can_id=1, data=b"\x01", interval_ms=None),
            InitRow(can_id=1, data=b"\x02", interval_ms=0),
            InitRow(can_id=1, data=b"\x03", interval_ms=80),
        ],
    )
    assert mod.rows[0].effective_interval_ms(50) == 50
    assert mod.rows[1].effective_interval_ms(50) == 50
    assert mod.rows[2].effective_interval_ms(50) == 80


def test_blank_module_roundtrip():
    """「新建」may save empty rows; load must accept it."""
    with tempfile.TemporaryDirectory() as d:
        p = Path(d) / "blank_user.json"
        mod = InitModule(name="空白模块", rows=[], default_interval_ms=50)
        save_module(mod, p)
        again = load_module(p)
        assert again.name == "空白模块"
        assert again.rows == []


def test_list_modules_includes_builtin_samples():
    mods = list_modules()
    stems = {m.path.stem for m in mods if m.path}
    names = {m.name for m in mods}
    assert "goo_init" in stems
    assert "megmeet_dc_synergic" not in stems
    assert "固高 GOO" in names
    assert "Megmeet 直流一元" not in names
    pkg_stems = {p.stem for p in package_modules_dir().glob("*.json") if p.is_file()}
    assert pkg_stems == {"goo_init"}


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
    print("all passed")

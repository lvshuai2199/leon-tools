"""Arc-current replay starts on the robot weld rising edge and stops on the fall."""
from can_tester.arc_replay import ArcPlayer, load_groups, package_arc_replay_path
from can_tester.megmeet_responder import CMD_POLL, ResponderState, build_reply, on_robot_frame


def _poll(weld: bool) -> bytes:
    return bytes([CMD_POLL, 0, 0x01 if weld else 0, 0, 0, 0, 0, 0])


def test_shipped_groups():
    path = package_arc_replay_path()
    groups = load_groups(path)
    assert len(groups) == 5
    assert groups[0].id == "1028-1541"
    assert groups[0].samples[0] == (4855, 173)  # logged 44288, byte-swapped
    assert groups[0].ref == 177  # logged 45312
    assert groups[1].id == "1028-1606"
    assert all(100 <= cur <= 300 for _t, cur in groups[1].samples)
    assert all(100 <= cur <= 300 for _t, cur in groups[0].samples)
    for group in groups:
        times = [t for t, _cur in group.samples]
        assert times == sorted(times)
        assert group.count >= 20


def test_replay_waits_for_arc_then_follows_time():
    groups = load_groups(package_arc_replay_path())
    group = groups[1]
    clock = [0.0]
    player = ArcPlayer(groups, clock=lambda: clock[0])
    player.select(group.name)
    st = ResponderState(current=50, auto_arc=True, arc_player=player)

    on_robot_frame(st, _poll(False))
    assert st.current == 50
    assert st.arc_ok is False
    assert player.status_text() == "等待起弧"

    # Non-poll weld bit is not 起弧.
    on_robot_frame(st, bytes([0x01, 0, 0x01, 0, 0, 0, 0, 0]))
    assert st.current == 50
    assert player.playing is False

    clock[0] = 10.0
    on_robot_frame(st, _poll(True))
    assert st.arc_ok is True
    assert player.playing is True
    assert st.current == group.ref
    assert player.status_text().startswith("基准")

    clock[0] = 10.0 + group.samples[0][0] / 1000.0
    on_robot_frame(st, _poll(True))
    assert st.current == group.samples[0][1]
    assert "回放 1/" in player.status_text()

    clock[0] = 10.0 + group.samples[4][0] / 1000.0
    data = on_robot_frame(st, _poll(True))
    assert st.current == group.samples[4][1]
    assert data is not None
    assert data[3] == st.current & 0xFF
    assert data[4] == (st.current >> 8) & 0xFF
    assert ((data[4] << 8) | data[3]) == st.current

    clock[0] = 10.0 + group.samples[-1][0] / 1000.0 + 5.0
    on_robot_frame(st, _poll(True))
    assert st.current == group.samples[-1][1]
    assert player.status_text().startswith("保持")

    on_robot_frame(st, _poll(False))
    assert st.arc_ok is False
    assert player.playing is False
    assert st.current == 50
    assert player.status_text() == "等待起弧"


def test_swapped_group_packs_little_endian_amps():
    groups = load_groups(package_arc_replay_path())
    current = groups[0].samples[0][1]
    assert current == 173
    data = build_reply(ResponderState(current=current, voltage=0))
    assert data[3] == 173 and data[4] == 0
    assert ((data[4] << 8) | data[3]) == 173


def test_select_off_restores_idle_current():
    groups = load_groups(package_arc_replay_path())
    clock = [0.0]
    player = ArcPlayer(groups, clock=lambda: clock[0])
    player.select(groups[2].name)
    st = ResponderState(current=90, arc_player=player)
    on_robot_frame(st, _poll(True))
    assert player.playing is True
    assert st.current != 90
    restore = player.select(None)
    assert restore == 90
    assert player.playing is False
    assert player.status_text() == "关闭"

"""Same-process dual CanSession on virtual bus: responder ↔ client poll."""
from __future__ import annotations

import time

from can_tester.bus import VIRTUAL_IFACE, CanSession
from can_tester.megmeet_responder import CMD_POLL, ROBOT_ID, WELDER_ID
from can_tester.responder_module import find_responder_by_name_or_stem, list_responder_modules
from can_tester.responder_service import ResponderService


def _wait_rx(session: CanSession, can_id: int, timeout_s: float = 1.0):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        for fr in session.drain_rx():
            if fr.can_id == can_id:
                return fr
        time.sleep(0.02)
    return None


def test_dual_virtual_megmeet_poll_reply():
    from can_tester.responder_module import ResponderModule, ResponderRow
    from can_tester.megmeet_responder import build_reply, ResponderState

    mod = find_responder_by_name_or_stem("megmeet_welder")
    if mod is None:
        mods = list_responder_modules()
        if mods:
            mod = mods[0]
        else:
            # Package profiles/ cleared; inline megmeet engine for dual-virtual smoke.
            mod = ResponderModule(
                name="Megmeet 焊机应答",
                engine="megmeet",
                rows=[
                    ResponderRow(
                        enabled=True,
                        match_id=ROBOT_ID,
                        extended=True,
                        reply=build_reply(ResponderState()),
                    )
                ],
            )
    assert mod.is_megmeet_engine()

    server = CanSession()  # 应答端
    client = CanSession()  # 客户端 TX/RX
    server.connect(VIRTUAL_IFACE)
    client.connect(VIRTUAL_IFACE)
    assert server.backend == "virtual" and client.backend == "virtual"

    svc = ResponderService(server, mod)
    # Drain any startup noise
    server.drain_rx()
    client.drain_rx()

    poll = bytes([CMD_POLL, 0, 0, 0, 0, 0, 0, 0])
    client.send(ROBOT_ID, poll, True)

    # Server must see poll and reply
    deadline = time.time() + 1.0
    saw_poll = False
    while time.time() < deadline and not saw_poll:
        for fr in server.drain_rx():
            if fr.can_id == ROBOT_ID:
                saw_poll = True
                svc.handle_frame(fr)
        time.sleep(0.02)
    assert saw_poll, "server did not RX poll on shared virtual"

    reply = _wait_rx(client, WELDER_ID, timeout_s=1.0)
    assert reply is not None, "client did not RX WELDER_ID reply"
    assert reply.is_extended
    assert len(reply.data) == 8
    assert reply.data[0] & 0x01  # ready

    server.disconnect()
    client.disconnect()


def test_dual_virtual_generic_b_to_a():
    """B→A generic frame (no responder): A must RX B's TX."""
    a = CanSession()
    b = CanSession()
    a.connect(VIRTUAL_IFACE)
    b.connect(VIRTUAL_IFACE)
    a.drain_rx()
    b.drain_rx()
    b.send(0x123, bytes([0xAA, 0xBB]), False)
    fr = _wait_rx(a, 0x123, timeout_s=1.0)
    assert fr is not None, "A did not see B generic TX"
    assert fr.data == bytes([0xAA, 0xBB])
    a.disconnect()
    b.disconnect()


if __name__ == "__main__":
    test_dual_virtual_megmeet_poll_reply()
    print("ok megmeet dual")
    test_dual_virtual_generic_b_to_a()
    print("ok generic dual")
    print("all passed")

"""Runtime auto-reply service (library / CLI; GUI can call helpers too).

Matches RX against a ResponderModule and sends replies on a CanSession.
engine=megmeet → dynamic megmeet_responder.on_robot_frame → WELDER_ID.
"""
from __future__ import annotations

import threading
import time
from dataclasses import dataclass
from typing import Callable, Optional

from can_tester.bus import CanFrame, CanSession
from can_tester.megmeet_responder import (
    ROBOT_ID,
    WELDER_ID,
    ResponderState,
    on_robot_frame,
    state_from_mapping,
)
from can_tester.responder_module import (
    ResponderModule,
    ResponderRow,
    matching_rows,
)


@dataclass(frozen=True)
class ReplyPlan:
    """One planned TX for a matched RX."""

    can_id: int
    data: bytes
    extended: bool
    delay_ms: int
    note: str = ""


def plan_reply(
    mod: ResponderModule,
    state: ResponderState,
    can_id: int,
    data: bytes,
) -> list[ReplyPlan]:
    """Build reply plans for one inbound frame (no I/O)."""
    rows = matching_rows(mod, int(can_id))
    if not rows:
        return []
    out: list[ReplyPlan] = []
    use_megmeet = mod.is_megmeet_engine()
    for row in rows:
        delay = row.effective_delay_ms(mod.default_delay_ms)
        if use_megmeet:
            payload = on_robot_frame(state, bytes(data))
            if payload is None:
                continue
            tx_id = WELDER_ID
            ext = True
        else:
            payload = bytes(row.reply)
            if not payload:
                continue
            tx_id = WELDER_ID if int(row.match_id) == int(ROBOT_ID) else int(row.match_id)
            ext = bool(row.extended) or tx_id > 0x7FF
        out.append(
            ReplyPlan(
                can_id=tx_id,
                data=payload,
                extended=ext,
                delay_ms=max(0, int(delay)),
                note=row.note or "",
            )
        )
    return out


class ResponderService:
    """Attach to a CanSession: on each RX match, send configured replies.

    Thread-safe for send via CanSession; call ``handle_frame`` from the
    RX drain path (GUI poll or CLI loop / background thread).
    """

    def __init__(
        self,
        session: CanSession,
        module: ResponderModule,
        *,
        state: ResponderState | None = None,
        on_tx: Optional[Callable[[CanFrame], None]] = None,
        sleep_fn: Callable[[float], None] = time.sleep,
    ) -> None:
        self.session = session
        self.module = module
        self.state = state if state is not None else state_from_mapping(module.state)
        self.on_tx = on_tx
        self._sleep = sleep_fn
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()

    def handle_frame(self, fr: CanFrame) -> list[CanFrame]:
        """Process one RX frame; return TX frames actually sent."""
        if fr.direction != "RX":
            return []
        if not self.session.connected:
            return []
        with self._lock:
            plans = plan_reply(self.module, self.state, fr.can_id, fr.data)
        sent: list[CanFrame] = []
        for plan in plans:
            if plan.delay_ms > 0:
                self._sleep(plan.delay_ms / 1000.0)
            if self._stop.is_set() or not self.session.connected:
                break
            tx = self.session.send(plan.can_id, plan.data, plan.extended)
            sent.append(tx)
            if self.on_tx is not None:
                try:
                    self.on_tx(tx)
                except Exception:
                    pass
        return sent

    def drain_and_reply(self, limit: int = 200) -> list[CanFrame]:
        """Drain session RX and auto-reply; return all TX replies sent."""
        sent: list[CanFrame] = []
        for fr in self.session.drain_rx(limit=limit):
            sent.extend(self.handle_frame(fr))
        return sent

    def start_background(self, period_s: float = 0.02) -> None:
        """Daemon thread: continuously drain RX and reply (CLI listen mode)."""
        self.stop_background()
        self._stop.clear()

        def _loop() -> None:
            while not self._stop.is_set():
                try:
                    self.drain_and_reply()
                except Exception:
                    if self._stop.is_set():
                        break
                self._stop.wait(period_s)

        self._thread = threading.Thread(
            target=_loop, name="can-responder", daemon=True
        )
        self._thread.start()

    def stop_background(self) -> None:
        self._stop.set()
        t = self._thread
        if t and t.is_alive() and t is not threading.current_thread():
            t.join(timeout=1.0)
        self._thread = None

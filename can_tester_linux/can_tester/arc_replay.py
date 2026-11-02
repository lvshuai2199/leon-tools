"""Replay logged Megmeet arc-tracking current after the robot arc-start edge.

Groups come from profiles/arc_replay/groups.json. t_ms is milliseconds after
that pass's ARC_TRACK start. The clock starts when a poll frame's weld bit
rises (机器人起焊 / 起弧). Before the first sample the reply holds `ref`;
after the last sample it holds the tail. Weld falling restores the current
captured at the rising edge.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from can_tester.megmeet_responder import CMD_POLL

OFF_LABEL = "关闭"


def package_arc_replay_path() -> Path:
    return Path(__file__).resolve().parent.parent / "profiles" / "arc_replay" / "groups.json"


@dataclass(frozen=True)
class ArcGroup:
    id: str
    name: str
    ref: int
    samples: tuple[tuple[int, int], ...]
    note: str = ""

    @property
    def count(self) -> int:
        return len(self.samples)


def feedback_amps(value: int) -> int:
    """Turn an ARC_TRACK ``cur`` into the amps FFW's little-endian read returns.

    15:41 logs stored the big-endian view of a little-endian feedback word
    (44288 == 173 A). Later logs are already amps.
    """
    v = max(0, min(0xFFFF, int(value)))
    # Above a real weld current, the log still holds the big-endian integer.
    if v > 500:
        return ((v & 0xFF) << 8) | ((v >> 8) & 0xFF)
    return v


def load_groups(path: Path) -> list[ArcGroup]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError("电弧回放文件必须是 JSON 对象")
    rows = raw.get("groups")
    if not isinstance(rows, list):
        raise ValueError("电弧回放缺少 groups")
    groups: list[ArcGroup] = []
    for item in rows:
        if not isinstance(item, dict):
            continue
        samples_raw = item.get("samples")
        if not isinstance(samples_raw, list) or not samples_raw:
            continue
        samples: list[tuple[int, int]] = []
        for pair in samples_raw:
            if not isinstance(pair, (list, tuple)) or len(pair) < 2:
                continue
            t_ms = max(0, int(pair[0]))
            current = feedback_amps(int(pair[1]))
            samples.append((t_ms, current))
        if not samples:
            continue
        samples.sort(key=lambda row: row[0])
        ref_raw = item.get("ref", samples[0][1])
        try:
            ref = int(round(float(ref_raw)))
        except (TypeError, ValueError):
            ref = samples[0][1]
        ref = feedback_amps(ref)
        groups.append(
            ArcGroup(
                id=str(item.get("id") or ""),
                name=str(item.get("name") or item.get("id") or "未命名"),
                ref=ref,
                samples=tuple(samples),
                note=str(item.get("note") or ""),
            )
        )
    return groups


class ArcPlayer:
    """One player shared by the Megmeet responder state."""

    def __init__(
        self,
        groups: list[ArcGroup] | None = None,
        clock: Callable[[], float] | None = None,
    ) -> None:
        self.groups = list(groups or [])
        self._clock = clock or _monotonic
        self.group: Optional[ArcGroup] = None
        self.playing = False
        self.weld_held = False
        self.phase = "idle"
        self.index = 0
        self.last_current = 0
        self._t0 = 0.0
        self._idle_current: Optional[int] = None
        self._arm = False

    @classmethod
    def load_default(cls) -> "ArcPlayer":
        path = package_arc_replay_path()
        try:
            groups = load_groups(path)
        except (OSError, json.JSONDecodeError, ValueError):
            groups = []
        return cls(groups)

    def now(self) -> float:
        return float(self._clock())

    def find(self, name: str | None) -> Optional[ArcGroup]:
        key = (name or "").strip()
        if not key or key == OFF_LABEL:
            return None
        for group in self.groups:
            if group.name == key or group.id == key:
                return group
        return None

    def select(self, name: str | None) -> Optional[int]:
        """Pick a group. If playback was running, return the current to restore."""
        restore = self._idle_current if self.playing else None
        self.playing = False
        self.phase = "idle"
        self.group = self.find(name)
        self._arm = self.group is not None and self.weld_held
        return restore

    def status_text(self) -> str:
        if self.group is None:
            return OFF_LABEL
        if not self.playing:
            if self._arm and self.weld_held:
                return "下一帧开始"
            return "等待起弧"
        if self.phase == "baseline":
            return f"基准 {self.last_current}A"
        if self.phase == "hold":
            return f"保持 {self.last_current}A"
        return f"回放 {self.index + 1}/{self.group.count} · {self.last_current}A"

    def apply(self, state, robot) -> None:
        """Update state.current from the selected group. Poll frames only."""
        if int(getattr(robot, "cmd", 0)) != CMD_POLL:
            return
        weld = bool(getattr(robot, "weld", False))
        rising = weld and not self.weld_held
        falling = (not weld) and self.weld_held
        self.weld_held = weld
        if falling and self.playing:
            self._stop(state)
            self._arm = False
            return
        if self.group is None:
            return
        if weld and (rising or self._arm) and not self.playing:
            self._start(state)
            self._arm = False
        if self.playing and weld:
            self._tick(state)

    def _start(self, state) -> None:
        try:
            self._idle_current = int(getattr(state, "current", 0))
        except (TypeError, ValueError):
            self._idle_current = 0
        self.playing = True
        self._t0 = self.now()
        self._tick(state)

    def _stop(self, state) -> None:
        if self._idle_current is not None:
            state.current = int(self._idle_current)
        self.playing = False
        self.phase = "idle"

    def _tick(self, state) -> None:
        group = self.group
        if group is None or not group.samples:
            return
        elapsed = int(round((self.now() - self._t0) * 1000.0))
        if elapsed < 0:
            elapsed = 0
        phase, current, index = _sample_at(group, elapsed)
        self.phase = phase
        self.index = index
        self.last_current = current
        state.current = current


def _sample_at(group: ArcGroup, elapsed_ms: int) -> tuple[str, int, int]:
    samples = group.samples
    if elapsed_ms < samples[0][0]:
        return "baseline", group.ref, 0
    lo = 0
    hi = len(samples) - 1
    while lo < hi:
        mid = (lo + hi + 1) // 2
        if samples[mid][0] <= elapsed_ms:
            lo = mid
        else:
            hi = mid - 1
    phase = "hold" if lo == len(samples) - 1 else "play"
    return phase, samples[lo][1], lo


def _monotonic() -> float:
    import time

    return time.monotonic()

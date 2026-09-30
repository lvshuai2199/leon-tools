#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Jasic / WeldingTools JTE CAN 焊机模拟器（虚拟机侧）。

Classic CAN，125 kbit/s。机器人每 20ms：0x202 RPDO1、0x302 RPDO2、0x080 SYNC。
开总线且勾选「应答」后，每 20ms 主动回 0x182 TPDO1、0x282 TPDO2。
字节布局与 can_tester/jasic_nbm.py 的 JTE 组帧一致。
"""

from __future__ import print_function

import os
import subprocess
import threading
import time
import tkinter as tk
import tkinter.font as tkfont
from tkinter import ttk, messagebox, scrolledtext

import can

# 与 can_tester/jasic_nbm.py 的 JTE 段一致，单文件可直接在虚拟机跑。
SYNC_ID = 0x080
TPDO1_ID = 0x182
TPDO2_ID = 0x282
RPDO1_ID = 0x202
RPDO2_ID = 0x302
JTE_MODES = {
    0: "直流一元化",
    1: "脉冲一元化",
    2: "JOB模式",
    3: "近控模式",
    4: "分别模式",
    5: "双脉冲",
}

FLOW_NODES = ["插件", "RPC", "CanBus", "can0", "焊机"]
NOTES = (
    "Jasic JTE / EVOLVE，标准帧，125 kbit/s。\n"
    "机器人每 20ms：0x202 RPDO1、0x302 RPDO2、0x080 SYNC。\n"
    "勾选「应答」后，本程序每 20ms 回 0x182 TPDO1 和 0x282 TPDO2。\n"
    "勾选「就绪」插件才认为焊机就绪。\n"
    "必须和 WeldingTools 用同一个 canN，不要选 virtual。\n"
    "插件 Enable 会把 can0 down 掉，之后重新点「打开总线」。\n"
    "「关闭」只松开本程序，不会 down 掉 can0。\n"
    "pip3 install python-can；缺界面则 sudo apt-get install -y python3-tk"
)
CHIP_OFF = {"bg": "#e5e5e5", "fg": "#777777"}
CHIP_ON = {"bg": "#1565c0", "fg": "#ffffff"}
CHIP_ERR = {"bg": "#c62828", "fg": "#ffffff"}
ROBOT_IDS = (RPDO1_ID, RPDO2_ID, SYNC_ID)


def _word_le(data, offset):
    raw = bytes(data or b"")
    lo = raw[offset] if offset < len(raw) else 0
    hi = raw[offset + 1] if offset + 1 < len(raw) else 0
    return lo | (hi << 8)


def _put_word_le(buf, offset, value):
    number = int(value) & 0xFFFF
    buf[offset] = number & 0xFF
    buf[offset + 1] = (number >> 8) & 0xFF


def _fit(data, size):
    raw = bytes(data or b"")
    if len(raw) >= size:
        return raw[:size]
    return raw + bytes(size - len(raw))


def parse_rpdo1(data):
    control = _word_le(data, 0)
    mode = (control >> 2) & 0x1F
    return {
        "weld": bool(control & 0x01),
        "robot_ready": bool(control & 0x02),
        "mode": mode,
        "mode_name": JTE_MODES.get(mode, str(mode)),
        "gas": bool(control & (1 << 8)),
        "feed": bool(control & (1 << 9)),
        "retract": bool(control & (1 << 10)),
        "fault_reset": bool(control & (1 << 11)),
        "touch": bool(control & (1 << 12)),
        "program": _word_le(data, 2),
        "job": _word_le(data, 4),
        "parameter1": _word_le(data, 6),
    }


def describe_jte_robot(rpdo1, rpdo2=b""):
    cmd = parse_rpdo1(rpdo1)
    chips = (
        ("起弧", cmd["weld"]),
        ("机器人就绪", cmd["robot_ready"]),
        ("检气", cmd["gas"]),
        ("送丝", cmd["feed"]),
        ("退丝", cmd["retract"]),
        ("故障复位", cmd["fault_reset"]),
        ("寻位", cmd["touch"]),
    )
    param2 = _word_le(rpdo2, 0) if rpdo2 else 0
    if param2 >= 0x8000:
        param2 -= 0x10000
    on = " ".join(name for name, flag in chips if flag)
    line = "{} 程序={} JOB={} P1={} P2={}".format(
        cmd["mode_name"], cmd["program"], cmd["job"], cmd["parameter1"], param2
    )
    if on:
        line = line + " " + on
    return {"chips": chips, "line": line, "cmd": cmd}


def pack_tpdo1(ready=True, in_weld=False, current_a=0, voltage_v=0.0, alarm=0):
    status = (0x01 if ready else 0) | (0x10 if in_weld else 0)
    try:
        current = int(round(float(current_a)))
    except (TypeError, ValueError):
        current = 0
    try:
        volts = int(round(float(voltage_v) * 10.0))
    except (TypeError, ValueError):
        volts = 0
    try:
        alarm_w = int(round(float(alarm)))
    except (TypeError, ValueError):
        alarm_w = 0
    buf = bytearray(8)
    _put_word_le(buf, 0, status)
    _put_word_le(buf, 2, current)
    _put_word_le(buf, 4, volts)
    _put_word_le(buf, 6, alarm_w)
    return bytes(buf)


def describe_jte_status(data):
    status = _word_le(data, 0)
    ready = bool(status & 0x01)
    in_weld = bool(status & 0x10)
    current = _word_le(data, 2)
    voltage = _word_le(data, 4) / 10.0
    alarm = _word_le(data, 6)
    on = " ".join(name for name, flag in (("就绪", ready), ("在焊", in_weld)) if flag) or "全关"
    line = "{} {}A {:.1f}V 报警{}".format(on, current, voltage, alarm)
    return {"line": line, "raw": _fit(data, 8)}


def jte_welder_replies(ready=True, in_weld=False, current_a=0, voltage_v=0.0, alarm=0):
    return [
        (TPDO1_ID, pack_tpdo1(ready, in_weld, current_a, voltage_v, alarm)),
        (TPDO2_ID, bytes(8)),
    ]


def hex8(data):
    return " ".join("{:02X}".format(b) for b in data)


def run_cmd(args):
    try:
        p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=5)
        if p.returncode == 0:
            return True, (p.stdout or b"").decode("utf-8", "ignore")
        err = (p.stderr or b"").decode("utf-8", "ignore").strip()
        p2 = subprocess.run(["sudo", "-n"] + args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=5)
        if p2.returncode == 0:
            return True, (p2.stdout or b"").decode("utf-8", "ignore")
        err2 = (p2.stderr or b"").decode("utf-8", "ignore").strip()
        return False, err2 or err or "exit {}".format(p.returncode)
    except Exception as exc:
        return False, str(exc)


def list_can_ifaces():
    net_dir = "/sys/class/net"
    names = []
    if os.path.isdir(net_dir):
        for name in sorted(os.listdir(net_dir)):
            if name.startswith("can") and os.path.isdir(os.path.join(net_dir, name)):
                names.append(name)
    return names or ["can0"]


def pick_family(root, candidates, fallback):
    try:
        names = set(tkfont.families(root))
    except Exception:
        names = set()
    for name in candidates:
        if name in names:
            return name
    return fallback


def given_text(mode, p1, p2):
    """分别模式按 0.1 刻度；其余 P1 为电流、P2 为修正。"""
    if int(mode) == 4:
        return "{:.1f}A {:.1f}V".format(p1 / 10.0, p2 / 10.0)
    return "{}A 修正{}".format(p1, p2)


def build_status_frames(ready, in_weld, current_a, voltage_v, alarm):
    return jte_welder_replies(
        ready=bool(ready),
        in_weld=bool(in_weld),
        current_a=current_a,
        voltage_v=voltage_v,
        alarm=alarm,
    )


class JasicCanResponder(object):
    def __init__(self, root):
        self.root = root
        self.root.title("Jasic CAN 模拟焊机")
        self.root.geometry("780x520")
        self.root.minsize(700, 460)
        self.fn = pick_family(
            root,
            ("Microsoft YaHei", "微软雅黑", "WenQuanYi Micro Hei", "Noto Sans CJK SC", "Droid Sans Fallback"),
            "TkDefaultFont",
        )
        self.mono = pick_family(
            root,
            ("Consolas", "DejaVu Sans Mono", "Ubuntu Mono", "Noto Sans Mono"),
            "TkFixedFont",
        )
        style = ttk.Style(self.root)
        style.configure(".", font=(self.fn, 8))
        style.configure("TLabelframe.Label", font=(self.fn, 8, "bold"))

        self.bus = None
        self.running = False
        self.rx_thread = None
        self.lock = threading.Lock()
        self._beat_job = None
        self._rebind_job = None
        self._session = 0
        self._flow_reset_job = None
        self._flow_redraw_job = None

        ifaces = list_can_ifaces()
        self.var_channel = tk.StringVar(value="can0" if "can0" in ifaces else ifaces[0])
        self.var_bitrate = tk.StringVar(value="125000")
        self.var_bustype = tk.StringVar(value="socketcan")
        self.var_auto_reply = tk.BooleanVar(value=True)
        self.var_auto_arc = tk.BooleanVar(value=True)
        self.var_log_changes = tk.BooleanVar(value=True)

        self.var_ready = tk.BooleanVar(value=True)
        self.var_in_weld = tk.BooleanVar(value=False)
        self.var_current = tk.StringVar(value="180")
        self.var_voltage = tk.StringVar(value="22.5")
        self.var_alarm = tk.StringVar(value="0")

        self.lbl_robot = {}
        self.rx_byte_cells = []
        self.rx2_byte_cells = []
        self.tx_byte_cells = []
        self.flow_boxes = []
        self.flow_fwd = []
        self.flow_back = []

        self.rpdo_count = 0
        self.sync_count = 0
        self.reply_count = 0
        self.last_rpdo_key = None
        self.last_sync_key = None
        self.last_tx_key = None
        self.last_weld = None
        self.last_rpdo1 = b""
        self.last_rpdo2 = b""
        self.hz = 0.0
        self.tx_hz = 0.0
        self.rx_times = []
        self.tx_times = []
        self.last_err_ts = 0.0

        self.build_ui()
        self.append_log("口: {}。125 kbit/s，点「打开总线」。".format(", ".join(ifaces)))

    def build_ui(self):
        self._build_port_bar()
        self._build_flow()
        self._build_body()
        self._build_log()

    def _chip(self, parent, key, title):
        box = tk.Frame(parent)
        tk.Label(box, text=title, font=(self.fn, 7), fg="#555").pack(side="top")
        lab = tk.Label(box, text="—", width=5, font=(self.fn, 8, "bold"), **CHIP_OFF)
        lab.pack(side="top", pady=(1, 0))
        self.lbl_robot[key] = lab
        return box

    def _set_chip(self, key, on, text_on="开", text_off="关", style=None):
        lab = self.lbl_robot.get(key)
        if lab is None:
            return
        colors = style if on else CHIP_OFF
        lab.config(text=text_on if on else text_off, **colors)

    def _build_port_bar(self):
        bar = ttk.Frame(self.root, padding=(6, 4))
        bar.pack(fill="x")

        ttk.Label(bar, text="端口").pack(side="left")
        self.cmb_channel = ttk.Combobox(bar, textvariable=self.var_channel, width=6, values=list_can_ifaces())
        self.cmb_channel.pack(side="left", padx=(4, 2))
        ttk.Button(bar, text="刷新", command=self.refresh_ifaces, width=4).pack(side="left")
        ttk.Combobox(
            bar, textvariable=self.var_bitrate, width=7, values=("125000", "250000", "500000")
        ).pack(side="left", padx=4)
        ttk.Combobox(
            bar, textvariable=self.var_bustype, width=8, values=("socketcan", "slcan", "pcan", "usb2can")
        ).pack(side="left")

        ttk.Separator(bar, orient="vertical").pack(side="left", fill="y", padx=8, pady=2)

        self.led = tk.Label(
            bar, text="关", width=3, bg="#bdbdbd", fg="#333333", font=(self.fn, 8), relief="groove"
        )
        self.led.pack(side="left")
        self.btn_open = ttk.Button(bar, text="打开总线", command=self.open_port, width=8)
        self.btn_open.pack(side="left", padx=(6, 2))
        self.btn_close = ttk.Button(bar, text="关闭", command=self.close_port, width=6, state="disabled")
        self.btn_close.pack(side="left")
        ttk.Button(bar, text="说明", command=self.show_notes, width=4).pack(side="left", padx=6)

        ttk.Checkbutton(bar, text="应答", variable=self.var_auto_reply, command=self._on_auto_toggle).pack(
            side="right", padx=2
        )
        ttk.Checkbutton(bar, text="起弧沿在焊", variable=self.var_auto_arc).pack(side="right")
        ttk.Checkbutton(bar, text="只记变化", variable=self.var_log_changes).pack(side="right", padx=2)

        stat = ttk.Frame(self.root, padding=(6, 0, 6, 2))
        stat.pack(fill="x")
        self.lbl_hz = ttk.Label(stat, text="收 0 Hz  发 0 Hz")
        self.lbl_hz.pack(side="left")
        self.lbl_cnt = ttk.Label(stat, text="  RPDO 0  SYNC 0  应答 0")
        self.lbl_cnt.pack(side="left")
        self.lbl_last = ttk.Label(stat, text="等待打开总线")
        self.lbl_last.pack(side="left", padx=8)

    def _build_flow(self):
        self.canvas = tk.Canvas(self.root, height=36, bg="#eceff1", highlightthickness=0)
        self.canvas.pack(fill="x")
        self.root.after(60, self._draw_flow)
        self.root.bind("<Configure>", lambda e: self._schedule_flow_redraw())

    def _schedule_flow_redraw(self):
        if self._flow_redraw_job:
            self.root.after_cancel(self._flow_redraw_job)
        self._flow_redraw_job = self.root.after(120, self._draw_flow)

    def _draw_flow(self):
        c = self.canvas
        c.delete("all")
        w = max(c.winfo_width(), 600)
        n = len(FLOW_NODES)
        box_w, box_h = 70, 22
        gap = max(8, (w - 12 - n * box_w) / float(n - 1))
        y = 7
        self.flow_boxes = []
        self.flow_fwd = []
        self.flow_back = []
        xs = []
        for i, title in enumerate(FLOW_NODES):
            x = 6 + i * (box_w + gap)
            xs.append(x)
            box = c.create_rectangle(x, y, x + box_w, y + box_h, fill="#ffffff", outline="#607d8b", width=1)
            c.create_text(x + box_w / 2, y + box_h / 2, text=title, font=(self.fn, 8))
            self.flow_boxes.append(box)
        for i in range(n - 1):
            x1 = xs[i] + box_w
            x2 = xs[i + 1]
            self.flow_fwd.append(c.create_line(x1 + 1, y + 7, x2 - 1, y + 7, arrow=tk.LAST, fill="#b0bec5", width=2))
            self.flow_back.append(c.create_line(x2 - 1, y + 16, x1 + 1, y + 16, arrow=tk.LAST, fill="#b0bec5", width=2))

    def _flash_flow(self, direction):
        color = "#1565c0" if direction == "rx" else "#2e7d32"
        items = self.flow_fwd if direction == "rx" else self.flow_back
        boxes = self.flow_boxes if direction == "rx" else list(reversed(self.flow_boxes))
        for item in items:
            self.canvas.itemconfig(item, fill=color, width=3)
        for box in boxes:
            self.canvas.itemconfig(box, outline=color, width=2)
        if self._flow_reset_job:
            self.root.after_cancel(self._flow_reset_job)
        self._flow_reset_job = self.root.after(220, self._reset_flow)

    def _reset_flow(self):
        self._flow_reset_job = None
        for item in self.flow_fwd + self.flow_back:
            self.canvas.itemconfig(item, fill="#b0bec5", width=2)
        for box in self.flow_boxes:
            self.canvas.itemconfig(box, outline="#607d8b", width=1)

    def _build_body(self):
        mid = tk.Frame(self.root)
        mid.pack(fill="both", expand=True, padx=4, pady=2)

        left = tk.LabelFrame(
            mid, text=" 插件下发  0x202 / 0x302 / 0x080 ", font=(self.fn, 8), padx=4, pady=2
        )
        left.pack(side="left", fill="both", expand=True, padx=(0, 3))
        self._build_byte_row(left, "202", self.rx_byte_cells, "#e3f2fd")
        self._build_byte_row(left, "302", self.rx2_byte_cells, "#e3f2fd")
        chips = tk.Frame(left)
        chips.pack(fill="x", pady=3)
        for key, title in (
            ("起弧", "起弧"),
            ("机器人就绪", "机就"),
            ("检气", "检气"),
            ("送丝", "送丝"),
            ("退丝", "退丝"),
            ("故障复位", "复位"),
            ("寻位", "寻位"),
        ):
            self._chip(chips, key, title).pack(side="left", padx=1)
        extra = tk.Frame(left)
        extra.pack(fill="x")
        tk.Label(extra, text="模式", font=(self.fn, 7)).pack(side="left")
        self.lbl_mode = tk.Label(extra, text="—", font=(self.fn, 8, "bold"))
        self.lbl_mode.pack(side="left", padx=(2, 6))
        tk.Label(extra, text="程序", font=(self.fn, 7)).pack(side="left")
        self.lbl_prog = tk.Label(extra, text="—", font=(self.mono, 10, "bold"), fg="#0d47a1")
        self.lbl_prog.pack(side="left", padx=(2, 6))
        tk.Label(extra, text="JOB", font=(self.fn, 7)).pack(side="left")
        self.lbl_job = tk.Label(extra, text="—", font=(self.mono, 11, "bold"), fg="#0d47a1")
        self.lbl_job.pack(side="left", padx=2)
        extra2 = tk.Frame(left)
        extra2.pack(fill="x", pady=(2, 0))
        tk.Label(extra2, text="给定", font=(self.fn, 7)).pack(side="left")
        self.lbl_given = tk.Label(extra2, text="—", font=(self.mono, 10, "bold"), fg="#0d47a1")
        self.lbl_given.pack(side="left", padx=4)

        right = tk.LabelFrame(mid, text=" 焊机回帧  0x182 / 0x282 ", font=(self.fn, 8), padx=4, pady=2)
        right.pack(side="left", fill="both", expand=True)
        self._build_byte_row(right, "182", self.tx_byte_cells, "#e8f5e9")
        tk.Label(right, text="0x282 固定 00 00 00 00 00 00 00 00", font=(self.fn, 7), fg="#555").pack(anchor="w")

        checks = tk.Frame(right)
        checks.pack(fill="x", pady=2)
        ttk.Checkbutton(checks, text="就绪", variable=self.var_ready, command=self._refresh_tx_preview).pack(
            side="left", padx=2
        )
        ttk.Checkbutton(checks, text="在焊", variable=self.var_in_weld, command=self._refresh_tx_preview).pack(
            side="left", padx=2
        )

        vals = tk.Frame(right)
        vals.pack(fill="x", pady=2)
        tk.Label(vals, text="A", font=(self.fn, 8, "bold")).pack(side="left")
        ttk.Entry(vals, textvariable=self.var_current, width=5).pack(side="left", padx=(0, 6))
        tk.Label(vals, text="V", font=(self.fn, 8, "bold")).pack(side="left")
        ttk.Entry(vals, textvariable=self.var_voltage, width=5).pack(side="left", padx=(0, 6))
        tk.Label(vals, text="报警", font=(self.fn, 8, "bold")).pack(side="left")
        ttk.Entry(vals, textvariable=self.var_alarm, width=5).pack(side="left")
        ttk.Button(vals, text="手发一帧", command=self.send_reply, width=8).pack(side="right")

        self.var_current.trace("w", lambda *_: self._refresh_tx_preview())
        self.var_voltage.trace("w", lambda *_: self._refresh_tx_preview())
        self.var_alarm.trace("w", lambda *_: self._refresh_tx_preview())
        self.root.after(150, self._refresh_tx_preview)

    def _build_byte_row(self, parent, prefix, store, bg):
        row = tk.Frame(parent)
        row.pack(fill="x")
        tk.Label(row, text=prefix, width=3, font=(self.mono, 8, "bold")).pack(side="left")
        for _ in range(8):
            cell = tk.Label(row, text="--", width=3, relief="solid", bd=1, font=(self.mono, 10, "bold"), bg=bg)
            cell.pack(side="left", padx=1)
            store.append(cell)

    def _build_log(self):
        frame = tk.LabelFrame(self.root, text="时间线", font=(self.fn, 8, "bold"))
        frame.pack(fill="both", expand=True, padx=4, pady=(0, 4))
        self.log = scrolledtext.ScrolledText(frame, height=6, font=(self.mono, 8))
        self.log.pack(fill="both", expand=True)
        self.log.tag_config("rx", foreground="#1565c0")
        self.log.tag_config("tx", foreground="#1b5e20")
        self.log.tag_config("info", foreground="#333")
        self.log.tag_config("chg", foreground="#0d47a1")
        ttk.Button(frame, text="清空", command=lambda: self.log.delete("1.0", "end"), width=5).pack(
            anchor="e", padx=2, pady=1
        )

    def refresh_ifaces(self):
        names = list_can_ifaces()
        self.cmb_channel["values"] = names
        if self.var_channel.get() not in names:
            self.var_channel.set("can0" if "can0" in names else names[0])
        self.append_log("刷新: {}".format(", ".join(names)), "info")

    def append_log(self, text, tag="info"):
        self.log.insert("end", time.strftime("[%H:%M:%S] ") + text + "\n", tag)
        self.log.see("end")

    def set_bytes(self, cells, data, active_bg):
        for i, cell in enumerate(cells):
            cell.config(text="{:02X}".format(data[i]) if i < len(data) else "--", bg=active_bg if i < len(data) else "#eee")

    def _numbers(self):
        try:
            current = int(round(float(self.var_current.get())))
        except ValueError:
            current = 0
        try:
            voltage = float(self.var_voltage.get())
        except ValueError:
            voltage = 0.0
        try:
            alarm = int(round(float(self.var_alarm.get())))
        except ValueError:
            alarm = 0
        current = max(0, min(65535, current))
        alarm = max(0, min(65535, alarm))
        return current, voltage, alarm

    def build_reply(self):
        current, voltage, alarm = self._numbers()
        frames = build_status_frames(self.var_ready.get(), self.var_in_weld.get(), current, voltage, alarm)
        return frames

    def _refresh_tx_preview(self):
        if not self.tx_byte_cells:
            return
        frames = self.build_reply()
        self.set_bytes(self.tx_byte_cells, frames[0][1], "#c8e6c9")

    def _set_led(self, on):
        if on:
            self.led.config(text="开", bg="#c8e6c9", fg="#1b5e20")
            self.btn_open.config(state="disabled")
            self.btn_close.config(state="normal")
        else:
            self.led.config(text="关", bg="#bdbdbd", fg="#333333")
            self.btn_open.config(state="normal")
            self.btn_close.config(state="disabled")

    def _paint_counts(self):
        self.lbl_hz.config(text="收 {:.0f} Hz  发 {:.0f} Hz".format(self.hz, self.tx_hz))
        self.lbl_cnt.config(
            text="  RPDO {}  SYNC {}  应答 {}".format(self.rpdo_count, self.sync_count, self.reply_count)
        )

    def iface_path(self, name):
        return os.path.join("/sys/class/net", name)

    def iface_exists(self, name):
        return os.path.isdir(self.iface_path(name))

    def iface_is_up(self, name):
        oper = os.path.join(self.iface_path(name), "operstate")
        if not os.path.isfile(oper):
            return False
        try:
            state = open(oper, "r").read().strip().lower()
        except Exception:
            return False
        return state in ("up", "unknown")

    def bring_iface_up(self):
        name = self.var_channel.get()
        baud = self.var_bitrate.get()
        if self.var_bustype.get() != "socketcan":
            return True
        if not self.iface_exists(name):
            self.append_log("没有网卡 {}，跳过 ip link".format(name), "info")
            return False
        if self.iface_is_up(name):
            self.append_log("{} 已是 UP，直接绑定".format(name), "info")
            return True
        ok, msg = run_cmd(["ip", "link", "set", name, "up", "type", "can", "bitrate", baud])
        if ok:
            self.append_log("端口已 up: {} bitrate {}".format(name, baud), "info")
            return True
        ok2, msg2 = run_cmd(["ip", "link", "set", name, "up"])
        if ok2:
            self.append_log("端口已 up: {}".format(name), "info")
            return True
        self.append_log("ip link 无权限或失败（可忽略）: {}".format(msg2 or msg), "info")
        return self.iface_exists(name)

    def show_notes(self):
        note_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "can0启动说明.txt")
        text = NOTES
        try:
            with open(note_path, "r", encoding="utf-8") as f:
                extra = f.read().strip()
            if extra:
                text = NOTES + "\n\n" + extra
        except Exception:
            pass
        win = tk.Toplevel(self.root)
        win.title("Jasic CAN 模拟说明")
        win.geometry("640x420")
        box = scrolledtext.ScrolledText(win, font=(self.fn, 9), wrap="word")
        box.pack(fill="both", expand=True, padx=6, pady=6)
        box.insert("1.0", text)
        box.config(state="disabled")
        ttk.Button(win, text="关闭", command=win.destroy).pack(pady=(0, 6))

    def open_port(self):
        self.refresh_ifaces()
        name = self.var_channel.get()
        if self.var_bustype.get() == "socketcan" and not self.iface_exists(name):
            messagebox.showerror(
                "打开总线",
                "系统里没有 {}。\n\n先在插件里连接 Jasic（会拉起 can0），\n或确认 USB2CAN 已枚举，再点刷新。".format(name),
            )
            return
        self.bring_iface_up()
        self.open_bus()

    def close_port(self):
        self.close_bus()

    def open_bus(self):
        kwargs = {"bustype": self.var_bustype.get(), "channel": self.var_channel.get()}
        if self.var_bustype.get() != "socketcan":
            kwargs["bitrate"] = int(self.var_bitrate.get())
        try:
            self.bus = can.interface.Bus(**kwargs)
        except Exception as exc:
            messagebox.showerror("打开失败", str(exc))
            return
        self.running = True
        self.rx_thread = threading.Thread(target=self.rx_loop)
        self.rx_thread.daemon = True
        self.rx_thread.start()
        self._set_led(True)
        self.lbl_last.config(text="总线已开，等插件 0x202 → {}".format(self.var_channel.get()))
        self.append_log(
            "已绑定 {} / {}  125k 标准帧 0x202/0x302/0x080 → 0x182/0x282".format(
                self.var_bustype.get(), self.var_channel.get()
            ),
            "info",
        )
        self._arm_beat()

    def close_bus(self):
        self.running = False
        self._cancel_beat()
        self._cancel_rebind()
        self._drop_socket()
        self._set_led(False)
        self.lbl_last.config(text="总线已关")
        self.append_log("已关闭绑定", "info")

    def _drop_socket(self):
        self._session += 1
        bus = self.bus
        self.bus = None
        if bus is not None:
            try:
                bus.shutdown()
            except Exception:
                pass

    def _cancel_rebind(self):
        if self._rebind_job is not None:
            try:
                self.root.after_cancel(self._rebind_job)
            except Exception:
                pass
        self._rebind_job = None

    def _schedule_rebind(self):
        if not self.running or self._rebind_job is not None:
            return
        self._rebind_job = self.root.after(200, self._rebind)

    def _on_bus_lost(self, session, text):
        if not self.running or session != self._session:
            return
        if text:
            self.append_log(text, "info")
        self.lbl_last.config(text="网卡被插件重启，正在重新绑定")
        self._drop_socket()
        self._schedule_rebind()

    def _rebind(self):
        self._rebind_job = None
        if not self.running or self.bus is not None:
            return
        name = self.var_channel.get()
        if self.var_bustype.get() == "socketcan" and not self.iface_is_up(name):
            self.lbl_last.config(text="{} down，等插件把它 up".format(name))
            self._schedule_rebind()
            return
        kwargs = {"bustype": self.var_bustype.get(), "channel": name}
        if self.var_bustype.get() != "socketcan":
            kwargs["bitrate"] = int(self.var_bitrate.get())
        try:
            self.bus = can.interface.Bus(**kwargs)
        except Exception as exc:
            self.append_log("重新绑定失败: {}".format(exc), "info")
            self._schedule_rebind()
            return
        self.rx_thread = threading.Thread(target=self.rx_loop)
        self.rx_thread.daemon = True
        self.rx_thread.start()
        self.lbl_last.config(text="已重新绑定 {}，继续等 0x202".format(name))
        self.append_log("已重新绑定 {}".format(name), "info")
        self._arm_beat()

    def _cancel_beat(self):
        if self._beat_job is not None:
            try:
                self.root.after_cancel(self._beat_job)
            except Exception:
                pass
        self._beat_job = None

    def _arm_beat(self):
        self._cancel_beat()
        if self.running and self.bus is not None and self.var_auto_reply.get():
            self._beat()

    def _on_auto_toggle(self):
        if self.running and self.var_auto_reply.get():
            self.append_log("应答开，每 20ms 回 0x182/0x282", "info")
            self._arm_beat()
        else:
            self._cancel_beat()
            if self.running:
                self.append_log("应答关", "info")

    def _beat(self):
        self._beat_job = None
        if not (self.running and self.var_auto_reply.get()):
            return
        if self.bus is None:
            self._schedule_rebind()
            self._beat_job = self.root.after(200, self._beat)
            return
        self.send_reply(from_auto=True)
        if self.running and self.var_auto_reply.get():
            self._beat_job = self.root.after(20, self._beat)

    def rx_loop(self):
        bus = self.bus
        session = self._session
        while self.running and self.bus is bus and self._session == session:
            try:
                msg = bus.recv(timeout=0.2)
            except Exception as exc:
                self.root.after(0, self._on_bus_lost, session, "收帧异常: {}".format(exc))
                return
            if msg is None or getattr(msg, "is_extended_id", False):
                continue
            if msg.arbitration_id not in ROBOT_IDS:
                continue
            data = list(msg.data) + [0] * 8
            self.root.after(0, self.on_robot_frame, int(msg.arbitration_id), data[:8])

    def _note_rx_rate(self):
        now = time.time()
        self.rx_times = [t for t in self.rx_times if now - t < 1.0]
        self.rx_times.append(now)
        self.hz = len(self.rx_times)

    def on_robot_frame(self, can_id, data):
        self._note_rx_rate()
        self._flash_flow("rx")
        if can_id == SYNC_ID:
            self.sync_count += 1
            key = tuple(data[:1])
            if key != self.last_sync_key or not self.var_log_changes.get():
                self.append_log("↓ SYNC 0x080  [{}]".format(hex8(data[:1] or [0])), "rx")
                self.last_sync_key = key
            self.lbl_last.config(text="SYNC")
            self._paint_counts()
            return
        if can_id == RPDO2_ID:
            self.rpdo_count += 1
            self.last_rpdo2 = bytes(data[:8])
            self.set_bytes(self.rx2_byte_cells, data, "#bbdefb")
            self._paint_robot(changed_id=can_id)
            return
        self.last_rpdo1 = bytes(data[:8])
        self.rpdo_count += 1
        self.set_bytes(self.rx_byte_cells, data, "#bbdefb")
        self._paint_robot(changed_id=can_id)

    def _paint_robot(self, changed_id):
        view = describe_jte_robot(self.last_rpdo1 or bytes(8), self.last_rpdo2 or bytes(8))
        cmd = view["cmd"]
        raw2 = self.last_rpdo2 or b""
        p2 = (raw2[0] if len(raw2) > 0 else 0) | ((raw2[1] if len(raw2) > 1 else 0) << 8)
        if p2 >= 0x8000:
            p2 -= 0x10000
        given = given_text(cmd["mode"], cmd["parameter1"], p2)
        self.lbl_mode.config(text=cmd["mode_name"])
        self.lbl_prog.config(text=str(cmd["program"]))
        self.lbl_job.config(text=str(cmd["job"]))
        self.lbl_given.config(text=given)
        flags = dict(view["chips"])
        self._set_chip("起弧", flags.get("起弧"), style=CHIP_ON)
        self._set_chip("机器人就绪", flags.get("机器人就绪"), style=CHIP_ON)
        self._set_chip("检气", flags.get("检气"), style=CHIP_ON)
        self._set_chip("送丝", flags.get("送丝"), style=CHIP_ON)
        self._set_chip("退丝", flags.get("退丝"), style=CHIP_ON)
        self._set_chip("故障复位", flags.get("故障复位"), style=CHIP_ON)
        self._set_chip("寻位", flags.get("寻位"), style=CHIP_ON)
        action = view["line"]
        key = (self.last_rpdo1, self.last_rpdo2)
        changed = key != self.last_rpdo_key
        self.last_rpdo_key = key
        if changed or not self.var_log_changes.get():
            tag = "chg" if changed else "rx"
            which = "0x202" if changed_id == RPDO1_ID else "0x302"
            raw = self.last_rpdo1 if changed_id == RPDO1_ID else self.last_rpdo2
            self.append_log("↓ {} {}  [{}]".format(which, action, hex8(raw)), tag)
        self.lbl_last.config(text=action)
        self._paint_counts()
        self._apply_auto_arc(bool(cmd["weld"]))

    def _apply_auto_arc(self, weld):
        if not self.var_auto_arc.get():
            self.last_weld = weld
            return
        if self.last_weld is None:
            self.last_weld = weld
            return
        if weld and not self.last_weld:
            self.var_in_weld.set(True)
            self.append_log("起弧↑ 自动勾在焊", "tx")
        elif (not weld) and self.last_weld:
            self.var_in_weld.set(False)
            self.append_log("起弧↓ 清在焊", "info")
        self.last_weld = weld

    def send_reply(self, from_auto=False):
        if self.bus is None:
            if not from_auto:
                messagebox.showwarning("未打开", "先点「打开总线」")
            return
        frames = self.build_reply()
        tpdo1 = frames[0][1]
        session = self._session
        try:
            with self.lock:
                current = self.bus
                if current is None:
                    raise OSError("总线已断开")
                for can_id, data in frames:
                    current.send(can.Message(arbitration_id=can_id, data=data, is_extended_id=False))
        except Exception as exc:
            now = time.time()
            if now - self.last_err_ts > 2.0:
                self.last_err_ts = now
                self._on_bus_lost(session, "发帧失败: {}".format(exc))
            else:
                self._on_bus_lost(session, "")
            return
        self.reply_count += 1
        now = time.time()
        self.tx_times = [t for t in self.tx_times if now - t < 1.0]
        self.tx_times.append(now)
        self.tx_hz = len(self.tx_times)
        key = tuple(tpdo1)
        changed = key != self.last_tx_key
        self.last_tx_key = key
        if changed or not from_auto:
            self.set_bytes(self.tx_byte_cells, tpdo1, "#a5d6a7")
            self._flash_flow("tx")
        if changed or not self.var_log_changes.get() or not from_auto:
            view = describe_jte_status(tpdo1)
            self.append_log("↑ {}  [{}]".format(view["line"], hex8(tpdo1)), "tx")
        self._paint_counts()


if __name__ == "__main__":
    root = tk.Tk()
    app = JasicCanResponder(root)

    def on_close():
        app.close_bus()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)
    root.mainloop()

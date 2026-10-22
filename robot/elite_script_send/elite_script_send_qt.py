#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Elite 脚本下发 — PySide6 界面（复用 sender / state_listener / trajectory）。"""
from __future__ import annotations

import argparse
import json
import socket
import sys
import threading
import time
import traceback
import webbrowser
from pathlib import Path

from PySide6.QtCore import Qt, QTimer, Signal, QObject, QSize
from PySide6.QtGui import QAction, QColor, QFont, QTextCharFormat, QTextCursor
from PySide6.QtWidgets import (
    QApplication,
    QCheckBox,
    QComboBox,
    QFileDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QSplitter,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from sender import (
    DEFAULT_HOST,
    DEFAULT_PORT,
    DEFAULT_SCRIPT_DIR,
    DEFAULT_TIMEOUT,
    SendError,
    app_dir,
    boot_and_send,
    brake_releasing,
    close_popup,
    dashboard_cmd,
    emergency_stop,
    list_script_files,
    play,
    powering_off,
    powering_on,
    send_text,
    stop_task,
    task,
)
from state_listener import RobotStateStream, format_pose
from trajectory import pose_sample, write_csv, write_html

# CLI helpers live in Tk module; import without launching GUI
import elite_script_send as _legacy

SETTINGS_PATH = app_dir() / "last_settings.json"
HOST_HISTORY_MAX = 20
SAMPLE = _legacy.SAMPLE
TITLE_CMDS = _legacy.TITLE_CMDS
load_settings = _legacy.load_settings
save_settings = _legacy.save_settings
_read_file = _legacy._read_file
run_cli = _legacy.run_cli

BTN_MIN_H = 29
NUM_FONT = QFont("Consolas", 10)
UI_FONT = QFont("Microsoft YaHei", 9)


class _Bridge(QObject):
    listen_status = Signal(str, str)
    work_done = Signal(str, object, object, bool)  # title, result, err, force


class EliteScriptWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Elite 脚本下发测试")
        self.setMinimumSize(720, 420)
        self.cfg = load_settings()
        geo = self.cfg.get("geometry") or "900x620"
        try:
            part = str(geo).split("+")[0]
            w, h = part.lower().split("x")
            self.resize(max(720, int(w)), max(420, int(h)))
        except (TypeError, ValueError):
            self.resize(900, 620)

        self.script_dir = self._resolve_script_dir(self.cfg.get("script_dir"))
        self.busy = False
        self.stop_event = threading.Event()
        self._loading = True
        self.host_history = self._load_host_history(self.cfg)
        self.history = list(self.cfg.get("command_history") or [])
        self.current_name = ""
        self._dir_full = ""

        self._listen_stop = threading.Event()
        self._listen_thread = None
        self._latest_pose = None
        self._listening = False
        self._recording = False
        self._rec_lock = threading.Lock()
        self._rec_samples = []
        self._rec_t0 = None

        self._bridge = _Bridge()
        self._bridge.listen_status.connect(self._on_listen_status)
        self._bridge.work_done.connect(self._done)

        self._save_timer = QTimer(self)
        self._save_timer.setSingleShot(True)
        self._save_timer.timeout.connect(self._save_now)

        self._pose_timer = QTimer(self)
        self._pose_timer.timeout.connect(self._poll_pose)

        self._build_ui()
        self._refresh(log=False)
        self._restore_last_file()
        self._refresh_history_list()
        self._loading = False
        self._start_listen()
        self._pose_timer.start(100)

    # ----- settings helpers -----
    def _resolve_script_dir(self, saved_dir):
        candidates = []
        if saved_dir:
            candidates.append(Path(str(saved_dir)).expanduser())
        candidates.append(Path(DEFAULT_SCRIPT_DIR))
        candidates.append(app_dir() / "scripts")
        for cand in candidates:
            try:
                resolved = cand.resolve()
            except OSError:
                resolved = cand
            if resolved.is_dir():
                return resolved
        fallback = Path(DEFAULT_SCRIPT_DIR)
        try:
            fallback.mkdir(parents=True, exist_ok=True)
        except OSError:
            pass
        return fallback

    def _ellipsis_path(self, shown, limit=36):
        shown = str(shown or "")
        if len(shown) <= limit:
            return shown
        return "…" + shown[-(limit - 1) :]

    def _set_filename(self, name):
        name = (name or "").strip()
        self.current_name = name
        self.lbl_filename.setText(name if name else "未命名")

    def _load_host_history(self, cfg):
        raw = cfg.get("host_history") or []
        current = str(cfg.get("host") or DEFAULT_HOST).strip()
        history, seen = [], set()
        for item in [current] + list(raw):
            host = str(item).strip()
            if not host or host in seen:
                continue
            seen.add(host)
            history.append(host)
        return history[:HOST_HISTORY_MAX]

    def _remember_host(self, host=None):
        host = (self.cmb_host.currentText() if host is None else host).strip()
        if not host:
            return
        items = [item for item in self.host_history if item != host]
        self.host_history = ([host] + items)[:HOST_HISTORY_MAX]
        cur = self.cmb_host.currentText()
        self.cmb_host.blockSignals(True)
        self.cmb_host.clear()
        self.cmb_host.addItems(self.host_history)
        self.cmb_host.setEditText(cur or host)
        self.cmb_host.blockSignals(False)
        self._schedule_save()

    def _clear_host_history(self):
        current = self.cmb_host.currentText().strip()
        extras = [item for item in self.host_history if item != current]
        if not extras:
            self._append("连接历史里没有其它地址")
            return
        ans = QMessageBox.question(
            self,
            "清空连接历史",
            "清空 %d 条已保存的 IP？\n当前地址会保留：%s" % (len(extras), current or "（空）"),
        )
        if ans != QMessageBox.Yes:
            return
        self.host_history = [current] if current else []
        self.cmb_host.blockSignals(True)
        self.cmb_host.clear()
        self.cmb_host.addItems(self.host_history)
        self.cmb_host.setEditText(current)
        self.cmb_host.blockSignals(False)
        self._schedule_save()
        self._append("已清空连接历史", "ok")

    def _tool_btn(self, text, slot, danger=False, primary=False, success=False, min_w=0):
        btn = QPushButton(text)
        btn.setFixedHeight(BTN_MIN_H)
        if min_w:
            btn.setMinimumWidth(min_w)
        btn.setCursor(Qt.PointingHandCursor)
        if danger:
            btn.setObjectName("danger")
        elif primary:
            btn.setObjectName("primary")
        elif success:
            btn.setObjectName("success")
        btn.clicked.connect(slot)
        return btn

    def _build_ui(self):
        # 美工定稿：页底 F5F7FA / 白卡 / 右栏操作竖条
        root = QWidget()
        self.setCentralWidget(root)
        root.setStyleSheet(
            "QWidget{background:#F5F7FA;color:#1F2937;font-family:'Microsoft YaHei';font-size:9pt;}"
            "QFrame#zone{background:#FFFFFF;border:1px solid #E5E7EB;border-radius:8px;}"
            "QPlainTextEdit,QListWidget,QLineEdit,QComboBox{"
            "background:#FFFFFF;color:#1F2937;border:1px solid #E5E7EB;border-radius:8px;padding:3px;}"
            "QTabWidget::pane{border:1px solid #E5E7EB;background:#FFFFFF;border-radius:8px;}"
            "QTabBar::tab{background:#FFFFFF;color:#6B7280;border:1px solid #E5E7EB;"
            "border-bottom:none;border-top-left-radius:6px;border-top-right-radius:6px;padding:6px 12px;}"
            "QTabBar::tab:selected{color:#2563EB;background:#EFF4FF;}"
            "QPushButton{background:#FFFFFF;color:#1F2937;border:1px solid #E5E7EB;"
            "border-radius:6px;padding:0px 10px;min-height:0px;}"
            "QPushButton:hover{background:#EFF4FF;border-color:#2563EB;color:#2563EB;}"
            "QPushButton#primary{background:#2563EB;color:#FFFFFF;border:1px solid #2563EB;}"
            "QPushButton#primary:hover{background:#1D4ED8;}"
            "QPushButton#danger{background:#DC2626;color:#FFFFFF;border:1px solid #DC2626;font-weight:bold;}"
            "QPushButton#danger:hover{background:#B91C1C;}"
            "QPushButton#success{background:#15803D;color:#FFFFFF;border:1px solid #15803D;}"
            "QPushButton#success:hover{background:#166534;}"
            "QLabel#muted{color:#6B7280;}"
            "QSplitter::handle{background:#E5E7EB;width:0px;}"
            "QPlainTextEdit#log{background:#FFFFFF;border:1px solid #E5E7EB;}"
            "QScrollBar:horizontal{height:6px;background:transparent;margin:0;}"
            "QScrollBar:horizontal:handle{background:#D1D5DB;border-radius:3px;min-width:24px;}"
            "QScrollBar:vertical{width:6px;background:transparent;margin:0;}"
            "QScrollBar:vertical:handle{background:#D1D5DB;border-radius:3px;min-height:24px;}"
        )
        lay = QVBoxLayout(root)
        lay.setContentsMargins(12, 10, 12, 10)
        lay.setSpacing(10)

        # ----- 1 顶栏 -----
        bar = QHBoxLayout()
        bar.setSpacing(6)
        bar.addWidget(QLabel("IP"))
        self.cmb_host = QComboBox()
        self.cmb_host.setEditable(True)
        self.cmb_host.setMinimumWidth(140)
        self.cmb_host.addItems(self.host_history)
        self.cmb_host.setEditText(str(self.cfg.get("host") or DEFAULT_HOST))
        self.cmb_host.currentTextChanged.connect(lambda *_: self._schedule_save())
        self.cmb_host.lineEdit().editingFinished.connect(self._remember_host)
        act_clear = QAction("清空连接历史", self)
        act_clear.triggered.connect(self._clear_host_history)
        self.cmb_host.setContextMenuPolicy(Qt.ActionsContextMenu)
        self.cmb_host.addAction(act_clear)
        bar.addWidget(self.cmb_host)
        bar.addWidget(QLabel("PORT"))
        self.ed_port = QLineEdit(str(self.cfg.get("port") or DEFAULT_PORT))
        self.ed_port.setFixedWidth(56)
        self.ed_port.textChanged.connect(lambda *_: self._schedule_save())
        bar.addWidget(self.ed_port)
        bar.addWidget(QLabel("超时"))
        self.ed_timeout = QLineEdit(str(self.cfg.get("timeout") or DEFAULT_TIMEOUT))
        self.ed_timeout.setFixedWidth(48)
        self.ed_timeout.textChanged.connect(lambda *_: self._schedule_save())
        bar.addWidget(self.ed_timeout)
        # 回执/Dashboard 已去；测连保留（帅吕：测连不需要去掉）
        bar.addWidget(self._tool_btn("测连", self._ping, primary=True, min_w=52))
        self.chk_open_html = QCheckBox("打开HTML")
        self.chk_open_html.setChecked(bool(self.cfg.get("open_html", False)))
        self.chk_open_html.toggled.connect(lambda *_: self._schedule_save())
        bar.addWidget(self.chk_open_html)
        # 顶栏只留 IP/PORT/超时/打开HTML；状态字/说明/回执/测连全去
        bar.addStretch(1)
        top_wrap = QFrame()
        top_wrap.setObjectName("zone")
        top_l = QVBoxLayout(top_wrap)
        top_l.setContentsMargins(10, 6, 10, 6)
        top_l.setSpacing(0)
        row = QWidget()
        row.setLayout(bar)
        top_l.addWidget(row)
        lay.addWidget(top_wrap)

        # ----- 2 位姿白卡：左坐标 · 右竖排停止监听/记录 -----
        pose_box = QFrame()
        pose_box.setObjectName("zone")
        pose_outer = QHBoxLayout(pose_box)
        pose_outer.setContentsMargins(10, 8, 10, 8)
        pose_outer.setSpacing(10)

        pose_left = QVBoxLayout()
        pose_left.setSpacing(4)
        cart = QHBoxLayout()
        cart.addWidget(QLabel("笛卡尔"))
        self.lbl_xyz = []
        self.lbl_rpy = []
        for name, unit, bucket in (
            ("X", "mm", self.lbl_xyz),
            ("Y", "mm", self.lbl_xyz),
            ("Z", "mm", self.lbl_xyz),
            ("Rx", "°", self.lbl_rpy),
            ("Ry", "°", self.lbl_rpy),
            ("Rz", "°", self.lbl_rpy),
        ):
            cart.addWidget(QLabel(name))
            lab = QLabel("—")
            lab.setFont(NUM_FONT)
            lab.setMinimumWidth(72)
            lab.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
            bucket.append(lab)
            cart.addWidget(lab)
            u = QLabel(unit)
            u.setObjectName("muted")
            cart.addWidget(u)
        cart.addStretch(1)
        pose_left.addLayout(cart)

        joint = QHBoxLayout()
        joint.addWidget(QLabel("关节"))
        self.lbl_j = []
        for i in range(6):
            joint.addWidget(QLabel("J%d" % (i + 1)))
            lab = QLabel("—")
            lab.setFont(NUM_FONT)
            lab.setMinimumWidth(64)
            lab.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
            self.lbl_j.append(lab)
            joint.addWidget(lab)
            u = QLabel("°")
            u.setObjectName("muted")
            joint.addWidget(u)
        joint.addStretch(1)
        pose_left.addLayout(joint)
        self.lbl_rec = QLabel("")
        self.lbl_rec.setStyleSheet("color:#2563EB;")
        pose_left.addWidget(self.lbl_rec)
        pose_outer.addLayout(pose_left, 1)

        pose_right = QVBoxLayout()
        pose_right.setSpacing(6)
        pose_right.setContentsMargins(0, 0, 0, 0)
        self.btn_listen = self._tool_btn("监听", self._toggle_listen, success=True, min_w=88)
        self.btn_listen.setFixedHeight(29)
        self.btn_listen.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        pose_right.addWidget(self.btn_listen)
        self.btn_record = self._tool_btn("记录", self._toggle_record, primary=True, min_w=88)
        self.btn_record.setFixedHeight(29)
        self.btn_record.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        pose_right.addWidget(self.btn_record)
        pose_right.addStretch(1)
        pose_outer.addLayout(pose_right)
        lay.addWidget(pose_box)

        # ----- 3 中区三栏：左列表 | 编辑 | 右操作 -----
        self.splitter = QSplitter(Qt.Horizontal)
        splitter = self.splitter
        splitter.setChildrenCollapsible(False)
        splitter.setHandleWidth(0)

        # 左：脚本列表 200～220
        left = QFrame()
        left.setObjectName("zone")
        left_l = QVBoxLayout(left)
        left_l.setContentsMargins(6, 6, 6, 6)
        left_l.setSpacing(4)
        files_bar = QHBoxLayout()
        files_bar.setSpacing(4)
        files_bar.addWidget(QLabel("脚本"))
        files_bar.addStretch(1)
        files_bar.addWidget(self._tool_btn("刷新", lambda: self._refresh(True), min_w=48))
        files_bar.addWidget(self._tool_btn("目录", self._pick_dir, min_w=48))
        left_l.addLayout(files_bar)
        self.lbl_dir = QLabel("")
        self.lbl_dir.setObjectName("muted")
        self.lbl_dir.setStyleSheet("font-size:7pt;")
        left_l.addWidget(self.lbl_dir)
        self.lst = QListWidget()
        self.lst.setFont(QFont("Consolas", 8))
        self.lst.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.lst.currentRowChanged.connect(self._on_select)
        left_l.addWidget(self.lst, 1)
        left.setMinimumWidth(160)
        left.setMaximumWidth(220)
        splitter.addWidget(left)

        # 中：编辑器
        editor = QWidget()
        self._editor_frame = editor
        editor.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        editor.setMinimumWidth(240)
        ed_l = QVBoxLayout(editor)
        ed_l.setContentsMargins(0, 0, 0, 0)
        ed_l.setSpacing(4)
        # 顶栏：文件名左 · 下发脚本（主蓝）→ 另存为（次）右对齐；无底栏组合钮
        ed_top = QHBoxLayout()
        ed_top.setSpacing(8)
        self.lbl_filename = QLabel("未命名")
        ed_top.addWidget(self.lbl_filename, 1)
        ed_top.addWidget(self._tool_btn("下发脚本", self._send, primary=True, min_w=80))
        ed_top.addWidget(self._tool_btn("另存为", self._save_as, min_w=72))
        ed_l.addLayout(ed_top)
        self.txt = QPlainTextEdit()
        self.txt.setFont(QFont("Consolas", 9))
        self.txt.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.txt.setMinimumWidth(160)
        self.txt.setLineWrapMode(QPlainTextEdit.NoWrap)
        self.txt.setHorizontalScrollBarPolicy(Qt.ScrollBarAsNeeded)
        self.txt.setVerticalScrollBarPolicy(Qt.ScrollBarAsNeeded)
        self.txt.setPlainText(self.cfg.get("script_text") or SAMPLE)
        self.txt.textChanged.connect(self._schedule_save)
        ed_l.addWidget(self.txt, 1)
        self._editor_zone = editor  # pack with right below

        # 右：无滚动条；组内≥6、组间≥10；急停钉底；小窗靠缩钮高
        right = QWidget()
        right.setMinimumWidth(128)
        right.setMaximumWidth(140)
        right_l = QVBoxLayout(right)
        right_l.setContentsMargins(0, 0, 0, 0)
        right_l.setSpacing(10)

        def _sec_title(text):
            lab = QLabel(text)
            lab.setStyleSheet("color:#6B7280;font-size:12px;")
            return lab

        self._right_btns = []

        def _add_btns(items):
            box = QVBoxLayout()
            box.setSpacing(6)
            box.setContentsMargins(0, 0, 0, 0)
            for text, slot in items:
                btn = self._tool_btn(text, slot)
                btn.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
                btn.setMinimumHeight(26)
                self._right_btns.append(btn)
                box.addWidget(btn)
            return box

        right_l.addWidget(_sec_title("电源"))
        right_l.addLayout(_add_btns((
            ("上电", self._power_on),
            ("下电", self._power_off),
            ("松闸", self._brake),
        )))
        right_l.addWidget(_sec_title("运动"))
        right_l.addLayout(_add_btns((
            ("运行", self._play),
            ("停止", self._stop_task),
            ("查询", self._task),
        )))
        right_l.addLayout(_add_btns((
            ("清弹窗", self._close_popup),
        )))
        right_l.addStretch(1)
        sep = QFrame()
        sep.setFrameShape(QFrame.HLine)
        sep.setFixedHeight(1)
        sep.setStyleSheet("background:#E5E7EB;border:none;max-height:1px;")
        right_l.addWidget(sep)
        estop = self._tool_btn("急停", self._estop, danger=True)
        estop.setMinimumHeight(35)
        estop.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self._right_estop = estop
        self._right_btns.append(estop)
        right_l.addWidget(estop)
        mid_wrap = QFrame()
        mid_wrap.setObjectName("zone")
        mid_l = QHBoxLayout(mid_wrap)
        mid_l.setContentsMargins(6, 6, 6, 6)
        mid_l.setSpacing(0)
        mid_l.addWidget(self._editor_zone, 1)
        mid_l.addWidget(right, 0)
        right.setFixedWidth(128)
        splitter.addWidget(mid_wrap)

        splitter.setStretchFactor(0, 0)
        splitter.setStretchFactor(1, 1)
        splitter.setSizes([210, 900, 134])
        lay.addWidget(splitter, 1)

        # ----- 4 底日志 -----
        self.tabs = QTabWidget()
        self.tabs.setMinimumHeight(120)
        self.tabs.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Preferred)
        self.log = QPlainTextEdit()
        self.log.setObjectName("log")
        self.log.setReadOnly(True)
        self.log.setFont(QFont("Microsoft YaHei", 10))
        self.tabs.addTab(self.log, "日志")

        hist = QWidget()
        hist_l = QVBoxLayout(hist)
        hist_l.setContentsMargins(2, 2, 2, 2)
        self.hist_list = QListWidget()
        self.hist_list.itemDoubleClicked.connect(lambda *_: self._history_fill())
        hist_l.addWidget(self.hist_list, 1)
        hbar = QHBoxLayout()
        hbar.addWidget(QLabel("29999"))
        self.ed_dash = QLineEdit(str(self.cfg.get("dashboard") or "task -r"))
        self.ed_dash.returnPressed.connect(self._dashboard)
        self.ed_dash.textChanged.connect(lambda *_: self._schedule_save())
        hbar.addWidget(self.ed_dash, 1)
        hbar.addWidget(self._tool_btn("发送", self._dashboard, min_w=48))
        hbar.addWidget(self._tool_btn("填入", self._history_fill, min_w=48))
        hbar.addWidget(self._tool_btn("重发", self._history_resend, min_w=48))
        hbar.addWidget(self._tool_btn("清空", self._history_clear, min_w=48))
        hist_l.addLayout(hbar)
        self.tabs.addTab(hist, "历史")
        lay.addWidget(self.tabs)

        self._sync_pose_buttons()

    def _set_tip(self, text):
        # 顶栏说明已去，仅保留日志侧提示
        return

    def _set_status(self, text, kind="info"):
        colors = {"ok": "#15803D", "err": "#DC2626", "info": "#6B7280", "busy": "#2563EB"}
        if hasattr(self, "lbl_status"):
            self.lbl_status.setText(text)
        if hasattr(self, "lbl_status"):
            self.lbl_status.setStyleSheet("color:%s;" % colors.get(kind, "#6B7280"))

    def _history_item(self):
        row = self.hist_list.currentRow()
        if row < 0:
            return None
        real = len(self.history) - 1 - row
        if real < 0 or real >= len(self.history):
            return None
        return self.history[real]

    def _refresh_history_list(self):
        self.hist_list.clear()
        for item in reversed(self.history):
            mark = "OK" if item.get("ok", True) else "NG"
            self.hist_list.addItem("%s  [%s]  %s" % (item.get("ts", ""), mark, item.get("title", "")))
        if self.history:
            self.hist_list.setCurrentRow(0)

    def _history_fill(self):
        item = self._history_item()
        if item is None:
            return
        cmd = (item.get("cmd") or "").strip()
        if cmd in ("halt + stop", "boot_and_send") or cmd.startswith("send "):
            return
        self.ed_dash.setText(cmd)
        self._schedule_save()

    def _history_resend(self):
        item = self._history_item()
        if item is None:
            return
        title = item.get("title") or ""
        cmd = (item.get("cmd") or "").strip()
        if title == "下发" or cmd.startswith("send "):
            self._send()
        elif title == "急停":
            self._estop()
        elif title == "上电+松闸+下发":
            self._boot_send()
        elif cmd:
            self.ed_dash.setText(cmd)
            self._dashboard()

    def _history_clear(self):
        self.history = []
        self._refresh_history_list()
        self._schedule_save()

    def _record_history(self, title, cmd, result_text, ok=True):
        self.history.append(
            {
                "ts": time.strftime("%H:%M:%S"),
                "title": title,
                "cmd": cmd or title,
                "result": (result_text or "").strip()[:800],
                "ok": bool(ok),
            }
        )
        self.history = self.history[-80:]
        self._refresh_history_list()
        self._schedule_save()

    def _collect_settings(self):
        last_file = self.current_name or ""
        item = self.lst.currentItem()
        if item is not None:
            text = item.text()
            if not text.startswith("（"):
                last_file = text.split("  (")[0]
        elif not last_file and self.cfg.get("last_file"):
            last_file = self.cfg.get("last_file")
        try:
            port = int(self.ed_port.text())
        except ValueError:
            port = DEFAULT_PORT
        try:
            timeout = float(self.ed_timeout.text())
        except ValueError:
            timeout = DEFAULT_TIMEOUT
        geo = "%dx%d" % (self.width(), self.height())
        return {
            "host": self.cmb_host.currentText().strip(),
            "host_history": list(self.host_history),
            "port": port,
            "timeout": timeout,
            "read_reply": False,
            "script_dir": str(self.script_dir),
            "last_file": last_file,
            "script_text": self.txt.toPlainText().rstrip("\n"),
            "dashboard": self.ed_dash.text().strip(),
            "geometry": geo,
            "command_history": self.history[-80:],
            "open_html": bool(self.chk_open_html.isChecked()),
        }

    def _schedule_save(self):
        if self._loading:
            return
        self._save_timer.start(400)

    def _save_now(self):
        data = self._collect_settings()
        self.cfg = data
        save_settings(data)


    def showEvent(self, event):
        super().showEvent(event)
        QTimer.singleShot(0, self._fill_splitter)
        QTimer.singleShot(50, self._fill_splitter)

        self._fit_right_btns()

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._fill_splitter()

        self._fit_right_btns()

    def _fill_splitter(self):
        """两栏：左列表 |（编辑+右栏零间距）。"""
        sp = getattr(self, "splitter", None)
        if sp is None:
            return
        total = max(sp.width(), 1)
        handles = max(sp.handleWidth(), 0)
        left_w = 210 if total >= 900 else (180 if total >= 720 else 160)
        mid = max(total - left_w - handles, 260)
        used = left_w + mid + handles
        if used < total:
            mid += total - used
        sp.setSizes([left_w, mid])


    def _fit_right_btns(self):
        """主钮常态 29；小窗 ≥26；急停固定 35。"""
        btns = getattr(self, "_right_btns", None) or []
        if not btns:
            return
        h = max(320, int(self.height()))
        if h >= 620:
            bh = 29
        elif h <= 480:
            bh = 26
        else:
            # 480→620: 26→29
            bh = int(26 + (h - 480) * (3 / 140.0))
            bh = min(29, max(26, bh))
        for b in btns:
            if b is getattr(self, "_right_estop", None):
                b.setFixedHeight(35)
            else:
                b.setFixedHeight(bh)


    def closeEvent(self, event):
        self._save_timer.stop()
        self._remember_host()
        save_settings(self._collect_settings())
        if self._recording:
            self._stop_record(open_html=False)
        self._listen_stop.set()
        self._pose_timer.stop()
        event.accept()

    # ----- pose / listen -----
    def _sync_pose_buttons(self):
        if self._listening:
            self.btn_listen.setText("停止监听")
            self.btn_listen.setObjectName("danger")
        else:
            self.btn_listen.setText("监听")
            self.btn_listen.setObjectName("success")
        if self._recording:
            self.btn_record.setText("停止记录")
            self.btn_record.setObjectName("danger")
        else:
            self.btn_record.setText("记录")
            self.btn_record.setObjectName("primary")
        # force style refresh after objectName change
        self.btn_listen.style().unpolish(self.btn_listen)
        self.btn_listen.style().polish(self.btn_listen)
        self.btn_record.style().unpolish(self.btn_record)
        self.btn_record.style().polish(self.btn_record)
        self.btn_listen.setFixedHeight(BTN_MIN_H)
        self.btn_record.setFixedHeight(BTN_MIN_H)

    def _toggle_listen(self):
        if self._listening:
            self._stop_listen()
        else:
            self._start_listen()

    def _toggle_record(self):
        if self._recording:
            self._stop_record()
        else:
            self._start_record()

    def _start_listen(self):
        self._listen_stop.set()
        self._listen_stop = threading.Event()
        self._listening = True
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setText("正在连接…")
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setStyleSheet("color:#2563EB;")
        self._sync_pose_buttons()
        self._listen_thread = threading.Thread(target=self._listen_worker, daemon=True)
        self._listen_thread.start()

    def _stop_listen(self):
        self._listen_stop.set()
        self._listening = False
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setText("已停止")
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setStyleSheet("color:#6B7280;")
        self._sync_pose_buttons()

    def _on_listen_status(self, text, kind):
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setText(text[:72])
        colors = {"ok": "#15803D", "err": "#DC2626", "info": "#6B7280", "busy": "#B45309"}
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setStyleSheet("color:%s;" % colors.get(kind, "#6B7280"))

    def _listen_worker(self):
        stop = self._listen_stop
        stream = RobotStateStream()
        while not stop.is_set():
            sock = None
            try:
                host, port, _timeout = self._params()
                sock = socket.create_connection((host, port), timeout=3)
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                sock.settimeout(1.0)
                stream.reset()
                self._bridge.listen_status.emit("监听中  %s:%s" % (host, port), "ok")
                while not stop.is_set():
                    try:
                        chunk = sock.recv(8192)
                    except socket.timeout:
                        continue
                    if not chunk:
                        raise socket.error("连接已关闭")
                    poses = stream.feed(chunk)
                    if poses:
                        self._latest_pose = poses[-1]
                        if self._recording:
                            now = time.time()
                            with self._rec_lock:
                                t0 = self._rec_t0
                                if t0 is not None and len(self._rec_samples) < 500000:
                                    for parsed in poses:
                                        self._rec_samples.append(pose_sample(parsed, t0, now))
            except Exception as exc:
                if stop.is_set():
                    break
                self._bridge.listen_status.emit("断开，重连中  %s" % exc, "err")
                stop.wait(1.5)
            finally:
                if sock is not None:
                    try:
                        sock.close()
                    except Exception:
                        pass

    def _poll_pose(self):
        parsed = self._latest_pose
        if parsed:
            shown = format_pose(parsed)
            joints = shown.get("joints_deg")
            xyz = shown.get("xyz_mm")
            rpy = shown.get("rpy_deg")
            if joints:
                for i, val in enumerate(joints):
                    self.lbl_j[i].setText("%.3f" % val)
            if xyz:
                for i, val in enumerate(xyz):
                    self.lbl_xyz[i].setText("%.2f" % val)
            if rpy:
                for i, val in enumerate(rpy):
                    self.lbl_rpy[i].setText("%.3f" % val)
        if self._recording:
            with self._rec_lock:
                n = len(self._rec_samples)
            self.lbl_rec.setText("记录中  %d 点" % n)

    def _start_record(self):
        if self._recording:
            return
        if self._listen_stop.is_set():
            self._start_listen()
        with self._rec_lock:
            self._rec_samples = []
            self._rec_t0 = time.time()
        self._recording = True
        self.lbl_rec.setText("记录中  0 点")
        self.lbl_rec.setStyleSheet("color:#2563EB;")
        self._sync_pose_buttons()
        self._append("开始记录 TCP / 关节")

    def _stop_record(self, open_html=None):
        if not self._recording and not self._rec_samples:
            return
        self._recording = False
        self._sync_pose_buttons()
        with self._rec_lock:
            samples = list(self._rec_samples)
            self._rec_t0 = None
        if not samples:
            self.lbl_rec.setText("未采到点")
            self._append("记录停止：没有数据", "err")
            return
        stamp = time.strftime("%Y%m%d_%H%M%S")
        folder = app_dir() / "traces"
        try:
            csv_path = write_csv(folder / ("traj_%s.csv" % stamp), samples)
            html_path = write_html(folder / ("traj_%s.html" % stamp), samples)
        except OSError as exc:
            self._append("保存轨迹失败: %s" % exc, "err")
            return
        self.lbl_rec.setText("已保存 %d 点" % len(samples))
        self.lbl_rec.setStyleSheet("color:#15803D;")
        self._append("轨迹 CSV   %s" % csv_path, "ok")
        self._append("轨迹 HTML  %s" % html_path, "ok")
        if open_html is None:
            open_html = bool(self.chk_open_html.isChecked())
        if open_html:
            try:
                webbrowser.open(html_path.resolve().as_uri())
            except Exception:
                pass

    # ----- files / editor -----
    def _append(self, line, kind="info"):
        colors = {"ok": "#15803D", "err": "#DC2626", "busy": "#2563EB", "info": "#1F2937"}
        fmt = QTextCharFormat()
        fmt.setForeground(QColor(colors.get(kind, "#111111")))
        cur = self.log.textCursor()
        cur.movePosition(QTextCursor.End)
        cur.insertText(line + "\n", fmt)
        self.log.setTextCursor(cur)
        self.log.ensureCursorVisible()
        self._set_status(line.split("\n")[0][:80], kind)

    def _refresh(self, log=True):
        path = Path(self.script_dir)
        try:
            path = path.expanduser()
        except OSError:
            pass
        self.script_dir = path
        self._dir_full = str(path)
        self.lbl_dir.setText(self._ellipsis_path(self._dir_full))
        self.lbl_dir.setToolTip(self._dir_full)
        self.lst.clear()

        if not path.exists():
            self.lst.addItem("（路径不存在）")
            if log:
                self._append("脚本目录不存在：%s" % path, "err")
            return
        if not path.is_dir():
            self.lst.addItem("（不是文件夹）")
            if log:
                self._append("脚本路径不是文件夹：%s" % path, "err")
            return
        try:
            files = list_script_files(path)
        except PermissionError:
            self.lst.addItem("（无权限）")
            if log:
                self._append("无法读取脚本目录（无权限）：%s" % path, "err")
            return
        except OSError as exc:
            self.lst.addItem("（读取失败）")
            if log:
                self._append("读取脚本目录失败：%s" % exc, "err")
            return

        if not files:
            self.lst.addItem("（无脚本）")
            if log:
                self._append("目录 %s  无脚本（支持 .script/.prg/.txt）" % path)
            return

        for p in files:
            try:
                size = p.stat().st_size
            except OSError:
                size = 0
            self.lst.addItem("%s  (%d B)" % (p.name, size))
        if log:
            self._append("目录 %s  共 %d 个脚本" % (path, len(files)))

    def _pick_dir(self):
        initial = str(self.script_dir) if Path(self.script_dir).exists() else str(app_dir())
        chosen = QFileDialog.getExistingDirectory(self, "选择脚本目录", initial)
        if chosen:
            self.script_dir = Path(chosen)
            self._refresh()
            self._schedule_save()

    def _on_select(self, row):
        if row < 0:
            return
        item = self.lst.item(row)
        if item is None:
            return
        text = item.text()
        if text.startswith("（"):
            return
        name = text.split("  (")[0]
        try:
            content = _read_file(self.script_dir / name)
        except OSError as exc:
            QMessageBox.critical(self, "读取失败", str(exc))
            return
        self.txt.setPlainText(content)
        self._set_filename(name)
        self._schedule_save()

    def _save_as(self):
        initial = self.current_name or "untitled.script"
        if Path(initial).suffix.lower() not in (".script", ".prg", ".txt"):
            initial = initial + ".script"
        initialdir = str(self.script_dir) if Path(self.script_dir).is_dir() else str(app_dir() / "scripts")
        chosen, _ = QFileDialog.getSaveFileName(
            self,
            "另存为",
            str(Path(initialdir) / initial),
            "Elite 脚本 (*.script);;文本 (*.txt);;PRG (*.prg);;所有文件 (*.*)",
        )
        if not chosen:
            return
        path = Path(chosen)
        content = self.txt.toPlainText().rstrip("\n") + "\n"
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        except OSError as exc:
            QMessageBox.critical(self, "另存为失败", str(exc))
            return
        self.script_dir = path.parent
        self._set_filename(path.name)
        self._refresh(log=False)
        for i in range(self.lst.count()):
            item = self.lst.item(i)
            if item.text().startswith("（"):
                continue
            if item.text().split("  (")[0] == path.name:
                self.lst.setCurrentRow(i)
                break
        self._append("已另存为 %s" % path, "ok")
        self._schedule_save()

    def _restore_last_file(self):
        name = self.cfg.get("last_file") or ""
        if not name:
            self._set_filename("")
            return
        path = self.script_dir / name
        for i in range(self.lst.count()):
            item = self.lst.item(i)
            if item.text().startswith("（"):
                continue
            if item.text().split("  (")[0] == name:
                self.lst.setCurrentRow(i)
                break
        if path.is_file():
            self._set_filename(name)
            if not self.cfg.get("script_text"):
                try:
                    self.txt.setPlainText(_read_file(path))
                except OSError:
                    pass
        else:
            self._set_filename("")

    # ----- background commands -----
    def _params(self):
        host = self.cmb_host.currentText().strip()
        # remember on UI thread later
        QTimer.singleShot(0, lambda h=host: self._remember_host(h))
        return host, int(self.ed_port.text()), float(self.ed_timeout.text())

    def _run_bg(self, title, fn, force=False):
        if self.busy and not force:
            return
        if not force:
            self.busy = True
            self.stop_event.clear()
            self._set_status(title + "…", "busy")
        else:
            self._set_status(title + "…", "err" if title == "急停" else "busy")

        def work():
            try:
                result = fn()
                self._bridge.work_done.emit(title, result, None, force)
            except Exception:
                err = traceback.format_exc()
                self._bridge.work_done.emit(title, None, err, force)

        threading.Thread(target=work, daemon=True).start()

    def _cancel_wait(self):
        self.stop_event.set()
        self._append("已取消等待（松闸/下电循环会退出）")

    def _done(self, title, result, err, force=False):
        if not force:
            self.busy = False
        if err:
            self._append("%s 失败\n%s" % (title, err.strip()), "err")
            self._record_history(title, title, err.strip(), ok=False)
            QMessageBox.critical(self, title, err.splitlines()[-1])
            return
        if isinstance(result, dict):
            msg = result.get("message")
            extra = result.get("reply") or ""
            kind = "err" if result.get("ok") is False else "ok"
            if msg:
                extra_bit = extra if extra and extra != msg else ""
                line = "%s  %s  %s" % (title, msg, extra_bit)
                self._append(line, kind)
            else:
                line = "%s 成功  %s bytes  %sms  %s:%s  %s" % (
                    title,
                    result.get("bytes_sent", "-"),
                    result.get("duration_ms", "-"),
                    result.get("host"),
                    result.get("port"),
                    extra,
                )
                self._append(line, kind)
            cmd = TITLE_CMDS.get(title, title)
            if title.startswith("Dashboard"):
                cmd = title.replace("Dashboard ", "", 1)
            elif title == "下发":
                cmd = "send 30001 (%s bytes)" % result.get("bytes_sent", "")
            elif title == "急停":
                cmd = "halt + stop"
            elif title == "上电+松闸+下发":
                cmd = "boot_and_send"
            self._record_history(title, cmd, line, ok=(result.get("ok") is not False))
            if result.get("ok") is False:
                QMessageBox.warning(self, title, msg or extra or "失败")
        else:
            self._append("%s  %s" % (title, result), "ok")
            self._record_history(title, title, str(result), ok=True)

    def _ping(self):
        def fn():
            host, port, timeout = self._params()
            with socket.create_connection((host, port), timeout=timeout):
                pass
            return "已连通 %s:%s" % (host, port)

        self._set_status("正在连接…", "busy")
        self._run_bg("测试连接", fn)

    def _send(self):
        text = self.txt.toPlainText().rstrip() + "\n"

        def fn():
            host, port, timeout = self._params()
            return send_text(
                text, host=host, port=port, timeout=timeout, read_reply=False
            )

        self._run_bg("下发", fn)

    def _power_on(self):
        def fn():
            host, _port, timeout = self._params()
            return powering_on(host=host, timeout=timeout)

        self._run_bg("上电", fn)

    def _power_off(self):
        def fn():
            host, _port, timeout = self._params()
            return powering_off(host=host, timeout=timeout, stop_event=self.stop_event)

        self._run_bg("下电", fn)

    def _brake(self):
        def fn():
            host, _port, timeout = self._params()
            return brake_releasing(host=host, timeout=timeout, stop_event=self.stop_event)

        self._run_bg("松闸", fn)

    def _play(self):
        def fn():
            host, _port, timeout = self._params()
            return play(host=host, timeout=timeout)

        self._run_bg("运行", fn)

    def _stop_task(self):
        def fn():
            host, _port, timeout = self._params()
            return stop_task(host=host, timeout=timeout)

        self._run_bg("停止工程", fn, force=True)

    def _estop(self):
        self.stop_event.set()

        def fn():
            host, _port, timeout = self._params()
            return emergency_stop(host=host, timeout=timeout)

        self._run_bg("急停", fn, force=True)

    def _task(self):
        def fn():
            host, _port, timeout = self._params()
            return task(host=host, timeout=timeout)

        self._run_bg("查询任务", fn)

    def _close_popup(self):
        def fn():
            host, _port, timeout = self._params()
            return close_popup(host=host, timeout=timeout)

        self._run_bg("清除弹窗", fn)

    def _boot_send(self):
        text = self.txt.toPlainText().rstrip() + "\n"

        def fn():
            host, _port, timeout = self._params()
            return boot_and_send(
                text,
                host=host,
                timeout=timeout,
                stop_event=self.stop_event,
                read_reply=False,
            )

        self._run_bg("上电+松闸+下发", fn)

    def _dashboard(self):
        cmd = self.ed_dash.text().strip()
        if not cmd:
            return

        def fn():
            host, _port, timeout = self._params()
            return dashboard_cmd(cmd, host=host, timeout=timeout)

        self._run_bg("Dashboard " + cmd, fn)


def main(argv=None):
    saved = load_settings()
    parser = argparse.ArgumentParser(description="Elite 30001 脚本下发测试 (Qt)")
    parser.add_argument("--host", default=saved.get("host") or DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=int(saved.get("port") or DEFAULT_PORT))
    parser.add_argument("--timeout", type=float, default=float(saved.get("timeout") or DEFAULT_TIMEOUT))
    parser.add_argument("--file", help="脚本文件路径")
    parser.add_argument("--stdin", action="store_true")
    parser.add_argument("--read-reply", action="store_true")
    parser.add_argument("--dashboard", help="29999 命令，例如 task -r")
    parser.add_argument("--power-on", action="store_true")
    parser.add_argument("--power-off", action="store_true")
    parser.add_argument("--close-popup", action="store_true")
    parser.add_argument("--stop-task", action="store_true")
    parser.add_argument("--estop", action="store_true")
    parser.add_argument("--brake", action="store_true")
    parser.add_argument("--play", action="store_true")
    parser.add_argument("--task", action="store_true")
    parser.add_argument("--boot-send", action="store_true")
    parser.add_argument("--cli", action="store_true")
    args = parser.parse_args(argv)

    if (
        args.cli
        or args.file
        or args.stdin
        or args.dashboard
        or args.power_on
        or args.power_off
        or args.close_popup
        or args.stop_task
        or args.estop
        or args.brake
        or args.play
        or args.task
        or args.boot_send
    ):
        return run_cli(args)

    # High-DPI: Qt6 handles by default; ensure AA attributes for older bindings
    QApplication.setHighDpiScaleFactorRoundingPolicy(
        Qt.HighDpiScaleFactorRoundingPolicy.PassThrough
    )
    app = QApplication(sys.argv)
    app.setFont(UI_FONT)
    win = EliteScriptWindow()
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())

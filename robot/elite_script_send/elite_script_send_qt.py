#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Elite 脚本下发 — PySide6 界面（复用 sender / state_listener / trajectory）。"""
from __future__ import annotations

import argparse
import json
import math
import os
import socket
import sys
import threading
import time
import traceback
import webbrowser
from pathlib import Path

from PySide6.QtCore import QByteArray, QObject, QPointF, QSize, Qt, QTimer, Signal
from PySide6.QtGui import QAction, QColor, QFont, QPainter, QPen, QPolygonF, QTextCharFormat, QTextCursor
from PySide6.QtWidgets import (
    QApplication,
    QCheckBox,
    QComboBox,
    QDockWidget,
    QDoubleSpinBox,
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
from trajectory import keep_sample, pose_sample, tcp_xyz, write_csv, write_html

# Settings / sample helpers inlined — avoid importing Tk legacy (elite_script_send)
# which pulls tkinter into the onefile and slows cold extract.
SETTINGS_PATH = app_dir() / "last_settings.json"
HOST_HISTORY_MAX = 20
SAMPLE = """def elite_script_send_probe():
    popup(s="elite_script_send ok", blocking=False)
end
elite_script_send_probe()
"""
TITLE_CMDS = {
    "上电": "robotControl -on",
    "下电": "robotControl -off",
    "松闸": "brakeRelease",
    "运行": "play",
    "停止工程": "stop",
    "查询任务": "task -r",
    "清除弹窗": "popup -c",
    "测试连接": "echo",
}


def load_settings():
    try:
        data = json.loads(SETTINGS_PATH.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            return data
    except (OSError, ValueError):
        pass
    return {}


def save_settings(data):
    try:
        SETTINGS_PATH.write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    except OSError:
        pass


def _read_file(path):
    p = Path(path)
    try:
        return p.read_text(encoding="utf-8")
    except OSError:
        return SAMPLE

BTN_MIN_H = 29
NUM_FONT = QFont("Consolas", 10)
UI_FONT = QFont("Microsoft YaHei", 9)


def _nice_step(span):
    raw = max(span, 1.0) / 5.0
    mag = 10 ** math.floor(math.log10(raw))
    nrm = raw / mag
    if nrm < 1.5:
        return mag
    if nrm < 3.5:
        return 2 * mag
    if nrm < 7.5:
        return 5 * mag
    return 10 * mag


class LiveTrajectoryView(QWidget):
    """记录过程中的 TCP 三维折线。投影和离线 HTML 一致：拖动旋转，滚轮缩放。"""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._pts = []
        self._total = 0
        self.yaw = 0.75
        self.pitch = 0.5
        self.dist = 1.0
        self.show_axes = False
        self._drag = None
        self.setMinimumHeight(140)
        self.setMouseTracking(True)
        self.setFocusPolicy(Qt.WheelFocus)
        self.setCursor(Qt.OpenHandCursor)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)

    def set_axes(self, on):
        self.show_axes = bool(on)
        self.update()

    def set_points(self, pts, total=None):
        self._pts = pts
        self._total = len(pts) if total is None else int(total)
        self.update()

    def clear_path(self):
        self.set_points([], 0)

    def _bounds(self, include_origin):
        pts = self._pts
        if not pts:
            return (0.0, 0.0, 0.0), 1.0
        minx = maxx = pts[0][0]
        miny = maxy = pts[0][1]
        minz = maxz = pts[0][2]
        for x, y, z in pts:
            if x < minx:
                minx = x
            elif x > maxx:
                maxx = x
            if y < miny:
                miny = y
            elif y > maxy:
                maxy = y
            if z < minz:
                minz = z
            elif z > maxz:
                maxz = z
        if include_origin:
            minx = min(minx, 0.0)
            miny = min(miny, 0.0)
            minz = min(minz, 0.0)
            maxx = max(maxx, 0.0)
            maxy = max(maxy, 0.0)
            maxz = max(maxz, 0.0)
        cx = (minx + maxx) / 2.0
        cy = (miny + maxy) / 2.0
        cz = (minz + maxz) / 2.0
        radius = max(
            1.0,
            abs(minx - cx), abs(maxx - cx),
            abs(miny - cy), abs(maxy - cy),
            abs(minz - cz), abs(maxz - cz),
        )
        return (cx, cy, cz), radius

    def _project(self, x, y, z, center, radius, width, height):
        x -= center[0]
        y -= center[1]
        z -= center[2]
        cy = math.cos(self.yaw)
        sy = math.sin(self.yaw)
        cp = math.cos(self.pitch)
        sp = math.sin(self.pitch)
        x1 = x * cy - y * sy
        y1 = x * sy + y * cy
        z2 = y1 * sp + z * cp
        scale = min(width, height) * 0.86 / (radius * self.dist * 2.0)
        return width / 2.0 + x1 * scale, height / 2.0 - z2 * scale

    def _draw_axes(self, painter, center, radius, width, height):
        step = _nice_step(radius * 2.0)
        reach = math.ceil((radius * 1.15) / step) * step
        colors = (QColor("#e53935"), QColor("#43a047"), QColor("#1e88e5"))
        names = ("X", "Y", "Z")
        painter.setFont(QFont("Consolas", 9))
        for axis in range(3):
            neg = [0.0, 0.0, 0.0]
            pos = [0.0, 0.0, 0.0]
            neg[axis] = -reach
            pos[axis] = reach
            ax, ay = self._project(neg[0], neg[1], neg[2], center, radius, width, height)
            bx, by = self._project(pos[0], pos[1], pos[2], center, radius, width, height)
            pen = QPen(colors[axis])
            pen.setWidthF(1.6)
            painter.setPen(pen)
            painter.drawLine(QPointF(ax, ay), QPointF(bx, by))
            painter.drawText(QPointF(bx + 6, by - 4), names[axis])
            tick = -reach
            while tick <= reach + 1e-6:
                if abs(tick) > 1e-9:
                    t0 = [0.0, 0.0, 0.0]
                    t1 = [0.0, 0.0, 0.0]
                    t0[axis] = tick
                    t1[axis] = tick
                    side = (axis + 1) % 3
                    t0[side] = -step * 0.08
                    t1[side] = step * 0.08
                    ux, uy = self._project(t0[0], t0[1], t0[2], center, radius, width, height)
                    wx, wy = self._project(t1[0], t1[1], t1[2], center, radius, width, height)
                    painter.drawLine(QPointF(ux, uy), QPointF(wx, wy))
                    painter.drawText(QPointF(wx + 3, wy + 3), str(int(round(tick))))
                tick += step
        ox, oy = self._project(0, 0, 0, center, radius, width, height)
        painter.setPen(Qt.NoPen)
        painter.setBrush(QColor("#ffffff"))
        painter.drawEllipse(QPointF(ox, oy), 3.5, 3.5)
        painter.setPen(QColor("#ffffff"))
        painter.drawText(QPointF(ox + 8, oy - 8), "O")

    def paintEvent(self, _event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing, True)
        width = self.width()
        height = self.height()
        painter.fillRect(0, 0, width, height, QColor("#111111"))
        center, radius = self._bounds(self.show_axes)
        if self.show_axes:
            self._draw_axes(painter, center, radius, width, height)
        pts = self._pts
        if not pts:
            painter.setPen(QColor("#9CA3AF"))
            painter.setFont(QFont("Microsoft YaHei", 10))
            painter.drawText(16, 28, "点「记录」后，TCP 轨迹在这里实时画出")
            painter.end()
            return
        poly = QPolygonF()
        for x, y, z in pts:
            sx, sy = self._project(x, y, z, center, radius, width, height)
            poly.append(QPointF(sx, sy))
        pen = QPen(QColor("#ffcc02"))
        pen.setWidthF(1.8)
        pen.setCosmetic(True)
        painter.setPen(pen)
        painter.setBrush(Qt.NoBrush)
        painter.drawPolyline(poly)
        painter.setPen(Qt.NoPen)
        painter.setBrush(QColor("#4caf50"))
        painter.drawEllipse(poly[0], 5, 5)
        painter.setBrush(QColor("#ff5252"))
        painter.drawEllipse(poly[-1], 5, 5)
        painter.setPen(QColor("#d1d5db"))
        painter.setFont(QFont("Microsoft YaHei", 9))
        label = "TCP  %d 点" % self._total
        if len(pts) != self._total:
            label += " · 显示 %d" % len(pts)
        painter.drawText(12, height - 10, label)
        painter.end()

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            pos = event.position()
            self._drag = (pos.x(), pos.y())
            self.setCursor(Qt.ClosedHandCursor)
        super().mousePressEvent(event)

    def mouseMoveEvent(self, event):
        if self._drag is None:
            super().mouseMoveEvent(event)
            return
        pos = event.position()
        x, y = pos.x(), pos.y()
        lx, ly = self._drag
        self.yaw += (x - lx) * 0.008
        self.pitch += (y - ly) * 0.008
        if self.pitch > 1.4:
            self.pitch = 1.4
        elif self.pitch < -1.4:
            self.pitch = -1.4
        self._drag = (x, y)
        self.update()

    def mouseReleaseEvent(self, event):
        self._drag = None
        self.setCursor(Qt.OpenHandCursor)
        super().mouseReleaseEvent(event)

    def mouseDoubleClickEvent(self, event):
        self.yaw = 0.75
        self.pitch = 0.5
        self.dist = 1.0
        self._drag = None
        self.update()
        super().mouseDoubleClickEvent(event)

    def wheelEvent(self, event):
        self.dist *= 1.08 if event.angleDelta().y() < 0 else 0.92
        if self.dist < 0.2:
            self.dist = 0.2
        elif self.dist > 8.0:
            self.dist = 8.0
        self.update()
        event.accept()


class _NoticeLabel(QLabel):
    """主界面一行反馈。单击打开日志。"""

    clicked = Signal()

    def __init__(self, text=""):
        super().__init__(text)
        self.setCursor(Qt.PointingHandCursor)
        self.setToolTip("打开日志")

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.LeftButton:
            self.clicked.emit()
        super().mouseReleaseEvent(event)


class _Bridge(QObject):
    listen_status = Signal(str, str)
    listen_connected = Signal(bool)  # True=socket ok; False=retrying/disconnected
    work_done = Signal(str, object, object, bool)  # title, result, err, force


class EliteScriptWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Elite 脚本下发测试")
        self.setMinimumSize(720, 560)
        self.cfg = load_settings()
        geo = self.cfg.get("geometry") or "900x620"
        try:
            part = str(geo).split("+")[0]
            w, h = part.lower().split("x")
            self.resize(max(720, int(w)), max(560, int(h)))
        except (TypeError, ValueError):
            self.resize(900, 640)

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
        self._listen_connected = False
        self._boot_after_show_done = False
        self._recording = False
        self._rec_lock = threading.Lock()
        self._rec_samples = []
        self._rec_t0 = None
        self._live_n = -1
        self._dir_started = False
        try:
            self._sample_step_mm = float(self.cfg.get("sample_step_mm") or 0)
        except (TypeError, ValueError):
            self._sample_step_mm = 0.0

        self._bridge = _Bridge()
        self._bridge.listen_status.connect(self._on_listen_status)
        self._bridge.listen_connected.connect(self._on_listen_connected)
        self._bridge.work_done.connect(self._done)

        self._save_timer = QTimer(self)
        self._save_timer.setSingleShot(True)
        self._save_timer.timeout.connect(self._save_now)

        self._pose_timer = QTimer(self)
        self._pose_timer.timeout.connect(self._poll_pose)

        self._build_ui()
        self._restore_last_file()
        self._refresh_history_list()
        self._loading = False
        # 启动仅自动监听（show 后异步）；禁止自动记录
        assert self._recording is False
        # listen / pose_timer 延后到 showEvent，先让顶栏可点

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
        if saved_dir:
            return Path(str(saved_dir)).expanduser()
        return Path(DEFAULT_SCRIPT_DIR)

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

    def _side_btn(self, text, tip):
        btn = QPushButton(text)
        btn.setObjectName("sideToggle")
        btn.setCheckable(True)
        btn.setFixedHeight(29)
        btn.setMinimumWidth(52)
        btn.setCursor(Qt.PointingHandCursor)
        btn.setToolTip(tip)
        return btn

    def _build_ui(self):
        # 美工定稿：页底 F5F7FA / 白卡 / 右栏操作竖条
        root = QWidget()
        self.setCentralWidget(root)
        self.setStyleSheet(
            "QWidget{background:#F5F7FA;color:#1F2937;font-family:'Microsoft YaHei';font-size:9pt;}"
            "QFrame#zone{background:#FFFFFF;border:1px solid #E5E7EB;border-radius:8px;}"
            "QPlainTextEdit,QListWidget,QLineEdit,QComboBox{"
            "background:#FFFFFF;color:#1F2937;border:1px solid #E5E7EB;border-radius:8px;padding:3px;}"
            "QTabWidget::pane{border:1px solid #E5E7EB;background:#FFFFFF;border-radius:8px;}"
            "QTabBar::tab{background:#FFFFFF;color:#6B7280;border:1px solid #E5E7EB;"
            "border-bottom:none;border-top-left-radius:6px;border-top-right-radius:6px;padding:6px 12px;}"
            "QTabBar::tab:selected{color:#2563EB;background:#EFF4FF;}"
            "QPushButton{background:#FFFFFF;color:#1F2937;border:1px solid #E5E7EB;"
            "border-radius:6px;padding:0px 10px;}"
            "QPushButton:hover{background:#EFF4FF;border-color:#2563EB;color:#2563EB;}"
            "QPushButton:disabled{background:#F3F4F6;color:#9CA3AF;border:1px solid #D1D5DB;}"
            "QPushButton#primary{background:#2563EB;color:#FFFFFF;border:1px solid #2563EB;"
            "min-height:29px;max-height:29px;}"
            "QPushButton#primary:hover{background:#1D4ED8;}"
            "QPushButton#danger{background:#DC2626;color:#FFFFFF;border:1px solid #DC2626;font-weight:bold;"
            "min-height:35px;max-height:35px;}"
            "QPushButton#danger:hover{background:#B91C1C;}"
            "QPushButton#dangerOutline{background:#FFFFFF;color:#DC2626;border:1px solid #DC2626;"
            "min-height:29px;max-height:29px;}"
            "QPushButton#dangerOutline:hover{background:#FEF2F2;color:#B91C1C;border-color:#B91C1C;}"
            "QPushButton#warn{background:#F59E0B;color:#FFFFFF;border:1px solid #D97706;"
            "min-height:29px;max-height:29px;}"
            "QPushButton#warn:hover{background:#D97706;}"
            "QPushButton#success{background:#2563EB;color:#FFFFFF;border:1px solid #2563EB;"
            "min-height:29px;max-height:29px;}"
            "QPushButton#success:hover{background:#1D4ED8;}"
            "QPushButton#sideToggle:checked{background:#EFF4FF;color:#2563EB;border:1px solid #2563EB;}"
            "QLabel#muted{color:#6B7280;}"
            "QSplitter#hSplit::handle{background:#E5E7EB;width:0px;height:0px;}"
            "QSplitter#trajSplit::handle{background:#D1D5DB;height:6px;}"
            "QPlainTextEdit#log{background:#FFFFFF;border:1px solid #E5E7EB;}"
            "QScrollBar:horizontal{height:6px;background:transparent;margin:0;}"
            "QScrollBar:horizontal:handle{background:#D1D5DB;border-radius:3px;min-width:24px;}"
            "QScrollBar:vertical{width:6px;background:transparent;margin:0;}"
            "QScrollBar:vertical:handle{background:#D1D5DB;border-radius:3px;min-height:24px;}"
        )
        lay = QVBoxLayout(root)
        lay.setContentsMargins(12, 10, 12, 10)
        lay.setSpacing(6)

        # ----- 1 顶栏 -----
        bar = QHBoxLayout()
        bar.setSpacing(6)
        bar.addWidget(QLabel("IP"))
        self.cmb_host = QComboBox()
        self.cmb_host.setEditable(True)
        self.cmb_host.setFixedWidth(118)
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
        self.ed_port.setFixedWidth(46)
        self.ed_port.textChanged.connect(lambda *_: self._schedule_save())
        bar.addWidget(self.ed_port)
        bar.addWidget(QLabel("超时"))
        self.ed_timeout = QLineEdit(str(self.cfg.get("timeout") or DEFAULT_TIMEOUT))
        self.ed_timeout.setFixedWidth(40)
        self.ed_timeout.textChanged.connect(lambda *_: self._schedule_save())
        bar.addWidget(self.ed_timeout)
        bar.addWidget(self._tool_btn("测连", self._ping, primary=True, min_w=52))
        bar.addStretch(1)
        estop_top = self._tool_btn("急停", self._estop, danger=True, min_w=96)
        estop_top.setFixedHeight(35)
        estop_top.setFixedWidth(96)
        self._right_estop = estop_top
        bar.addWidget(estop_top)
        top_wrap = QFrame()
        top_wrap.setObjectName("zone")
        top_l = QVBoxLayout(top_wrap)
        top_l.setContentsMargins(10, 6, 10, 6)
        top_l.setSpacing(0)
        row = QWidget()
        row.setLayout(bar)
        top_l.addWidget(row)
        lay.addWidget(top_wrap)

        # ----- 2 操作行 + 通知栏。坐标改到右侧竖列 -----
        act_box = QFrame()
        act_box.setObjectName("zone")
        act_l = QVBoxLayout(act_box)
        act_l.setContentsMargins(10, 6, 10, 6)
        act_l.setSpacing(4)
        pose_act = QHBoxLayout()
        pose_act.setSpacing(6)
        self.btn_listen = self._tool_btn("监听", self._toggle_listen, primary=True, min_w=72)
        self.btn_listen.setFixedHeight(29)
        self.btn_listen.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        self.btn_record = self._tool_btn("记录", self._toggle_record, primary=True, min_w=72)
        self.btn_record.setFixedHeight(29)
        self.btn_record.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        pose_act.addWidget(self.btn_listen)
        pose_act.addWidget(self.btn_record)
        self.lbl_rec = QLabel("")
        self.lbl_rec.setStyleSheet("color:#2563EB;")
        pose_act.addWidget(self.lbl_rec)
        pose_act.addStretch(1)
        self.btn_dir = self._side_btn("目录", "打开脚本目录。不点开不读取文件夹")
        self.btn_traj = self._side_btn("轨迹", "打开侧边轨迹页，可拉成独立窗口")
        self.btn_log = self._side_btn("日志", "打开日志和历史")
        pose_act.addWidget(self.btn_dir)
        pose_act.addWidget(self.btn_traj)
        pose_act.addWidget(self.btn_log)
        act_l.addLayout(pose_act)
        self.lbl_status = _NoticeLabel("就绪")
        self.lbl_status.setObjectName("muted")
        self.lbl_status.setSizePolicy(QSizePolicy.Ignored, QSizePolicy.Fixed)
        self.lbl_status.clicked.connect(self._open_log_from_notice)
        act_l.addWidget(self.lbl_status)
        lay.addWidget(act_box)

        # ----- 3 脚本编辑 | 右侧电源/运动 -----
        self.splitter = QSplitter(Qt.Horizontal)
        splitter = self.splitter
        splitter.setObjectName("hSplit")
        splitter.setChildrenCollapsible(False)
        splitter.setHandleWidth(0)

        # 目录侧边页：启动不读文件夹
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
        files_bar.addWidget(self._tool_btn("更换", self._pick_dir, min_w=48))
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
        left.setMinimumWidth(180)
        self._dir_pane = left

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
        self.btn_send = self._tool_btn("下发脚本", self._send, primary=True, min_w=80)
        self.btn_send.setFixedHeight(29)
        self.btn_send.setMinimumHeight(29)
        self.btn_send.setMaximumHeight(29)
        ed_top.addWidget(self.btn_send)
        self.btn_save_as = self._tool_btn("另存为", self._save_as, min_w=72)
        self.btn_save_as.setFixedHeight(29)
        self.btn_save_as.setMinimumHeight(29)
        self.btn_save_as.setMaximumHeight(29)
        self.btn_save_as.setStyleSheet(
            "QPushButton{background:#FFFFFF;color:#1F2937;border:1px solid #E5E7EB;"
            "border-radius:6px;padding:0px 10px;min-height:29px;max-height:29px;}"
            "QPushButton:hover{background:#EFF4FF;border-color:#2563EB;color:#2563EB;}"
        )
        ed_top.addWidget(self.btn_save_as)
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

        # 右：贴在页底上，不进脚本白卡。急停在顶栏。组内 4。
        right = QWidget()
        right.setMinimumWidth(128)
        right.setMaximumWidth(140)
        right_l = QVBoxLayout(right)
        right_l.setContentsMargins(0, 0, 0, 0)
        right_l.setSpacing(6)

        def _sec_title(text):
            lab = QLabel(text)
            lab.setStyleSheet("color:#6B7280;font-size:11px;padding:0;margin:0;")
            return lab

        self._right_btns = []

        def _add_btns(items):
            box = QVBoxLayout()
            box.setSpacing(4)
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
        right.setFixedWidth(128)
        mid_wrap = QFrame()
        mid_wrap.setObjectName("zone")
        mid_l = QVBoxLayout(mid_wrap)
        mid_l.setContentsMargins(6, 6, 6, 6)
        mid_l.setSpacing(0)
        mid_l.addWidget(self._editor_zone, 1)
        work = QWidget()
        work_l = QHBoxLayout(work)
        work_l.setContentsMargins(0, 0, 0, 0)
        work_l.setSpacing(6)
        work_l.addWidget(mid_wrap, 1)
        work_l.addWidget(right, 0)
        work_l.addWidget(self._build_pose_column(), 0)
        splitter.addWidget(work)

        splitter.setStretchFactor(0, 1)

        # ----- 3b 实时轨迹（记录中刷新；停录后仍留在画布上） -----
        traj = QFrame()
        traj.setObjectName("zone")
        traj.setMinimumHeight(150)
        traj_l = QVBoxLayout(traj)
        traj_l.setContentsMargins(8, 4, 8, 6)
        traj_l.setSpacing(4)
        traj_bar = QHBoxLayout()
        traj_bar.setSpacing(8)
        traj_bar.addWidget(QLabel("间距"))
        self.sp_step = QDoubleSpinBox()
        self.sp_step.setRange(0, 50)
        self.sp_step.setDecimals(2)
        self.sp_step.setSingleStep(0.1)
        self.sp_step.setSuffix(" mm")
        self.sp_step.setFixedWidth(88)
        self.sp_step.setValue(self._sample_step_mm)
        self.sp_step.setToolTip("采集间距。0 表示每个状态包都记；大于 0 时，相对上一个已记录点移动超过该距离才记一点。")
        self.sp_step.valueChanged.connect(self._on_sample_step)
        traj_bar.addWidget(self.sp_step)
        self.live = LiveTrajectoryView()
        self.chk_axes = QCheckBox("坐标系")
        self.chk_axes.toggled.connect(self.live.set_axes)
        traj_bar.addWidget(self.chk_axes)
        traj_bar.addStretch(1)
        self.chk_open_html = QCheckBox("打开HTML")
        self.chk_open_html.setChecked(bool(self.cfg.get("open_html", False)))
        self.chk_open_html.toggled.connect(lambda *_: self._schedule_save())
        traj_bar.addWidget(self.chk_open_html)
        self.btn_open_traces = self._tool_btn("打开文件夹", self._open_traces_folder, min_w=88)
        self.btn_open_traces.setFixedHeight(26)
        traj_bar.addWidget(self.btn_open_traces)
        self.btn_float = QPushButton("拉出")
        self.btn_float.setFixedHeight(26)
        self.btn_float.setToolTip("拉成独立窗口；再点一次贴回侧边")
        self.btn_float.clicked.connect(self._toggle_float_traj)
        traj_bar.addWidget(self.btn_float)
        traj_l.addLayout(traj_bar)
        traj_hint = QLabel("拖动旋转 · 滚轮缩放 · 双击复位")
        traj_hint.setObjectName("muted")
        traj_l.addWidget(traj_hint)
        traj_l.addWidget(self.live, 1)
        self._traj_pane = traj

        # ----- 4 底日志 -----
        self.tabs = QTabWidget()
        self.tabs.setMinimumHeight(160)
        self.tabs.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
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
        self._log_pane = self.tabs

        lay.addWidget(splitter, 1)
        self._setup_side_docks()

        self._sync_pose_buttons()

    def _pose_row(self, name, unit, bucket):
        row = QHBoxLayout()
        row.setSpacing(4)
        row.setContentsMargins(0, 0, 0, 0)
        title = QLabel(name)
        title.setFixedWidth(28)
        value = QLabel("—")
        value.setFont(NUM_FONT)
        value.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        value.setMinimumWidth(58)
        unit_lbl = QLabel(unit)
        unit_lbl.setObjectName("muted")
        unit_lbl.setFixedWidth(22)
        row.addWidget(title)
        row.addWidget(value, 1)
        row.addWidget(unit_lbl)
        bucket.append(value)
        wrap = QWidget()
        wrap.setLayout(row)
        return wrap

    def _build_pose_column(self):
        card = QFrame()
        card.setObjectName("zone")
        card.setFixedWidth(156)
        lay = QVBoxLayout(card)
        lay.setContentsMargins(8, 8, 8, 8)
        lay.setSpacing(2)
        cart_title = QLabel("笛卡尔")
        cart_title.setObjectName("muted")
        lay.addWidget(cart_title)
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
            lay.addWidget(self._pose_row(name, unit, bucket))
        joint_title = QLabel("关节")
        joint_title.setObjectName("muted")
        lay.addSpacing(8)
        lay.addWidget(joint_title)
        self.lbl_j = []
        for i in range(6):
            lay.addWidget(self._pose_row("J%d" % (i + 1), "°", self.lbl_j))
        lay.addStretch(1)
        return card

    def _setup_side_docks(self):
        feat = QDockWidget.DockWidgetFeature
        flags = feat.DockWidgetMovable | feat.DockWidgetFloatable | feat.DockWidgetClosable
        areas = Qt.LeftDockWidgetArea | Qt.RightDockWidgetArea

        self.dock_dir = QDockWidget("目录", self)
        self.dock_dir.setObjectName("dirDock")
        self.dock_dir.setAllowedAreas(areas)
        self.dock_dir.setFeatures(flags)
        self.dock_dir.setWidget(self._dir_pane)
        self.addDockWidget(Qt.LeftDockWidgetArea, self.dock_dir)
        self.dock_dir.hide()
        self.dock_dir.visibilityChanged.connect(self._on_dir_vis)
        self.btn_dir.toggled.connect(self._set_dir_open)

        self.dock_traj = QDockWidget("轨迹", self)
        self.dock_traj.setObjectName("trajDock")
        self.dock_traj.setAllowedAreas(areas)
        self.dock_traj.setFeatures(flags)
        self.dock_traj.setWidget(self._traj_pane)
        self.addDockWidget(Qt.RightDockWidgetArea, self.dock_traj)
        self.dock_traj.hide()
        self.dock_traj.visibilityChanged.connect(self._on_traj_vis)
        self.dock_traj.topLevelChanged.connect(lambda *_: self._sync_float_btn())
        self.btn_traj.toggled.connect(self._set_traj_open)

        self.dock_log = QDockWidget("日志", self)
        self.dock_log.setObjectName("logDock")
        self.dock_log.setAllowedAreas(
            Qt.BottomDockWidgetArea | Qt.LeftDockWidgetArea | Qt.RightDockWidgetArea
        )
        self.dock_log.setFeatures(flags)
        self.dock_log.setWidget(self._log_pane)
        self.addDockWidget(Qt.BottomDockWidgetArea, self.dock_log)
        self.dock_log.hide()
        self.dock_log.visibilityChanged.connect(self._on_log_vis)
        self.btn_log.toggled.connect(self._set_log_open)

    def _set_dir_open(self, on):
        if on:
            self.dock_dir.show()
            if not self.dock_dir.isFloating():
                self.resizeDocks([self.dock_dir], [220], Qt.Horizontal)
            if not self._dir_started:
                self._dir_started = True
                self._refresh(log=False)
            self.dock_dir.raise_()
        else:
            self.dock_dir.hide()

    def _on_dir_vis(self, visible):
        self.btn_dir.blockSignals(True)
        self.btn_dir.setChecked(bool(visible))
        self.btn_dir.blockSignals(False)

    def _set_traj_open(self, on):
        if on:
            self.dock_traj.show()
            if not self.dock_traj.isFloating():
                self.resizeDocks([self.dock_traj], [380], Qt.Horizontal)
            self.dock_traj.raise_()
        else:
            self.dock_traj.hide()

    def _set_log_open(self, on):
        if on:
            self.dock_log.show()
            if not self.dock_log.isFloating():
                self.resizeDocks([self.dock_log], [180], Qt.Vertical)
            self.dock_log.raise_()
        else:
            self.dock_log.hide()

    def _on_log_vis(self, visible):
        self.btn_log.blockSignals(True)
        self.btn_log.setChecked(bool(visible))
        self.btn_log.blockSignals(False)

    def _open_log_from_notice(self):
        if not self.btn_log.isChecked():
            self.btn_log.setChecked(True)
        else:
            self.dock_log.raise_()

    def _on_traj_vis(self, visible):
        self.btn_traj.blockSignals(True)
        self.btn_traj.setChecked(bool(visible))
        self.btn_traj.blockSignals(False)
        self._sync_float_btn()

    def _toggle_float_traj(self):
        floating = not self.dock_traj.isFloating()
        self.dock_traj.setFloating(floating)
        self.dock_traj.show()
        if floating:
            self.dock_traj.resize(480, 640)
        self.btn_traj.blockSignals(True)
        self.btn_traj.setChecked(True)
        self.btn_traj.blockSignals(False)
        self._sync_float_btn()

    def _sync_float_btn(self):
        floating = self.dock_traj.isFloating()
        self.btn_float.setText("贴回" if floating else "拉出")

    def _on_sample_step(self, value):
        self._sample_step_mm = float(value)
        self._schedule_save()

    def _set_tip(self, text):
        # 顶栏说明已去，仅保留日志侧提示
        return

    def _set_status(self, text, kind="info"):
        colors = {"ok": "#15803D", "err": "#DC2626", "info": "#6B7280", "busy": "#2563EB"}
        if hasattr(self, "lbl_status"):
            shown = (text or "").split("\n")[0][:80]
            self.lbl_status.setText(shown)
            self.lbl_status.setToolTip(shown)
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
            "sample_step_mm": float(self._sample_step_mm),
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
        if not self._boot_after_show_done:
            self._boot_after_show_done = True
            # 先让窗口可交互，再后台连监听
            QTimer.singleShot(0, self._boot_after_show)

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._fill_splitter()

        self._fit_right_btns()

    def _boot_after_show(self):
        """窗口已显示后再自动监听；不阻塞顶栏点击。"""
        if not self._listening and not self._recording:
            self._start_listen()
        if not self._pose_timer.isActive():
            self._pose_timer.start(100)

    def _fill_splitter(self):
        """编辑区单独占中栏；目录在侧边页里，不再挤进这条分割。"""
        return


    def _fit_right_btns(self):
        """主钮常态 29；小窗 ≥26；顶栏急停固定 35。"""
        btns = getattr(self, "_right_btns", None) or []
        h = max(320, int(self.height()))
        if h >= 620:
            bh = 29
        elif h <= 480:
            bh = 26
        else:
            bh = int(26 + (h - 480) * (3 / 140.0))
            bh = min(29, max(26, bh))
        for b in btns:
            b.setFixedHeight(bh)
        est = getattr(self, "_right_estop", None)
        if est is not None:
            est.setFixedHeight(35)


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
            if self._listen_connected:
                self.btn_listen.setText("停止监听")
                self.btn_listen.setObjectName("dangerOutline")
                self.btn_listen.setEnabled(True)
            else:
                self.btn_listen.setText("连接中…")
                self.btn_listen.setObjectName("")
                self.btn_listen.setEnabled(False)
        else:
            self.btn_listen.setText("监听")
            self.btn_listen.setObjectName("primary")
            self.btn_listen.setEnabled(True)
        if self._recording:
            self.btn_record.setText("停止记录")
            self.btn_record.setObjectName("warn")
        else:
            self.btn_record.setText("记录")
            self.btn_record.setObjectName("primary")
        # force style refresh after objectName change
        self.btn_listen.style().unpolish(self.btn_listen)
        self.btn_listen.style().polish(self.btn_listen)
        self.btn_record.style().unpolish(self.btn_record)
        self.btn_record.style().polish(self.btn_record)
        for b in (self.btn_listen, self.btn_record):
            b.setFixedHeight(BTN_MIN_H)
            b.setMinimumHeight(BTN_MIN_H)
            b.setMaximumHeight(BTN_MIN_H)

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
        self._listen_connected = False
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setText("正在连接…")
            self.lbl_listen.setStyleSheet("color:#2563EB;")
        self._sync_pose_buttons()
        self._listen_thread = threading.Thread(target=self._listen_worker, daemon=True)
        self._listen_thread.start()

    def _stop_listen(self):
        self._listen_stop.set()
        self._listening = False
        self._listen_connected = False
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setText("已停止")
        if hasattr(self, "lbl_listen"):
            self.lbl_listen.setStyleSheet("color:#6B7280;")
        self._sync_pose_buttons()

    def _on_listen_status(self, text, kind):
        self._set_status(text[:72], kind)

    def _on_listen_connected(self, ok):
        self._listen_connected = bool(ok)
        self._sync_pose_buttons()

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
                self._bridge.listen_connected.emit(True)
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
                                    step = self._sample_step_mm
                                    prev = self._rec_samples[-1] if self._rec_samples else None
                                    for parsed in poses:
                                        sample = pose_sample(parsed, t0, now)
                                        if not keep_sample(prev, sample, step):
                                            continue
                                        self._rec_samples.append(sample)
                                        prev = sample
            except Exception as exc:
                if stop.is_set():
                    break
                self._bridge.listen_connected.emit(False)
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
        self._sync_live_path()

    def _start_record(self):
        if self._recording:
            return
        # 启动/加载阶段禁止自动进入记录
        if getattr(self, "_loading", False):
            return
        if self._listen_stop.is_set():
            self._start_listen()
        with self._rec_lock:
            self._rec_samples = []
            self._rec_t0 = time.time()
        self._live_n = -1
        self.live.clear_path()
        self._recording = True
        if not self.btn_traj.isChecked():
            self.btn_traj.setChecked(True)
        self.lbl_rec.setText("记录中  0 点")
        self.lbl_rec.setStyleSheet("color:#2563EB;")
        self._sync_pose_buttons()
        step = self._sample_step_mm
        if step > 0:
            self._append("开始记录 TCP / 关节，间距 %.2f mm" % step)
        else:
            self._append("开始记录 TCP / 关节")

    def _stop_record(self, open_html=None):
        if not self._recording and not self._rec_samples:
            return
        self._recording = False
        self._sync_pose_buttons()
        with self._rec_lock:
            samples = list(self._rec_samples)
            self._rec_t0 = None
        self._sync_live_path()
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


    def _sync_live_path(self):
        with self._rec_lock:
            n = len(self._rec_samples)
            if n == self._live_n:
                return
            pts = tcp_xyz(self._rec_samples)
        self._live_n = n
        self.live.set_points(pts, n)

    def _open_traces_folder(self):
        """打开轨迹 traces 目录（与写 CSV/HTML 同路径）；缺则创建。"""
        folder = app_dir() / "traces"
        try:
            folder.mkdir(parents=True, exist_ok=True)
            os.startfile(str(folder.resolve()))
            self._append("已打开轨迹目录  %s" % folder, "ok")
        except OSError as exc:
            self._append("打开轨迹目录失败: %s" % exc, "err")

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
                self._append("目录 %s  无脚本（支持 .script/.lua/.py/.txt）" % path)
            return

        root = Path(path).resolve()
        for p in files:
            try:
                size = p.stat().st_size
            except OSError:
                size = 0
            try:
                rel = p.resolve().relative_to(root).as_posix()
            except ValueError:
                rel = p.name
            item = QListWidgetItem("%s  (%d B)" % (rel, size))
            item.setData(Qt.UserRole, str(p.resolve()))
            self.lst.addItem(item)
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
        if text.startswith("（") or text.startswith("("):
            return
        stored = item.data(Qt.UserRole)
        name = text.split("  (")[0]
        path = Path(stored) if stored else (Path(self.script_dir) / name)
        try:
            content = _read_file(path)
        except OSError as exc:
            QMessageBox.critical(self, "读取失败", str(exc))
            return
        self.txt.setPlainText(content)
        self._set_filename(name)
        self._schedule_save()

    def _save_as(self):
        initial = self.current_name or "untitled.script"
        if Path(initial).suffix.lower() not in (".script", ".lua", ".py", ".txt"):
            initial = initial + ".script"
        initialdir = str(self.script_dir) if Path(self.script_dir).is_dir() else str(app_dir() / "scripts")
        chosen, _ = QFileDialog.getSaveFileName(
            self,
            "另存为",
            str(Path(initialdir) / initial),
            "脚本 (*.script *.lua *.py *.txt);;Elite (*.script);;Lua (*.lua);;Python (*.py);;文本 (*.txt);;所有文件 (*.*)",
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
        if self._dir_started:
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
        import elite_script_send as _legacy
        return _legacy.run_cli(args)

    # High-DPI: Qt6 handles by default; ensure AA attributes for older bindings
    QApplication.setHighDpiScaleFactorRoundingPolicy(
        Qt.HighDpiScaleFactorRoundingPolicy.PassThrough
    )
    app = QApplication(sys.argv)
    app.setFont(UI_FONT)
    win = EliteScriptWindow()
    win.show()
    app.processEvents()  # 先画出可点壳，listen 在 showEvent 里异步启动
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())

from __future__ import annotations

from pathlib import Path

import os
import re
import shlex
import subprocess

import copy
import threading
import time

def _bundled_cjk_dir() -> Path | None:
    """WenQuanYi shipped with the app. Tk does not fall back per glyph."""
    font_dir = Path(__file__).resolve().parent / "fonts" / "wqy"
    if (font_dir / "wqy-microhei.ttc").is_file():
        return font_dir
    return None


def _sharpen_fonts_before_tk() -> None:
    """Register the bundled CJK face before Tk starts.

    A previous fonts.conf used a DOCTYPE that fontconfig rejects, so Tk only
    saw a Latin face and every Chinese character became an empty box.
    WenQuanYi is drawn with hinting off, matching its own fontconfig snippet.
    """
    if os.name != "posix" or os.environ.get("FONTCONFIG_FILE"):
        return
    font_dir = _bundled_cjk_dir()
    cache = Path.home() / ".cache" / "can_tester"
    try:
        cache.mkdir(parents=True, exist_ok=True)
        conf = cache / "fonts.conf"
        font_dir_xml = ""
        if font_dir is not None:
            font_dir_xml = "<dir>%s</dir>" % font_dir.as_posix()
        conf.write_text(
            """<?xml version="1.0"?>
<fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  %s
  <match target="font">
    <test name="family"><string>WenQuanYi Micro Hei</string></test>
    <edit name="hinting" mode="assign"><bool>false</bool></edit>
    <edit name="antialias" mode="assign"><bool>true</bool></edit>
    <edit name="rgba" mode="assign"><const>none</const></edit>
  </match>
  <match target="font">
    <test name="family"><string>WenQuanYi Micro Hei Mono</string></test>
    <edit name="hinting" mode="assign"><bool>false</bool></edit>
    <edit name="antialias" mode="assign"><bool>true</bool></edit>
    <edit name="rgba" mode="assign"><const>none</const></edit>
  </match>
  <alias>
    <family>sans-serif</family>
    <prefer><family>WenQuanYi Micro Hei</family></prefer>
  </alias>
  <alias>
    <family>Sans</family>
    <prefer><family>WenQuanYi Micro Hei</family></prefer>
  </alias>
</fontconfig>
"""
            % font_dir_xml,
            encoding="utf-8",
        )
        os.environ["FONTCONFIG_FILE"] = str(conf)
    except OSError:
        pass


_sharpen_fonts_before_tk()

import tkinter as tk
from tkinter import ttk, messagebox, simpledialog
from tkinter import font as tkfont

from can_tester.bus import (
    DEFAULT_IFACE,
    VIRTUAL_IFACE,
    CanSession,
    list_can_interfaces,
    is_socketcan_present,
    OCCUPANCY_HINT,
)
from can_tester.parse import (
    parse_can_data,
    parse_can_id_auto,
    format_can_id,
    format_data_hex,
)
from can_tester import megmeet
from can_tester.config import (
    AppSettings,
    PERIOD_FAIL_SILENT_MAX,
    REPLY_COUNT_MAX,
    REPLY_COUNT_MIN,
    frame_log_key,
    is_new_or_changed_payload,
    load_settings,
    parse_rx_id_whitelist,
    save_settings,
)
from can_tester.init_module import (
    InitModule,
    InitRow,
    USER_MODULES_DIR,
    find_module_by_name_or_stem,
    list_modules,
    load_module,
    run_module,
    save_module,
)
from can_tester.megmeet_responder import (
    ROBOT_ID,
    WELDER_ID,
    ResponderState,
    build_reply,
    on_robot_frame,
    state_from_mapping,
)
from can_tester import __version__
from can_tester.responder_module import (
    USER_RESPONDER_DIR,
    ResponderModule,
    ResponderRow,
    SignalDef,
    apply_signals_to_buffer,
    find_responder_by_name_or_stem,
    list_responder_modules,
    load_responder_module,
    matching_rows,
    package_responder_dir,
    save_responder_module,
    signal_defaults_dict,
    values_from_signals_for_state,
)
from can_tester.goo_combo import (
    COMBO_MEGMEET_GOO,
    INIT_MODULE_NAME,
    JOB_MODES,
    MODE_BY_NAME,
    RESPONDER_MODULE_NAME,
    ROLE_CLIENT,
    ROLE_WELDER,
    ROBOT_ID,
    WELDER_ID,
    describe_robot,
    describe_welder,
    pack_robot_poll,
)
from can_tester.responder_service import plan_reply

# ----- designer tokens (美工) -----
BG = "#F8F8FA"
BORDER = "#D8DCE1"
HEADER_FG = "#5A6069"
STATUS_ERR_FG = "#C4554D"
MUTED_FG = "#787D85"
PRIMARY_BG = "#2F6FED"
PRIMARY_BG_ACTIVE = "#2558C7"
PRIMARY_BG_DISABLED = "#A8C0F0"
PRIMARY_FG = "#FFFFFF"
SECONDARY_BG = "#FFFFFF"
SECONDARY_FG = "#2A2E34"
ROW_HEIGHT = 32
EDGE = 16
GAP = 12
FONT_UI = ("Sans", 10)
FONT_UI_SM = ("Sans", 9)
FONT_MONO = ("DejaVu Sans Mono", 10)
BAUD_CHOICES = ("125000", "250000", "500000", "1000000")
LED_IDLE = BORDER  # #D8DCE1
LED_TX_ON = "#2F9E5B"
LED_RX_ON = PRIMARY_BG  # #2F6FED
LED_DIAMETER = 10
LED_GAP = 6
LED_ON_MS = 140  # lit time per blink
LED_GAP_MS = 90  # forced off so a fast stream still blinks instead of staying solid

# Settings 美工 lock v1.7.24
MOD_ROW_H = 32
MOD_COMBO_MIN_W = 220
MOD_PAD_X = 10  # horizontal padding (combo field + label↔control gap)
MOD_BTN_GAP = 6
MOD_LABEL_MAX = 18  # truncate long display names; factory short names never clip
SETTINGS_LABEL_W = 110  # left label column (~通用 / 回帧 / 初始化)
SETTINGS_SECTION = 12  # section / control-row spacing
SETTINGS_HELP_GAP = 8  # help text top gap below controls
SETTINGS_HELP_WRAP = 520

# 选档 combobox: top entry is literal 「未选择」(not blank)
CHOICE_NONE = "未选择"



def _truncate_ui_label(text: str, max_len: int = MOD_LABEL_MAX) -> str:
    """Short readable combo label; append … when too long."""
    s = (text or "").strip()
    if max_len <= 0:
        return ""
    if len(s) <= max_len:
        return s
    if max_len == 1:
        return "…"
    return s[: max_len - 1] + "…"


def _display_module_choices(full_keys: list[str]) -> tuple[list[str], dict[str, str]]:
    """Build combo values (truncated); top is always 「未选择」→""."""
    mapping: dict[str, str] = {CHOICE_NONE: ""}
    displays: list[str] = [CHOICE_NONE]
    used: set[str] = {CHOICE_NONE}
    for full in full_keys:
        full = (full or "").strip()
        if not full or full == CHOICE_NONE:
            continue
        disp = _truncate_ui_label(full)
        n = 2
        while disp in used and mapping.get(disp) != full:
            # Rare collision after truncate — disambiguate.
            suffix = str(n)
            room = max(1, MOD_LABEL_MAX - 1 - len(suffix))
            disp = full[:room] + "…" + suffix
            n += 1
        used.add(disp)
        mapping[disp] = full
        displays.append(disp)
    return displays, mapping


def _is_choice_none(value: str | None) -> bool:
    s = (value or "").strip()
    return (not s) or s == CHOICE_NONE


class _HoverTooltip:
    """Lightweight hover tip for signal notes."""

    def __init__(self, widget: tk.Misc, text: str) -> None:
        self.widget = widget
        self.text = text or ""
        self._tip: tk.Toplevel | None = None
        if self.text:
            widget.bind("<Enter>", self._show, add="+")
            widget.bind("<Leave>", self._hide, add="+")

    def _show(self, _event=None) -> None:
        if self._tip is not None or not self.text:
            return
        tip = tk.Toplevel(self.widget)
        tip.wm_overrideredirect(True)
        try:
            tip.attributes("-topmost", True)
        except tk.TclError:
            pass
        x = self.widget.winfo_rootx() + 8
        y = self.widget.winfo_rooty() + self.widget.winfo_height() + 4
        tip.geometry(f"+{x}+{y}")
        tk.Label(
            tip,
            text=self.text,
            justify=tk.LEFT,
            background="#FFF8E7",
            foreground="#333333",
            relief=tk.SOLID,
            borderwidth=1,
            font=FONT_UI_SM,
            wraplength=320,
            padx=6,
            pady=4,
        ).pack()
        self._tip = tip

    def _hide(self, _event=None) -> None:
        tip = self._tip
        self._tip = None
        if tip is not None:
            try:
                tip.destroy()
            except tk.TclError:
                pass


SIGNAL_ROW_H = 28
SIGNAL_GAP = 6


def _font_families(root: tk.Misc) -> set[str]:
    try:
        return set(tkfont.families(root))
    except tk.TclError:
        return set()


def _choose_ui_family(root: tk.Misc) -> str:
    """WenQuanYi stays sharp at UI sizes. Noto CJK at 9–10px goes muddy in Tk."""
    families = _font_families(root)
    for name in (
        "WenQuanYi Micro Hei",
        "WenQuanYi Zen Hei",
        "Noto Sans CJK SC",
        "Noto Sans CJK JP",
        "Noto Sans CJK TC",
        "Source Han Sans SC",
        "Source Han Sans CN",
        "Droid Sans Fallback",
        "AR PL UMing CN",
        "Microsoft YaHei",
        "PingFang SC",
    ):
        if name in families:
            return name
    return "Sans"


def _desktop_text_scale() -> float:
    """1.0 means leave Tk's own scaling. Larger means the desktop is scaled up."""
    try:
        out = subprocess.check_output(["xrdb", "-query"], text=True, timeout=1, stderr=subprocess.DEVNULL)
    except (OSError, subprocess.SubprocessError):
        out = ""
    for line in out.splitlines():
        if "Xft.dpi" not in line or ":" not in line:
            continue
        try:
            dpi = float(line.split(":", 1)[1].strip())
        except ValueError:
            continue
        if dpi > 110:
            return dpi / 96.0
    return 1.0


def _choose_mono_family(root: tk.Misc, ui_family: str) -> str:
    families = _font_families(root)
    if ui_family != "Sans":
        return ui_family
    return "DejaVu Sans Mono"


def _apply_cjk_fonts(root: tk.Misc) -> None:
    global FONT_UI, FONT_UI_SM, FONT_MONO
    scale = _desktop_text_scale()
    if scale > 1.05:
        try:
            root.tk.call("tk", "scaling", round(96.0 * scale / 72.0, 2))
        except tk.TclError:
            pass
    ui = _choose_ui_family(root)
    mono = _choose_mono_family(root, ui)
    # CJK below 11px smears. Keep labels and headings on the same size.
    FONT_UI = (ui, 12)
    FONT_UI_SM = (ui, 12)
    FONT_MONO = (ui, 12)
    named = {
        "TkDefaultFont": FONT_UI,
        "TkTextFont": FONT_UI,
        "TkHeadingFont": FONT_UI_SM,
        "TkCaptionFont": FONT_UI_SM,
        "TkMenuFont": FONT_UI,
        "TkFixedFont": FONT_MONO,
    }
    for name, spec in named.items():
        try:
            tkfont.nametofont(name, root).configure(family=spec[0], size=spec[1])
        except tk.TclError:
            pass
    family, size = FONT_UI
    if family in _font_families(root):
        spec = "{%s} %s" % (family, size)
        try:
            root.option_add("*Font", spec)
            root.option_add("*Label.font", spec)
            root.option_add("*Menu.font", spec)
            root.option_add("*Menu.foreground", SECONDARY_FG)
        except tk.TclError:
            pass


def _apply_app_style(root: tk.Misc) -> ttk.Style:
    _apply_cjk_fonts(root)
    style = ttk.Style(root)
    try:
        style.theme_use("clam")
    except tk.TclError:
        pass

    style.configure(".", background=BG, foreground=SECONDARY_FG, font=FONT_UI)
    style.configure("TFrame", background=BG)
    style.configure("Card.TFrame", background=BG, relief="solid", borderwidth=1)
    style.configure("TLabel", background=BG, foreground=SECONDARY_FG, font=FONT_UI)
    style.configure("Muted.TLabel", background=BG, foreground=SECONDARY_FG, font=FONT_UI)
    style.configure("StatusMuted.TLabel", background=BG, foreground=SECONDARY_FG, font=FONT_UI)
    style.configure("StatusError.TLabel", background=BG, foreground=STATUS_ERR_FG, font=FONT_UI)
    style.configure("Header.TLabel", background=BG, foreground=SECONDARY_FG, font=FONT_UI)
    style.configure("TLabelframe", background=BG, foreground=SECONDARY_FG, bordercolor=BORDER)
    style.configure("TLabelframe.Label", background=BG, foreground=SECONDARY_FG, font=FONT_UI)
    style.configure("TCheckbutton", background=BG, foreground=SECONDARY_FG, font=FONT_UI)
    style.configure("TRadiobutton", background=BG, font=FONT_UI)
    style.configure("TEntry", fieldbackground="#FFFFFF", foreground=SECONDARY_FG, font=FONT_UI, bordercolor=BORDER, lightcolor=BORDER, darkcolor=BORDER)
    style.configure("TCombobox", fieldbackground="#FFFFFF", foreground=SECONDARY_FG, font=FONT_UI, bordercolor=BORDER)
    style.configure(
        "SettingsMod.TCombobox",
        fieldbackground="#FFFFFF",
        bordercolor=BORDER,
        foreground=SECONDARY_FG,
        font=FONT_UI,  # size 10
        padding=(MOD_PAD_X, 6),
    )
    style.map(
        "SettingsMod.TCombobox",
        foreground=[
            ("disabled", MUTED_FG),
            ("readonly", SECONDARY_FG),
            ("!disabled", SECONDARY_FG),
        ],
    )
    style.configure(
        "SettingsModMuted.TCombobox",
        fieldbackground="#FFFFFF",
        bordercolor=BORDER,
        foreground=MUTED_FG,  # #787D85 when 「未选择」
        font=FONT_UI,
        padding=(MOD_PAD_X, 6),
    )
    style.map(
        "SettingsModMuted.TCombobox",
        foreground=[
            ("disabled", MUTED_FG),
            ("readonly", MUTED_FG),
            ("!disabled", MUTED_FG),
        ],
    )
    style.configure("TSpinbox", fieldbackground="#FFFFFF", bordercolor=BORDER)
    style.configure("TScrollbar", background=BG, troughcolor=BG, bordercolor=BORDER)

    style.configure(
        "Primary.TButton",
        background=PRIMARY_BG,
        foreground=PRIMARY_FG,
        bordercolor=PRIMARY_BG,
        focusthickness=0,
        padding=(14, 6),
        font=FONT_UI,
    )
    style.map(
        "Primary.TButton",
        background=[
            ("disabled", PRIMARY_BG_DISABLED),
            ("pressed", PRIMARY_BG_ACTIVE),
            ("active", PRIMARY_BG_ACTIVE),
        ],
        foreground=[("disabled", "#F0F4FC")],
    )

    style.configure(
        "Secondary.TButton",
        background=SECONDARY_BG,
        foreground=SECONDARY_FG,
        bordercolor=BORDER,
        focusthickness=0,
        padding=(12, 6),
        font=FONT_UI,
    )
    style.map(
        "Secondary.TButton",
        background=[
            ("disabled", "#EEF0F3"),
            ("pressed", "#E8EAEE"),
            ("active", "#F0F2F5"),
        ],
        foreground=[("disabled", MUTED_FG)],
    )

    style.configure(
        "DangerSecondary.TButton",
        background=SECONDARY_BG,
        foreground=STATUS_ERR_FG,
        bordercolor=STATUS_ERR_FG,
        focusthickness=0,
        padding=(12, 6),
        font=FONT_UI,
    )
    style.map(
        "DangerSecondary.TButton",
        background=[
            ("disabled", "#EEF0F3"),
            ("pressed", "#F8E8E7"),
            ("active", "#FBEDEC"),
        ],
        foreground=[
            ("disabled", MUTED_FG),
            ("pressed", STATUS_ERR_FG),
            ("active", STATUS_ERR_FG),
        ],
        bordercolor=[
            ("disabled", BORDER),
            ("pressed", STATUS_ERR_FG),
            ("active", STATUS_ERR_FG),
        ],
    )

    # Settings 初始化 module-row buttons: height ~32, font 10, h-pad 10
    style.configure(
        "SettingsRow.TButton",
        background=SECONDARY_BG,
        foreground=SECONDARY_FG,
        bordercolor=BORDER,
        focusthickness=0,
        padding=(MOD_PAD_X, 6),
        font=FONT_UI,
    )
    style.map(
        "SettingsRow.TButton",
        background=[
            ("disabled", "#EEF0F3"),
            ("pressed", "#E8EAEE"),
            ("active", "#F0F2F5"),
        ],
        foreground=[("disabled", MUTED_FG)],
    )
    style.configure(
        "SettingsRowDanger.TButton",
        background=SECONDARY_BG,
        foreground=STATUS_ERR_FG,
        bordercolor=STATUS_ERR_FG,
        focusthickness=0,
        padding=(MOD_PAD_X, 6),
        font=FONT_UI,
    )
    style.map(
        "SettingsRowDanger.TButton",
        background=[
            ("disabled", "#EEF0F3"),
            ("pressed", "#F8E8E7"),
            ("active", "#FBEDEC"),
        ],
        foreground=[
            ("disabled", MUTED_FG),
            ("pressed", STATUS_ERR_FG),
            ("active", STATUS_ERR_FG),
        ],
        bordercolor=[
            ("disabled", BORDER),
            ("pressed", STATUS_ERR_FG),
            ("active", STATUS_ERR_FG),
        ],
    )

    style.configure(
        "Toolbar.TButton",
        background=SECONDARY_BG,
        foreground=SECONDARY_FG,
        bordercolor=BORDER,
        padding=(8, 4),
        font=FONT_UI_SM,
    )
    style.map(
        "Toolbar.TButton",
        background=[("pressed", "#E8EAEE"), ("active", "#F0F2F5")],
    )

    style.configure(
        "Quick.TButton",
        background=SECONDARY_BG,
        foreground=SECONDARY_FG,
        bordercolor=BORDER,
        focusthickness=0,
        padding=(8, 3),
        font=FONT_UI_SM,
    )
    style.map(
        "Quick.TButton",
        background=[
            ("disabled", "#EEF0F3"),
            ("pressed", "#E8EAEE"),
            ("active", "#F0F2F5"),
        ],
        foreground=[("disabled", MUTED_FG)],
    )

    style.configure(
        "Treeview",
        background="#FFFFFF",
        fieldbackground="#FFFFFF",
        foreground=SECONDARY_FG,
        bordercolor=BORDER,
        lightcolor=BORDER,
        darkcolor=BORDER,
        rowheight=ROW_HEIGHT,
        font=FONT_UI,
    )
    style.configure(
        "Treeview.Heading",
        background="#EEF0F3",
        foreground=SECONDARY_FG,
        bordercolor=BORDER,
        relief="flat",
        font=FONT_UI_SM,
    )
    style.map("Treeview.Heading", background=[("active", "#E4E7EB")])
    style.map(
        "Treeview",
        background=[("selected", "#D6E4FF")],
        foreground=[("selected", SECONDARY_FG)],
    )

    style.configure(
        "Mono.Treeview",
        background="#FFFFFF",
        fieldbackground="#FFFFFF",
        foreground=SECONDARY_FG,
        bordercolor=BORDER,
        rowheight=ROW_HEIGHT,
        font=FONT_MONO,
    )
    style.configure(
        "Mono.Treeview.Heading",
        background="#EEF0F3",
        foreground=SECONDARY_FG,
        bordercolor=BORDER,
        relief="flat",
        font=FONT_UI_SM,
    )
    style.map("Mono.Treeview.Heading", background=[("active", "#E4E7EB")])
    style.map(
        "Mono.Treeview",
        background=[("selected", "#D6E4FF")],
        foreground=[("selected", SECONDARY_FG)],
    )

    style.configure("TSeparator", background=BORDER)
    return style


class CardFrame(ttk.Frame):
    """Section card with border color approximating radius-8 panel."""

    def __init__(self, master, **kw):
        kw.setdefault("style", "TFrame")
        super().__init__(master, **kw)
        self.configure(padding=0)
        # Outer highlight approximates a rounded card border under ttk limits.
        self._outer = tk.Frame(
            self,
            bg=BORDER,
            highlightthickness=0,
            bd=0,
        )
        self._outer.pack(fill=tk.BOTH, expand=True)
        self.body = tk.Frame(self._outer, bg=BG, highlightthickness=0, bd=0)
        self.body.pack(fill=tk.BOTH, expand=True, padx=1, pady=1)


class RowEditDialog(tk.Toplevel):
    """Edit one init table row."""

    def __init__(self, master: tk.Misc, row: InitRow) -> None:
        super().__init__(master)
        self.title("编辑初始化行")
        self.resizable(False, False)
        self.result: InitRow | None = None
        self.configure(bg=BG)
        self.transient(master)
        try:
            self.grab_set()
        except tk.TclError:
            pass

        body = ttk.Frame(self, padding=EDGE)
        body.pack(fill=tk.BOTH, expand=True)

        self.enabled_var = tk.BooleanVar(value=row.enabled)
        # Blank row (can_id=0) → empty editable field, never a blank window.
        self.id_var = tk.StringVar(
            value="" if int(getattr(row, "can_id", 0) or 0) == 0 else f"0x{row.can_id:X}"
        )
        self.ext_var = tk.BooleanVar(value=bool(row.extended))
        self.data_var = tk.StringVar(value=format_data_hex(row.data))
        self.interval_var = tk.StringVar(
            value="" if row.interval_ms is None else str(int(row.interval_ms))
        )
        self.note_var = tk.StringVar(value=row.note or "")

        r = 0
        ttk.Checkbutton(body, text="启用", variable=self.enabled_var).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="CAN ID").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.id_var, width=20).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Checkbutton(body, text="扩展帧", variable=self.ext_var).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="数据(HEX)").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.data_var, width=40).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="间隔(ms)").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.interval_var, width=12).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        ttk.Label(body, text="空/0 = 用全局默认", style="Muted.TLabel").grid(
            row=r, column=2, sticky=tk.W, padx=4
        )
        r += 1
        ttk.Label(body, text="备注").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.note_var, width=40).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1

        btns = ttk.Frame(body)
        btns.grid(row=r, column=0, columnspan=3, pady=(GAP, 0))
        ttk.Button(btns, text="确定", style="Primary.TButton", command=self._ok).pack(
            side=tk.LEFT, padx=4
        )
        ttk.Button(
            btns, text="取消", style="Secondary.TButton", command=self.destroy
        ).pack(side=tk.LEFT, padx=4)

        self.bind("<Return>", lambda _e: self._ok())
        self.bind("<Escape>", lambda _e: self.destroy())
        self.wait_visibility()
        self.focus_set()

    def _ok(self) -> None:
        try:
            force_ext = True if self.ext_var.get() else None
            can_id, extended = parse_can_id_auto(self.id_var.get().strip(), force_ext)
            if self.ext_var.get():
                extended = True
            data = parse_can_data(self.data_var.get())
            iv_text = self.interval_var.get().strip()
            if not iv_text:
                interval_ms = None
            else:
                interval_ms = int(float(iv_text))
                if interval_ms <= 0:
                    interval_ms = None
            self.result = InitRow(
                enabled=bool(self.enabled_var.get()),
                can_id=can_id,
                extended=extended,
                data=data,
                interval_ms=interval_ms,
                note=self.note_var.get().strip(),
            )
        except Exception as exc:
            messagebox.showerror("无效", str(exc), parent=self)
            return
        self.destroy()


class SettingsDialog(tk.Toplevel):
    """Settings: left nav + right detail (v1.7.24 美工 lock; add-row fix 1.7.25)."""

    PAGES = ("通用", "口命令", "初始化", "回帧")
    NAV_W = 120
    NAV_BG = "#EEF0F3"
    NAV_SEL_BG = "#FFFFFF"
    NAV_FG = HEADER_FG  # #5A6069
    NAV_BAR = PRIMARY_BG  # #2F6FED
    ROW_H = 36
    WIN_W, WIN_H = 720, 560

    def __init__(
        self,
        master: "CanTesterApp",
        settings: AppSettings,
        module_choices: list[str],
        responder_choices: list[str] | None = None,
    ) -> None:
        super().__init__(master)
        self.title("设置")
        self.resizable(False, False)
        self.result: AppSettings | None = None
        self.configure(bg=BORDER)
        self.transient(master)
        self.grab_set()
        self.app = master
        self._page = self.PAGES[0]
        self._embed_kind: str | None = None
        self._embed_panel: tk.Misc | None = None

        self.enable_init_var = tk.BooleanVar(value=settings.enable_init_on_connect)
        full_keys = list(module_choices)
        raw_sel = (settings.init_module or "").strip()
        if raw_sel and raw_sel != CHOICE_NONE and raw_sel not in full_keys:
            full_keys.append(raw_sel)
        displays, mapping = _display_module_choices(full_keys)
        self._module_display_map = mapping
        # Show truncated label in combo; map back to full on save.
        sel_disp = CHOICE_NONE
        if raw_sel and raw_sel != CHOICE_NONE:
            for d, f in mapping.items():
                if f == raw_sel or d == raw_sel:
                    sel_disp = d
                    break
            if sel_disp == CHOICE_NONE:
                sel_disp = _truncate_ui_label(raw_sel)
                mapping[sel_disp] = raw_sel
                self._module_display_map = mapping
        # No concrete module → force enable off (未选择 pattern).
        if sel_disp == CHOICE_NONE:
            self.enable_init_var.set(False)
        self.module_var = tk.StringVar(value=sel_disp)
        self._module_choices = displays
        self.default_iv_var = tk.StringVar(value=str(int(settings.default_interval_ms)))
        self.rx_filter_var = tk.StringVar(value=settings.rx_id_filter or "")
        self.rx_changes_var = tk.BooleanVar(value=bool(settings.rx_changes_only))
        self.show_self_echo_var = tk.BooleanVar(value=bool(settings.show_self_echo))
        self.listen_only_var = tk.BooleanVar(value=bool(settings.listen_only_shared))
        self.enable_responder_var = tk.BooleanVar(value=bool(settings.enable_responder))
        raw_r = (settings.responder_module or "").strip()
        r_choices = [CHOICE_NONE] + [
            c for c in (responder_choices or []) if c and c != CHOICE_NONE
        ]
        # Resolve stem → JSON name so dropdown never shows stem.
        sel_r = CHOICE_NONE
        if raw_r and raw_r != CHOICE_NONE:
            if raw_r in r_choices:
                sel_r = raw_r
            else:
                found = find_responder_by_name_or_stem(raw_r)
                if found is not None:
                    nm = (found.name or "").strip() or (
                        found.path.stem if found.path else raw_r
                    )
                    if nm not in r_choices:
                        r_choices.append(nm)
                    sel_r = nm
                else:
                    r_choices.append(raw_r)
                    sel_r = raw_r
        self.responder_module_var = tk.StringVar(value=sel_r)
        if _is_choice_none(self.responder_module_var.get()):
            self.enable_responder_var.set(False)
        self._responder_choices = r_choices
        # Button / checkbox refs filled when pages build.
        self._init_enable_cb: ttk.Checkbutton | None = None
        self._init_edit_btn: ttk.Button | None = None
        self._init_del_btn: ttk.Button | None = None
        self._resp_enable_cb: ttk.Checkbutton | None = None
        self._resp_edit_btn: ttk.Button | None = None
        self.print_var = tk.BooleanVar(value=settings.print_client_data)
        self.count_var = tk.StringVar(value=str(settings.reply_count))
        self.to_var = tk.StringVar(value=str(int(settings.reply_timeout_ms)))
        self.iface_cmd_var = tk.StringVar(value="can0")

        shell = tk.Frame(self, bg=BORDER, bd=0, highlightthickness=0)
        shell.pack(fill=tk.BOTH, expand=True)
        outer = tk.Frame(shell, bg=BG)
        outer.pack(fill=tk.BOTH, expand=True, padx=1, pady=1)

        mid = tk.Frame(outer, bg=BG)
        mid.pack(fill=tk.BOTH, expand=True)

        # --- left nav ---
        nav_wrap = tk.Frame(mid, bg=self.NAV_BG, width=self.NAV_W)
        nav_wrap.pack(side=tk.LEFT, fill=tk.Y)
        nav_wrap.pack_propagate(False)
        self._nav_btns: list[tk.Frame] = []
        self._nav_labels: list[tk.Label] = []
        self._nav_bars: list[tk.Frame] = []
        for i, name in enumerate(self.PAGES):
            row = tk.Frame(nav_wrap, bg=self.NAV_BG, height=self.ROW_H, cursor="hand2")
            row.pack(fill=tk.X)
            row.pack_propagate(False)
            bar = tk.Frame(row, bg=self.NAV_BG, width=3)
            bar.pack(side=tk.LEFT, fill=tk.Y)
            lab = tk.Label(
                row,
                text=name,
                bg=self.NAV_BG,
                fg=self.NAV_FG,
                font=FONT_UI,
                anchor=tk.W,
                padx=12,
            )
            lab.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
            for w in (row, lab, bar):
                w.bind("<Button-1>", lambda _e, n=name: self._select_page(n))
            self._nav_btns.append(row)
            self._nav_labels.append(lab)
            self._nav_bars.append(bar)

        # --- right column: fixed bottom bar + scrolling detail ---
        right = tk.Frame(mid, bg="#FFFFFF")
        right.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        bottom = tk.Frame(right, bg="#FFFFFF")
        bottom.pack(side=tk.BOTTOM, fill=tk.X)
        btns = ttk.Frame(bottom, padding=(16, 8, 16, 12))
        btns.pack(side=tk.RIGHT)
        ttk.Button(
            btns, text="保存", style="Primary.TButton", command=self._ok
        ).pack(side=tk.RIGHT)
        ttk.Button(
            btns, text="取消", style="Secondary.TButton", command=self._cancel
        ).pack(side=tk.RIGHT, padx=(0, 6))

        self._detail_host = tk.Frame(right, bg="#FFFFFF")
        self._detail_host.pack(fill=tk.BOTH, expand=True)

        self.protocol("WM_DELETE_WINDOW", self._cancel)
        self.update_idletasks()
        sw = self.winfo_screenwidth()
        sh = self.winfo_screenheight()
        x = max(0, (sw - self.WIN_W) // 2)
        y = max(0, (sh - self.WIN_H) // 2)
        self.geometry(f"{self.WIN_W}x{self.WIN_H}+{x}+{y}")
        self.minsize(self.WIN_W, self.WIN_H)
        self._select_page(self.PAGES[0])
        self.wait_visibility()
        self.focus_set()

    def _clear_detail(self) -> None:
        self._close_embed(silent=True)
        for w in self._detail_host.winfo_children():
            w.destroy()

    def _select_page(self, name: str) -> None:
        if name not in self.PAGES:
            return
        self._close_embed(silent=True)
        self._page = name
        for i, p in enumerate(self.PAGES):
            sel = p == name
            bg = self.NAV_SEL_BG if sel else self.NAV_BG
            bar_bg = self.NAV_BAR if sel else self.NAV_BG
            self._nav_btns[i].configure(bg=bg)
            self._nav_labels[i].configure(bg=bg, fg=self.NAV_FG)
            self._nav_bars[i].configure(bg=bar_bg)
        self._render_page()

    def _make_scroll_body(self) -> ttk.Frame:
        """Right detail: pad 16, vertical scroll when content overflows."""
        wrap = tk.Frame(self._detail_host, bg="#FFFFFF")
        wrap.pack(fill=tk.BOTH, expand=True)
        canvas = tk.Canvas(wrap, bg="#FFFFFF", highlightthickness=0, bd=0)
        sy = ttk.Scrollbar(wrap, orient=tk.VERTICAL, command=canvas.yview)
        canvas.configure(yscrollcommand=sy.set)
        sy.pack(side=tk.RIGHT, fill=tk.Y)
        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        body = ttk.Frame(canvas, padding=(EDGE, 0, EDGE, EDGE))  # page top pad 0
        win = canvas.create_window((0, 0), window=body, anchor="nw")

        def _sync_scroll(_event: object | None = None) -> None:
            canvas.configure(scrollregion=canvas.bbox("all"))

        def _sync_width(event: tk.Event) -> None:
            canvas.itemconfigure(win, width=max(int(event.width), 1))

        body.bind("<Configure>", _sync_scroll)
        canvas.bind("<Configure>", _sync_width)

        def _on_wheel(event: tk.Event) -> str | None:
            if getattr(event, "num", None) == 4 or int(getattr(event, "delta", 0) or 0) > 0:
                canvas.yview_scroll(-1, "units")
            elif getattr(event, "num", None) == 5 or int(getattr(event, "delta", 0) or 0) < 0:
                canvas.yview_scroll(1, "units")
            return "break"

        def _bind_wheel(_event: object | None = None) -> None:
            canvas.bind_all("<MouseWheel>", _on_wheel)
            canvas.bind_all("<Button-4>", _on_wheel)
            canvas.bind_all("<Button-5>", _on_wheel)

        def _unbind_wheel(_event: object | None = None) -> None:
            canvas.unbind_all("<MouseWheel>")
            canvas.unbind_all("<Button-4>")
            canvas.unbind_all("<Button-5>")

        canvas.bind("<Enter>", _bind_wheel)
        canvas.bind("<Leave>", _unbind_wheel)
        body.bind("<Enter>", _bind_wheel)
        body.bind("<Leave>", _unbind_wheel)
        self.bind("<Destroy>", lambda _e: _unbind_wheel(), add="+")
        return body

    def _render_page(self) -> None:
        self._clear_detail()
        body = self._make_scroll_body()
        body.columnconfigure(0, minsize=SETTINGS_LABEL_W)
        body.columnconfigure(1, weight=1)
        page = self._page
        if page == "通用":
            self._build_general(body)
        elif page == "口命令":
            self._build_iface_cmd(body)
        elif page == "初始化":
            self._build_init(body)
        elif page == "回帧":
            self._build_reply(body)

    def _add_row(
        self,
        parent: ttk.Frame,
        r: int,
        label: str,
        widget: tk.Widget,
        pady: tuple[int, int] = (0, SETTINGS_SECTION),
    ) -> int:
        # Label col ~110; control row H32 feel; sticky w = left + V-center
        ttk.Label(parent, text=label).grid(row=r, column=0, sticky="w", pady=pady)
        widget.grid(row=r, column=1, sticky="ew", pady=pady, padx=(MOD_PAD_X, 0))
        return r + 1

    def _add_help(self, parent: ttk.Frame, r: int, text: str) -> int:
        ttk.Label(
            parent,
            text=text,
            style="Muted.TLabel",
            wraplength=SETTINGS_HELP_WRAP,
        ).grid(
            row=r,
            column=0,
            columnspan=2,
            sticky=tk.W,
            pady=(SETTINGS_HELP_GAP, 0),
        )
        return r + 1

    def _build_general(self, body: ttk.Frame) -> None:
        r = 0
        rx_wrap = ttk.Frame(body)
        rx_wrap.rowconfigure(0, minsize=MOD_ROW_H)
        ttk.Entry(rx_wrap, textvariable=self.rx_filter_var).pack(
            side=tk.LEFT, fill=tk.X, expand=True
        )
        ttk.Button(
            rx_wrap,
            text="Megmeet",
            style="Toolbar.TButton",
            command=self._fill_megmeet_ids,
        ).pack(side=tk.LEFT, padx=(MOD_BTN_GAP, 0))
        r = self._add_row(body, r, "RX白名单", rx_wrap)
        r = self._add_row(
            body, r, "仅显示变化", ttk.Checkbutton(body, variable=self.rx_changes_var)
        )
        r = self._add_row(
            body, r, "显示本机回环", ttk.Checkbutton(body, variable=self.show_self_echo_var)
        )
        r = self._add_row(
            body,
            r,
            "只监听不独占",
            ttk.Checkbutton(
                body,
                variable=self.listen_only_var,
                command=self._on_listen_only_toggle,
            ),
        )
        r = self._add_row(
            body, r, "打印客户端", ttk.Checkbutton(body, variable=self.print_var)
        )
        r = self._add_help(body, r, "白名单空=全部（逗号/空格分隔）")
        r = self._add_help(
            body,
            r,
            "「仅显示变化」：同方向+同 ID 数据未变不进入时间线；有变化则追加一行。"
            "周期发送 / 自动应答的重复 TX 同样不进列表。",
        )
        r = self._add_help(
            body,
            r,
            "「只监听不独占」：SocketCAN 尽力共享打开；"
            "无可靠 listen-only 标志时禁止本工具 TX，冲突时提示占用",
        )

    def _build_iface_cmd(self, body: ttk.Frame) -> None:
        # Page nav already says 口命令 — no redundant LabelFrame title.
        r = 0
        ttk.Label(body, text="接口名").grid(
            row=r, column=0, sticky="w", pady=(0, SETTINGS_SECTION)
        )
        ttk.Entry(body, textvariable=self.iface_cmd_var).grid(
            row=r,
            column=1,
            sticky="ew",
            pady=(0, SETTINGS_SECTION),
            padx=(MOD_PAD_X, 0),
        )
        r += 1
        cmd_btns = ttk.Frame(body)
        cmd_btns.grid(row=r, column=0, columnspan=2, sticky="w")
        cmd_btns.rowconfigure(0, minsize=MOD_ROW_H)
        ttk.Button(
            cmd_btns,
            text="创建",
            style="SettingsRow.TButton",
            command=self._cmd_iface_create,
        ).pack(side=tk.LEFT)
        ttk.Button(
            cmd_btns,
            text="拉起",
            style="SettingsRow.TButton",
            command=self._cmd_iface_up,
        ).pack(side=tk.LEFT, padx=(MOD_BTN_GAP, 0))
        ttk.Button(
            cmd_btns,
            text="关闭",
            style="SettingsRow.TButton",
            command=self._cmd_iface_down,
        ).pack(side=tk.LEFT, padx=(MOD_BTN_GAP, 0))

    def _build_init(self, body: ttk.Frame) -> None:
        r = 0
        self._init_enable_cb = None  # v1.7.26: no 「启用」checkbox; combo drives on/off
        # 美工 lock: combo min 220, row H 32, font 10, pad_x 10; btns H 32 gap 6; V-center.
        displays = list(self._module_choices)
        mapping = getattr(self, "_module_display_map", None) or {d: d for d in displays}
        self._module_display_map = mapping

        mod_row = ttk.Frame(body)
        mod_row.columnconfigure(0, weight=1, minsize=MOD_COMBO_MIN_W)
        mod_row.rowconfigure(0, minsize=MOD_ROW_H)
        self.module_cb = ttk.Combobox(
            mod_row,
            textvariable=self.module_var,
            values=displays,
            style="SettingsMod.TCombobox",
            font=FONT_UI,
            state="readonly",
        )
        self.module_cb.grid(row=0, column=0, sticky="nsew")
        try:
            self.module_cb.configure(width=max(12, MOD_COMBO_MIN_W // 8))
        except tk.TclError:
            pass
        self.module_cb.bind("<<ComboboxSelected>>", self._on_module_combo_selected)
        mod_btns = ttk.Frame(mod_row)
        mod_btns.grid(row=0, column=1, sticky="", padx=(MOD_BTN_GAP, 0))
        ttk.Button(
            mod_btns,
            text="新建",
            style="SettingsRow.TButton",
            command=self._new_init_module,
        ).pack(side=tk.LEFT)
        self._init_edit_btn = ttk.Button(
            mod_btns,
            text="编辑",
            style="SettingsRow.TButton",
            command=self._embed_init_detail,
        )
        self._init_edit_btn.pack(side=tk.LEFT, padx=(MOD_BTN_GAP, 0))
        self._init_del_btn = ttk.Button(
            mod_btns,
            text="删除",
            style="SettingsRowDanger.TButton",
            command=self._delete_selected_init_module,
        )
        self._init_del_btn.pack(side=tk.LEFT, padx=(MOD_BTN_GAP, 0))
        ttk.Label(body, text="模块").grid(
            row=r, column=0, sticky="w", pady=(0, SETTINGS_SECTION)
        )
        mod_row.grid(
            row=r,
            column=1,
            sticky="ew",
            pady=(0, SETTINGS_SECTION),
            padx=(MOD_PAD_X, 0),
        )
        r += 1
        r = self._add_row(
            body, r, "默认间隔", ttk.Entry(body, textvariable=self.default_iv_var, width=12)
        )
        self._add_help(body, r, "间隔单位 ms；行间隔为空/0 时用此默认值")
        self._apply_module_choice_ui()

    def _build_responder(self, body: ttk.Frame) -> None:
        # No outer LabelFrame — page nav already 「应答服务」.
        # v1.7.30: read-only — profile dropdown only; no 编辑档 / match / signal / auto_rules.
        r = 0
        self._resp_enable_cb = None
        self._resp_edit_btn = None  # no edit entry point (美工 lock)
        resp_row = ttk.Frame(body)
        resp_row.columnconfigure(0, weight=1, minsize=MOD_COMBO_MIN_W)
        resp_row.rowconfigure(0, minsize=MOD_ROW_H)
        self.responder_cb = ttk.Combobox(
            resp_row,
            textvariable=self.responder_module_var,
            values=self._responder_choices,
            style="SettingsMod.TCombobox",
            font=FONT_UI,
            state="readonly",
        )
        self.responder_cb.grid(row=0, column=0, sticky="nsew")
        try:
            self.responder_cb.configure(width=max(12, MOD_COMBO_MIN_W // 8))
        except tk.TclError:
            pass
        self.responder_cb.bind("<<ComboboxSelected>>", self._on_responder_combo_selected)
        ttk.Label(body, text="应答档").grid(
            row=r, column=0, sticky="w", pady=(0, SETTINGS_SECTION)
        )
        resp_row.grid(
            row=r,
            column=1,
            sticky="ew",
            pady=(0, SETTINGS_SECTION),
            padx=(MOD_PAD_X, 0),
        )
        r += 1
        self._add_help(body, r, "规则按内置写死，不可编辑")
        self._apply_responder_choice_ui()

    def _build_reply(self, body: ttk.Frame) -> None:
        # Same label column as 通用; no redundant LabelFrame title.
        r = 0
        r = self._add_row(
            body,
            r,
            "回帧条数",
            ttk.Spinbox(
                body,
                from_=REPLY_COUNT_MIN,
                to=REPLY_COUNT_MAX,
                textvariable=self.count_var,
                width=8,
            ),
        )
        r = self._add_row(
            body,
            r,
            "回帧超时",
            ttk.Entry(body, textvariable=self.to_var, width=12),
        )
        self._add_help(
            body,
            r,
            "超时单位 ms（关「打印客户端」时，TX 后按条数等待回帧）",
        )

    def _embed_shell(self) -> ttk.Frame:
        """Replace right pane with back + scrollable host for embedded editors."""
        self._close_embed(silent=True)
        for w in self._detail_host.winfo_children():
            w.destroy()
        wrap = tk.Frame(self._detail_host, bg="#FFFFFF")
        wrap.pack(fill=tk.BOTH, expand=True)
        top = ttk.Frame(wrap, padding=(12, 8, 12, 0))
        top.pack(fill=tk.X)
        ttk.Button(
            top,
            text="← 返回",
            style="Secondary.TButton",
            command=self._back_from_embed,
        ).pack(side=tk.LEFT)
        # Vertical scroll so 720×520 Settings can reach match table + signal table + 保存.
        scroll_wrap = tk.Frame(wrap, bg="#FFFFFF")
        scroll_wrap.pack(fill=tk.BOTH, expand=True)
        canvas = tk.Canvas(scroll_wrap, bg="#FFFFFF", highlightthickness=0, bd=0)
        sy = ttk.Scrollbar(scroll_wrap, orient=tk.VERTICAL, command=canvas.yview)
        canvas.configure(yscrollcommand=sy.set)
        sy.pack(side=tk.RIGHT, fill=tk.Y)
        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        host = ttk.Frame(canvas, padding=(8, 4, 8, 8))
        win = canvas.create_window((0, 0), window=host, anchor="nw")

        def _sync_scroll(_event: object | None = None) -> None:
            canvas.configure(scrollregion=canvas.bbox("all"))

        def _sync_width(event: tk.Event) -> None:
            canvas.itemconfigure(win, width=max(int(event.width), 1))

        host.bind("<Configure>", _sync_scroll)
        canvas.bind("<Configure>", _sync_width)

        def _on_wheel(event: tk.Event) -> str | None:
            if getattr(event, "num", None) == 4 or int(getattr(event, "delta", 0) or 0) > 0:
                canvas.yview_scroll(-1, "units")
            elif getattr(event, "num", None) == 5 or int(getattr(event, "delta", 0) or 0) < 0:
                canvas.yview_scroll(1, "units")
            return "break"

        def _bind_wheel(_event: object | None = None) -> None:
            canvas.bind_all("<MouseWheel>", _on_wheel)
            canvas.bind_all("<Button-4>", _on_wheel)
            canvas.bind_all("<Button-5>", _on_wheel)

        def _unbind_wheel(_event: object | None = None) -> None:
            canvas.unbind_all("<MouseWheel>")
            canvas.unbind_all("<Button-4>")
            canvas.unbind_all("<Button-5>")

        canvas.bind("<Enter>", _bind_wheel)
        canvas.bind("<Leave>", _unbind_wheel)
        host.bind("<Enter>", _bind_wheel)
        host.bind("<Leave>", _unbind_wheel)
        wrap.bind("<Destroy>", lambda _e: _unbind_wheel(), add="+")
        return host

    def _resolve_module_choice(self, display: str | None = None) -> str:
        """Map combo display (maybe truncated) back to full module key."""
        disp = (display if display is not None else self.module_var.get() or "").strip()
        if _is_choice_none(disp):
            return ""
        mapping = getattr(self, "_module_display_map", None) or {}
        if disp in mapping:
            return mapping[disp]
        # Already a full name / stem
        return disp

    def _resolve_responder_choice(self, display: str | None = None) -> str:
        """Map responder combo value; 「未选择」→""."""
        disp = (
            display if display is not None else self.responder_module_var.get() or ""
        ).strip()
        if _is_choice_none(disp):
            return ""
        return disp

    def _apply_module_choice_ui(self) -> None:
        """未选择 → gray + disable edit/delete; concrete → normal (enable=selected)."""
        none = _is_choice_none(self.module_var.get())
        style = "SettingsModMuted.TCombobox" if none else "SettingsMod.TCombobox"
        try:
            if getattr(self, "module_cb", None) is not None:
                self.module_cb.configure(style=style)
        except (tk.TclError, AttributeError):
            pass
        btn_state = tk.DISABLED if none else tk.NORMAL
        for w in (self._init_edit_btn, self._init_del_btn):
            if w is None:
                continue
            try:
                w.configure(state=btn_state)
            except tk.TclError:
                pass
        self.enable_init_var.set(False if none else True)

    def _apply_responder_choice_ui(self) -> None:
        """未选择 → gray muted combo; concrete → normal (enable=selected). No edit btn."""
        none = _is_choice_none(self.responder_module_var.get())
        style = "SettingsModMuted.TCombobox" if none else "SettingsMod.TCombobox"
        try:
            if getattr(self, "responder_cb", None) is not None:
                self.responder_cb.configure(style=style)
        except (tk.TclError, AttributeError):
            pass
        self.enable_responder_var.set(False if none else True)

    def _on_module_combo_selected(self, _event: object | None = None) -> None:
        key = self._resolve_module_choice()
        if not key:
            self.enable_init_var.set(False)
            self.app._working_module = None
            try:
                self.app.init_var.set("")
            except Exception:
                pass
            self.app._rebuild_rows_tree()
            self.app._refresh_init_summary()
        else:
            self.enable_init_var.set(True)
            self._sync_settings_module_to_app()
        self._apply_module_choice_ui()

    def _on_responder_combo_selected(self, _event: object | None = None) -> None:
        key = self._resolve_responder_choice()
        if not key:
            self.enable_responder_var.set(False)
            self.app._responder_module = None
            self.app._sync_responder_state_from_module()
        else:
            self.enable_responder_var.set(True)
            self.listen_only_var.set(False)
            mod = find_responder_by_name_or_stem(key)
            if mod is not None:
                self.app._responder_module = mod
                self.app._sync_responder_state_from_module()
        self._apply_responder_choice_ui()

    def _sync_settings_module_to_app(self) -> None:
        """Map settings module_var (stem/name) onto app.init_var label."""
        key = self._resolve_module_choice()
        if not key:
            self.app._working_module = None
            try:
                self.app.init_var.set("")
            except Exception:
                pass
            self.app._rebuild_rows_tree()
            self.app._refresh_init_summary()
            return
        for m in self.app._init_modules:
            if m.name == key or (m.path and m.path.stem == key):
                self.app.init_var.set(self.app._module_label(m))
                self.app._load_selected_module()
                return

    def _reload_module_choices(self, select: str | None = None) -> None:
        """Refresh settings dropdown from app module list; optionally select key."""
        self.app._refresh_modules()
        full_keys = list(self.app._module_choice_keys())
        displays, mapping = _display_module_choices(full_keys)
        self._module_display_map = mapping
        self._module_choices = displays
        try:
            self.module_cb.configure(values=displays)
        except (tk.TclError, AttributeError):
            pass
        if select is not None:
            sel = (select or "").strip()
            if _is_choice_none(sel):
                self.module_var.set(CHOICE_NONE)
                self.enable_init_var.set(False)
            else:
                disp = CHOICE_NONE
                for d, f in mapping.items():
                    if f == sel or d == sel:
                        disp = d
                        break
                if disp == CHOICE_NONE:
                    # stem match against modules
                    for m in self.app._init_modules:
                        if m.path and m.path.stem == sel:
                            for d, f in mapping.items():
                                if f == (m.name or "").strip() or f == sel:
                                    disp = d
                                    break
                            break
                self.module_var.set(disp)
                if disp != CHOICE_NONE:
                    self.enable_init_var.set(True)
            self._apply_module_choice_ui()

    def _new_init_module(self) -> None:
        """Create a blank init module JSON under ~/.can_tester/modules/."""
        USER_MODULES_DIR.mkdir(parents=True, exist_ok=True)
        name = simpledialog.askstring(
            "新建模块",
            "模块名称:",
            initialvalue="未命名模块",
            parent=self,
        )
        if name is None:
            return
        name = name.strip() or "未命名模块"
        # Derive a unique file stem from the display name.
        base = re.sub(r"[^\w\u4e00-\u9fff\-]+", "_", name, flags=re.UNICODE).strip("_") or "untitled"
        stem = base
        n = 2
        path = USER_MODULES_DIR / f"{stem}.json"
        while path.is_file():
            stem = f"{base}_{n}"
            path = USER_MODULES_DIR / f"{stem}.json"
            n += 1
        mod = InitModule(
            name=name,
            rows=[],
            description="",
            default_interval_ms=50,
        )
        try:
            save_module(mod, path)
        except Exception as exc:
            messagebox.showerror("新建失败", str(exc), parent=self)
            return
        self._reload_module_choices(select=stem)
        self.enable_init_var.set(True)
        self._apply_module_choice_ui()
        self._sync_settings_module_to_app()

    def _delete_selected_init_module(self) -> None:
        key = self._resolve_module_choice()
        if not key:
            messagebox.showinfo("提示", "请先选择要删除的模块", parent=self)
            return
        mod = find_module_by_name_or_stem(key)
        if mod is None or mod.path is None:
            messagebox.showwarning("无法删除", f"找不到模块文件: {key}", parent=self)
            return
        label = (mod.name or mod.path.stem).strip() or key
        if not messagebox.askyesno(
            "删除模块", f"删除模块 {label}？", parent=self
        ):
            return
        path = Path(mod.path).resolve()
        try:
            path.unlink()
        except OSError as exc:
            messagebox.showerror("删除失败", str(exc), parent=self)
            return
        # Clear app working copy if it was the deleted file.
        wm = self.app._working_module
        if wm is not None and wm.path is not None:
            try:
                if Path(wm.path).resolve() == path:
                    self.app._working_module = None
                    self.app.init_var.set("")
            except OSError:
                self.app._working_module = None
                self.app.init_var.set("")
        # Refresh list; deleted was active → 「未选择」.
        self._reload_module_choices(select=CHOICE_NONE)
        self.app._rebuild_rows_tree()
        self.app._refresh_init_summary()

    def _embed_init_detail(self) -> None:
        if not self._resolve_module_choice():
            messagebox.showinfo("提示", "请先选择模块", parent=self)
            return
        self._sync_settings_module_to_app()
        host = self._embed_shell()
        panel = InitDetailPanel(host, self.app, embedded=True)
        panel.pack(fill=tk.BOTH, expand=True)
        self._embed_kind = "init"
        self._embed_panel = panel
        # Prefer settings-embedded panel over any stale top-level
        if getattr(self.app, "_init_detail", None) is not None:
            try:
                other = self.app._init_detail
                if other is not panel:
                    other.destroy()
            except tk.TclError:
                pass
        self.app._init_detail = panel  # type: ignore[assignment]

    def _embed_responder_detail(self) -> None:
        key = self._resolve_responder_choice()
        if not key:
            messagebox.showinfo("提示", "请先选择应答档", parent=self)
            return
        host = self._embed_shell()
        panel = ResponderDetailPanel(host, self.app, preselect=key, embedded=True)
        panel.pack(fill=tk.BOTH, expand=True)
        self._embed_kind = "responder"
        self._embed_panel = panel
        if getattr(self.app, "_responder_detail", None) is not None:
            try:
                other = self.app._responder_detail
                if other is not panel:
                    other.destroy()
            except tk.TclError:
                pass
        self.app._responder_detail = panel  # type: ignore[assignment]

    def _close_embed(self, silent: bool = False) -> None:
        panel = self._embed_panel
        kind = self._embed_kind
        self._embed_panel = None
        self._embed_kind = None
        if panel is None:
            return
        try:
            if kind == "init" and hasattr(panel, "detach"):
                panel.detach()
                if getattr(self.app, "_init_detail", None) is panel:
                    self.app._init_detail = None
            elif kind == "responder" and hasattr(panel, "detach"):
                panel.detach()
                if getattr(self.app, "_responder_detail", None) is panel:
                    self.app._responder_detail = None
            panel.destroy()
        except tk.TclError:
            pass
        if not silent:
            self.app._refresh_init_summary()

    def _back_from_embed(self) -> None:
        self._close_embed(silent=False)
        self._render_page()

    def _cancel(self) -> None:
        self._close_embed(silent=True)
        self.destroy()

    def _iface_cmd_name(self) -> str:
        return (self.iface_cmd_var.get() or "").strip() or "can0"

    def _cmd_iface_create(self) -> None:
        app = self.app
        if hasattr(app, "_cmd_iface_create"):
            app._cmd_iface_create(self._iface_cmd_name(), parent=self)

    def _cmd_iface_up(self) -> None:
        app = self.app
        if hasattr(app, "_cmd_iface_up"):
            app._cmd_iface_up(self._iface_cmd_name(), parent=self)

    def _cmd_iface_down(self) -> None:
        app = self.app
        if hasattr(app, "_cmd_iface_down"):
            app._cmd_iface_down(self._iface_cmd_name(), parent=self)

    def _on_responder_toggle(self) -> None:
        # Kept for compatibility; enable is combo-driven.
        if self.enable_responder_var.get():
            self.listen_only_var.set(False)

    def _on_listen_only_toggle(self) -> None:
        if self.listen_only_var.get():
            self.enable_responder_var.set(False)
            # Selecting listen-only turns responder off via 「未选择」.
            try:
                self.responder_module_var.set(CHOICE_NONE)
                self._apply_responder_choice_ui()
                self.app._responder_module = None
                self.app._sync_responder_state_from_module()
            except Exception:
                pass

    def _fill_megmeet_ids(self) -> None:
        ids = f"{megmeet.PLUGIN_TX_ID:08X},{megmeet.WELDER_RX_ID:08X}"
        self.rx_filter_var.set(ids)

    def _ok(self) -> None:
        try:
            n = int(self.count_var.get())
            t = float(self.to_var.get())
            iv = int(float(self.default_iv_var.get()))
        except ValueError:
            messagebox.showerror(
                "无效", "回帧条数/超时/默认间隔必须是数字", parent=self
            )
            return
        init_key = self._resolve_module_choice()
        resp_key = self._resolve_responder_choice()
        # v1.7.26: enable flags always overwritten from combo (no separate checkbox).
        enable_init = bool(init_key)
        enable_resp = bool(resp_key)
        self.result = AppSettings(
            enable_init_on_connect=enable_init,
            init_module=init_key,
            default_interval_ms=iv,
            print_client_data=bool(self.print_var.get()),
            reply_count=n,
            reply_timeout_ms=t,
            rx_id_filter=self.rx_filter_var.get().strip(),
            rx_changes_only=bool(self.rx_changes_var.get()),
            show_self_echo=bool(self.show_self_echo_var.get()),
            listen_only_shared=bool(self.listen_only_var.get()),
            enable_responder=enable_resp,
            responder_module=resp_key,
        ).clamp()
        self._close_embed(silent=True)
        self.destroy()


class InitDetailPanel(ttk.Frame):
    """Init-module editor — embeddable in Settings or wrapped by InitDetailDialog."""

    def __init__(
        self, master: tk.Misc, app: "CanTesterApp", *, embedded: bool = False
    ) -> None:
        super().__init__(master)
        self.app = app
        self.embedded = embedded

        ttk.Label(self, text="初始化模块", style="Header.TLabel").pack(anchor=tk.W)

        irow = ttk.Frame(self)
        irow.pack(fill=tk.X, pady=(6, 4))
        ttk.Label(irow, text="模块").pack(side=tk.LEFT)
        self.init_combo = ttk.Combobox(
            irow, textvariable=app.init_var, state="readonly", width=28
        )
        self.init_combo.pack(side=tk.LEFT, padx=4)
        self.init_combo.bind(
            "<<ComboboxSelected>>", lambda _e: app._load_selected_module()
        )
        ttk.Button(
            irow, text="刷新", style="Toolbar.TButton", command=app._refresh_modules
        ).pack(side=tk.LEFT, padx=2)
        ttk.Button(
            irow,
            text="保存模块",
            style="Toolbar.TButton",
            command=app._save_working_module,
        ).pack(side=tk.LEFT, padx=2)
        ttk.Button(
            irow, text="另存为", style="Toolbar.TButton", command=app._save_module_as
        ).pack(side=tk.LEFT, padx=2)

        table_wrap = ttk.Frame(self)
        table_wrap.pack(fill=tk.BOTH, expand=True, pady=4)
        cols = ("enabled", "can_id", "data", "interval", "note")
        self.rows_tree = ttk.Treeview(
            table_wrap,
            columns=cols,
            show="headings",
            height=8 if embedded else 10,
            selectmode="browse",
            style="Treeview",
        )
        headings = {
            "enabled": ("启用", 44),
            "can_id": ("CAN ID", 90),
            "data": ("数据", 140),
            "interval": ("间隔ms", 60),
            "note": ("备注", 100),
        }
        for c, (text_h, w) in headings.items():
            self.rows_tree.heading(c, text=text_h)
            self.rows_tree.column(c, width=w, anchor=tk.W)
        sy = ttk.Scrollbar(table_wrap, orient=tk.VERTICAL, command=self.rows_tree.yview)
        self.rows_tree.configure(yscrollcommand=sy.set)
        self.rows_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        sy.pack(side=tk.RIGHT, fill=tk.Y)
        self.rows_tree.bind("<Double-1>", app._on_row_double_click)
        self.rows_tree.bind("<Button-1>", app._on_row_click)

        brow = ttk.Frame(self)
        brow.pack(fill=tk.X, pady=(4, 0))
        self.init_btn = ttk.Button(
            brow,
            text="发送勾选",
            style="Primary.TButton",
            command=app._run_checked_rows,
        )
        self.init_btn.pack(side=tk.LEFT)
        if app._init_busy:
            self.init_btn.configure(state=tk.DISABLED)
        ttk.Button(
            brow,
            text="发送选中",
            style="Secondary.TButton",
            command=app._run_selected_row,
        ).pack(side=tk.LEFT, padx=(8, 2))
        ttk.Button(
            brow, text="添加行", style="Secondary.TButton", command=app._add_row
        ).pack(side=tk.LEFT, padx=2)
        ttk.Button(
            brow, text="删除行", style="Secondary.TButton", command=app._delete_row
        ).pack(side=tk.LEFT, padx=2)
        ttk.Button(
            brow,
            text="上移",
            style="Secondary.TButton",
            command=lambda: app._move_row(-1),
        ).pack(side=tk.LEFT, padx=2)
        ttk.Button(
            brow,
            text="下移",
            style="Secondary.TButton",
            command=lambda: app._move_row(1),
        ).pack(side=tk.LEFT, padx=2)
        ttk.Button(
            brow,
            text="编辑",
            style="Secondary.TButton",
            command=app._edit_selected_row,
        ).pack(side=tk.LEFT, padx=2)

        self.init_status_var = app.init_status_var
        ttk.Label(self, textvariable=self.init_status_var, style="Muted.TLabel").pack(
            anchor=tk.W, pady=(6, 0)
        )

        app.rows_tree = self.rows_tree
        app.init_combo = self.init_combo
        app.init_btn = self.init_btn
        labels = [CHOICE_NONE] + [app._module_label(m) for m in app._init_modules]
        self.init_combo["values"] = labels
        if not (app.init_var.get() or "").strip():
            app.init_var.set(CHOICE_NONE)
        app._rebuild_rows_tree()

    def detach(self) -> None:
        app = self.app
        if app.rows_tree is self.rows_tree:
            app.rows_tree = None  # type: ignore[assignment]
        if app.init_combo is self.init_combo:
            app.init_combo = None  # type: ignore[assignment]
        if app.init_btn is self.init_btn:
            app.init_btn = None  # type: ignore[assignment]
        app._refresh_init_summary()

    def lift(self) -> None:  # type: ignore[override]
        try:
            self.master.winfo_toplevel().lift()
        except tk.TclError:
            pass

    def focus_set(self) -> None:  # type: ignore[override]
        try:
            super().focus_set()
        except tk.TclError:
            pass


class InitDetailDialog(tk.Toplevel):
    """Top-level wrapper for init editor (main-window「查看详情」)."""

    def __init__(self, app: "CanTesterApp") -> None:
        super().__init__(app)
        self.app = app
        self.title("初始化详情")
        self.configure(bg=BG)
        self.transient(app)
        self.geometry("520x560")
        self.minsize(480, 420)
        body = ttk.Frame(self, padding=EDGE)
        body.pack(fill=tk.BOTH, expand=True)
        self.panel = InitDetailPanel(body, app, embedded=False)
        self.panel.pack(fill=tk.BOTH, expand=True)
        self.protocol("WM_DELETE_WINDOW", self._on_close)
        self.bind("<Escape>", lambda _e: self._on_close())

    def _on_close(self) -> None:
        app = self.app
        if app._init_detail is self:
            app._init_detail = None
        self.panel.detach()
        self.destroy()


class ResponderRowEditDialog(tk.Toplevel):
    """Edit one 应答档 row."""

    def __init__(self, master: tk.Misc, row: ResponderRow) -> None:
        super().__init__(master)
        self.title("编辑应答行")
        self.resizable(False, False)
        self.result: ResponderRow | None = None
        self.configure(bg=BG)
        self.transient(master)
        try:
            self.grab_set()
        except tk.TclError:
            pass

        body = ttk.Frame(self, padding=EDGE)
        body.pack(fill=tk.BOTH, expand=True)

        self.enabled_var = tk.BooleanVar(value=row.enabled)
        self.id_var = tk.StringVar(
            value="" if int(getattr(row, "match_id", 0) or 0) == 0 else f"0x{row.match_id:X}"
        )
        self.ext_var = tk.BooleanVar(value=bool(row.extended))
        self.reply_var = tk.StringVar(value=format_data_hex(row.reply))
        self.delay_var = tk.StringVar(
            "" if row.delay_ms is None else str(int(row.delay_ms))
        )
        self.note_var = tk.StringVar(value=row.note or "")

        r = 0
        ttk.Checkbutton(body, text="启用", variable=self.enabled_var).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="匹配 ID").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.id_var, width=18).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Checkbutton(body, text="扩展帧", variable=self.ext_var).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="回帧 HEX").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.reply_var, width=36).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="延时 ms").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.delay_var, width=10).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="备注").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.note_var, width=36).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(
            body,
            text="空延时=档默认；engine=megmeet 时 HEX 为快照，运行时动态覆盖",
            style="Muted.TLabel",
            wraplength=360,
        ).grid(row=r, column=0, columnspan=2, sticky=tk.W, pady=(4, 8))
        r += 1
        btns = ttk.Frame(body)
        btns.grid(row=r, column=0, columnspan=2, sticky=tk.E)
        ttk.Button(
            btns, text="取消", style="Secondary.TButton", command=self.destroy
        ).pack(side=tk.RIGHT, padx=(6, 0))
        ttk.Button(
            btns, text="确定", style="Primary.TButton", command=self._ok
        ).pack(side=tk.RIGHT)
        self.geometry("420x300")

    def _ok(self) -> None:
        try:
            mid, _ = parse_can_id_auto(self.id_var.get().strip(), None)
            reply = parse_can_data(self.reply_var.get())
            delay_s = self.delay_var.get().strip()
            delay_i = None if delay_s == "" else int(float(delay_s))
            if delay_i is not None and delay_i < 0:
                delay_i = None
        except Exception as exc:
            messagebox.showerror("无效", str(exc), parent=self)
            return
        self.result = ResponderRow(
            enabled=bool(self.enabled_var.get()),
            match_id=int(mid),
            extended=bool(self.ext_var.get()) or mid > 0x7FF,
            reply=reply,
            delay_ms=delay_i,
            note=self.note_var.get().strip(),
        )
        self.destroy()



class SignalEditDialog(tk.Toplevel):
    """Edit one signal definition (名称/备注/规则/默认)."""

    def __init__(self, master: tk.Misc, sig: SignalDef | None = None) -> None:
        super().__init__(master)
        self.title("编辑信号")
        self.resizable(False, False)
        self.result: SignalDef | None = None
        self.configure(bg=BG)
        self.transient(master)
        try:
            self.grab_set()
        except tk.TclError:
            pass

        sig = sig or SignalDef()
        # Blank / new row → empty editable fields (not "0").
        blank = not (sig.name or "").strip() and not (sig.note or "").strip() and not (
            sig.key or ""
        ).strip()

        body = ttk.Frame(self, padding=EDGE)
        body.pack(fill=tk.BOTH, expand=True)

        kind0 = (sig.kind or "bit").strip().lower()
        if kind0 not in ("bit", "u8", "u16"):
            kind0 = "bit"
        self.name_var = tk.StringVar(value=sig.name or "")
        self.note_var = tk.StringVar(value=sig.note or "")
        self.kind_var = tk.StringVar(value=kind0)
        self.byte_var = tk.StringVar(
            value="" if blank else str(int(sig.byte))
        )
        self.bit_var = tk.StringVar(
            value="" if blank else str(int(sig.bit))
        )
        self.scale_var = tk.StringVar(
            value="" if blank else str(float(sig.scale) if sig.scale else 1.0)
        )
        self.key_var = tk.StringVar(value=sig.key or "")
        if kind0 == "bit":
            self.default_bit_var = tk.BooleanVar(
                value=False if blank else bool(sig.default)
            )
            self.default_num_var = tk.StringVar(value="")
        else:
            self.default_bit_var = tk.BooleanVar(value=False)
            self.default_num_var = tk.StringVar(
                value="" if blank else str(sig.default)
            )

        r = 0
        ttk.Label(body, text="名称").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.name_var, width=24).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="备注").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.note_var, width=36).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(body, text="类型").grid(row=r, column=0, sticky=tk.W, pady=2)
        kind_cb = ttk.Combobox(
            body,
            textvariable=self.kind_var,
            values=("bit", "u8", "u16"),
            state="readonly",
            width=10,
        )
        kind_cb.grid(row=r, column=1, sticky=tk.W, pady=2)
        kind_cb.bind("<<ComboboxSelected>>", lambda _e: self._sync_kind_ui())
        r += 1
        ttk.Label(body, text="字节").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.byte_var, width=8).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        self._bit_label = ttk.Label(body, text="位")
        self._bit_label.grid(row=r, column=0, sticky=tk.W, pady=2)
        self._bit_entry = ttk.Entry(body, textvariable=self.bit_var, width=8)
        self._bit_entry.grid(row=r, column=1, sticky=tk.W, pady=2)
        self._bit_row = r
        r += 1
        self._scale_label = ttk.Label(body, text="scale")
        self._scale_label.grid(row=r, column=0, sticky=tk.W, pady=2)
        self._scale_entry = ttk.Entry(body, textvariable=self.scale_var, width=8)
        self._scale_entry.grid(row=r, column=1, sticky=tk.W, pady=2)
        r += 1
        self._def_bit_cb = ttk.Checkbutton(
            body, text="默认勾选", variable=self.default_bit_var
        )
        self._def_bit_cb.grid(row=r, column=1, sticky=tk.W, pady=2)
        r += 1
        self._def_num_label = ttk.Label(body, text="默认值")
        self._def_num_label.grid(row=r, column=0, sticky=tk.W, pady=2)
        self._def_num_entry = ttk.Entry(body, textvariable=self.default_num_var, width=12)
        self._def_num_entry.grid(row=r, column=1, sticky=tk.W, pady=2)
        r += 1
        ttk.Label(body, text="key").grid(row=r, column=0, sticky=tk.W, pady=2)
        ttk.Entry(body, textvariable=self.key_var, width=16).grid(
            row=r, column=1, sticky=tk.W, pady=2
        )
        r += 1
        ttk.Label(
            body,
            text="key 可选：同步 megmeet ResponderState（ready/error/current…）",
            style="Muted.TLabel",
            wraplength=360,
        ).grid(row=r, column=0, columnspan=2, sticky=tk.W, pady=(4, 8))
        r += 1
        btns = ttk.Frame(body)
        btns.grid(row=r, column=0, columnspan=2, sticky=tk.E)
        ttk.Button(
            btns, text="取消", style="Secondary.TButton", command=self._cancel
        ).pack(side=tk.RIGHT, padx=(6, 0))
        ttk.Button(
            btns, text="确定", style="Primary.TButton", command=self._ok
        ).pack(side=tk.RIGHT)
        self._sync_kind_ui()
        self.geometry("420x360")
        self.protocol("WM_DELETE_WINDOW", self._cancel)

    def _sync_kind_ui(self) -> None:
        k = (self.kind_var.get() or "bit").strip().lower()
        try:
            if k == "bit":
                self._bit_label.grid()
                self._bit_entry.grid()
                self._bit_entry.configure(state=tk.NORMAL)
                self._def_bit_cb.configure(state=tk.NORMAL)
                self._scale_entry.configure(state=tk.DISABLED)
                self._def_num_entry.configure(state=tk.DISABLED)
            else:
                # Bit field only for bit kind.
                self._bit_label.grid_remove()
                self._bit_entry.grid_remove()
                self._def_bit_cb.configure(state=tk.DISABLED)
                self._scale_entry.configure(state=tk.NORMAL)
                self._def_num_entry.configure(state=tk.NORMAL)
        except tk.TclError:
            pass

    def _cancel(self) -> None:
        self.result = None
        try:
            self.grab_release()
        except tk.TclError:
            pass
        self.destroy()

    def _ok(self) -> None:
        try:
            kind = (self.kind_var.get() or "bit").strip().lower()
            if kind not in ("bit", "u8", "u16"):
                kind = "bit"
            byte_s = self.byte_var.get().strip()
            byte_i = max(0, min(7, int(float(byte_s)))) if byte_s else 0
            bit_s = self.bit_var.get().strip()
            bit_i = max(0, min(7, int(float(bit_s)))) if bit_s else 0
            if kind != "bit":
                bit_i = 0
            scale_s = self.scale_var.get().strip()
            scale = float(scale_s) if scale_s else 1.0
            if not scale:
                scale = 1.0
            if kind == "bit":
                default: object = bool(self.default_bit_var.get())
            else:
                num_s = self.default_num_var.get().strip()
                default = float(num_s) if num_s else 0.0
                if kind == "u8" and float(default) == int(default):
                    default = int(default)
            self.result = SignalDef(
                name=self.name_var.get().strip(),
                note=self.note_var.get().strip(),
                kind=kind,
                byte=byte_i,
                bit=bit_i,
                scale=scale,
                default=default,
                key=self.key_var.get().strip(),
            )
        except Exception as exc:
            messagebox.showerror("无效", str(exc), parent=self)
            return
        try:
            self.grab_release()
        except tk.TclError:
            pass
        self.destroy()


class ResponderDetailPanel(ttk.Frame):
    """应答档 editor — embeddable in Settings or wrapped by ResponderDetailDialog."""

    def __init__(
        self,
        master: tk.Misc,
        app: "CanTesterApp",
        preselect: str = "",
        *,
        embedded: bool = False,
    ) -> None:
        super().__init__(master)
        self.app = app
        self.embedded = embedded

        ttk.Label(self, text="应答档", style="Header.TLabel").pack(anchor=tk.W)

        irow = ttk.Frame(self)
        irow.pack(fill=tk.X, pady=(6, 4))
        ttk.Label(irow, text="档").pack(side=tk.LEFT)
        self.profile_var = tk.StringVar()
        self.profile_combo = ttk.Combobox(
            irow, textvariable=self.profile_var, state="readonly", width=28
        )
        self.profile_combo.pack(side=tk.LEFT, padx=4)
        self.profile_combo.bind("<<ComboboxSelected>>", lambda _e: self._load_selected())
        ttk.Button(
            irow, text="刷新", style="Toolbar.TButton", command=self._refresh_list
        ).pack(side=tk.LEFT, padx=2)
        ttk.Button(
            irow, text="保存档", style="Toolbar.TButton", command=self._save
        ).pack(side=tk.LEFT, padx=2)
        ttk.Button(
            irow, text="另存为", style="Toolbar.TButton", command=self._save_as
        ).pack(side=tk.LEFT, padx=2)

        table = ttk.Frame(self)
        table.pack(fill=tk.BOTH, expand=True, pady=4)
        cols = ("enabled", "match_id", "reply", "delay", "note")
        self.tree = ttk.Treeview(
            table,
            columns=cols,
            show="headings",
            height=8 if embedded else 10,
            selectmode="browse",
        )
        for c, (txt, w) in {
            "enabled": ("启用", 44),
            "match_id": ("匹配 ID", 90),
            "reply": ("回帧 HEX", 140),
            "delay": ("延时ms", 60),
            "note": ("备注", 100),
        }.items():
            self.tree.heading(c, text=txt)
            self.tree.column(c, width=w, anchor=tk.W)
        sy = ttk.Scrollbar(table, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=sy.set)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        sy.pack(side=tk.RIGHT, fill=tk.Y)
        self.tree.bind("<Double-1>", lambda _e: self._edit_row())
        self.tree.bind("<Button-1>", self._on_click)

        brow = ttk.Frame(self)
        brow.pack(fill=tk.X, pady=(4, 0))
        for text, cmd in (
            ("添加行", self._add_row),
            ("删除行", self._delete_row),
            ("上移", lambda: self._move(-1)),
            ("下移", lambda: self._move(1)),
            ("编辑", self._edit_row),
        ):
            ttk.Button(brow, text=text, style="Secondary.TButton", command=cmd).pack(
                side=tk.LEFT, padx=2
            )
        ttk.Button(
            brow,
            text="发一帧应答",
            style="Primary.TButton",
            command=self.app._manual_send_responder_frame,
        ).pack(side=tk.RIGHT)

        # --- 信号 section (JSON-driven) ---
        ttk.Label(self, text="信号", style="Header.TLabel").pack(
            anchor=tk.W, pady=(10, 2)
        )
        sig_table = ttk.Frame(self)
        sig_table.pack(fill=tk.BOTH, expand=True, pady=2)
        sig_cols = ("default", "name", "note", "rule", "default_val")
        self.sig_tree = ttk.Treeview(
            sig_table,
            columns=sig_cols,
            show="headings",
            height=5 if self.embedded else 6,
            selectmode="browse",
        )
        for c, (txt, w) in {
            "default": ("默认勾选", 56),
            "name": ("名称", 72),
            "note": ("备注", 120),
            "rule": ("规则", 72),
            "default_val": ("默认值", 64),
        }.items():
            self.sig_tree.heading(c, text=txt)
            self.sig_tree.column(c, width=w, anchor=tk.W)
        sy2 = ttk.Scrollbar(sig_table, orient=tk.VERTICAL, command=self.sig_tree.yview)
        self.sig_tree.configure(yscrollcommand=sy2.set)
        self.sig_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        sy2.pack(side=tk.RIGHT, fill=tk.Y)
        self.sig_tree.bind("<Double-1>", lambda _e: self._edit_signal())
        self.sig_tree.bind("<Button-1>", self._on_sig_click)

        sbrow = ttk.Frame(self)
        sbrow.pack(fill=tk.X, pady=(4, 0))
        for text, cmd in (
            ("添加行", self._add_signal),
            ("删除", self._delete_signal),
            ("上移", lambda: self._move_signal(-1)),
            ("下移", lambda: self._move_signal(1)),
            ("编辑", self._edit_signal),
        ):
            ttk.Button(sbrow, text=text, style="Secondary.TButton", command=cmd).pack(
                side=tk.LEFT, padx=2
            )

        self.status_var = tk.StringVar(value="")
        ttk.Label(self, textvariable=self.status_var, style="Muted.TLabel").pack(
            anchor=tk.W, pady=(6, 0)
        )

        self._working: ResponderModule | None = None
        self._modules: list[ResponderModule] = []
        self._refresh_list()
        if preselect:
            self._select_key(preselect)
        elif self._modules:
            self.profile_combo.current(0)
            self._load_selected()

    def detach(self) -> None:
        pass

    def lift(self) -> None:  # type: ignore[override]
        try:
            self.master.winfo_toplevel().lift()
        except tk.TclError:
            pass

    def focus_set(self) -> None:  # type: ignore[override]
        try:
            super().focus_set()
        except tk.TclError:
            pass

    def _refresh_list(self) -> None:
        self._modules = list_responder_modules()
        labels = [
            (m.name or "").strip() or (m.path.stem if m.path else "")
            for m in self._modules
        ]
        self.profile_combo["values"] = labels
        self.status_var.set(f"共 {len(self._modules)} 个应答档")

    def _select_key(self, key: str) -> None:
        key = (key or "").strip()
        for i, m in enumerate(self._modules):
            stem = m.path.stem if m.path else ""
            if m.name == key or stem == key:
                self.profile_combo.current(i)
                self._load_selected()
                return

    def _load_selected(self) -> None:
        idx = self.profile_combo.current()
        if idx < 0 or idx >= len(self._modules):
            return
        mod = self._modules[idx]
        try:
            self._working = load_responder_module(mod.path) if mod.path else mod
        except Exception as exc:
            messagebox.showerror("加载失败", str(exc), parent=self.winfo_toplevel())
            return
        self._rebuild()
        self.status_var.set(
            f"{self._working.name} · engine={self._working.engine or 'static'} · "
            f"行 {len(self._working.rows)} · 信号 {len(self._working.signals)}"
        )
        self.app._responder_module = self._working
        self.app._sync_responder_state_from_module()
        if hasattr(self.app, "_refresh_signal_card"):
            self.app._refresh_signal_card()

    def _rebuild(self) -> None:
        for i in self.tree.get_children():
            self.tree.delete(i)
        for i in self.sig_tree.get_children():
            self.sig_tree.delete(i)
        if not self._working:
            return
        for r in self._working.rows:
            self.tree.insert(
                "",
                tk.END,
                values=(
                    "✓" if r.enabled else "",
                    f"0x{r.match_id:X}",
                    format_data_hex(r.reply),
                    "" if r.delay_ms is None else str(r.delay_ms),
                    r.note or "",
                ),
            )
        for s in self._working.signals:
            if (s.kind or "").lower() == "bit":
                def_chk = "✓" if bool(s.default) else ""
                def_val = "true" if bool(s.default) else "false"
            else:
                def_chk = ""
                def_val = str(s.default)
            self.sig_tree.insert(
                "",
                tk.END,
                values=(
                    def_chk,
                    s.name or "",
                    s.note or "",
                    s.rule_summary(),
                    def_val,
                ),
            )

    def _selected_index(self) -> int | None:
        sel = self.tree.selection()
        if not sel:
            return None
        return int(self.tree.index(sel[0]))

    def _on_click(self, event) -> None:
        if self.tree.identify_region(event.x, event.y) != "cell":
            return
        if self.tree.identify_column(event.x) != "#1":
            return
        row_id = self.tree.identify_row(event.y)
        if not row_id or not self._working:
            return
        idx = self.tree.index(row_id)
        rows = list(self._working.rows)
        if 0 <= idx < len(rows):
            rows[idx].enabled = not rows[idx].enabled
            self._working = self._working.with_rows(rows)
            self._rebuild()

    def _add_row(self) -> None:
        if not self._working:
            messagebox.showwarning(
                "无应答档", "请先选择一个应答档", parent=self.winfo_toplevel()
            )
            return
        # Immediate blank row (no modal). Edit via double-click / 「编辑」.
        blank = ResponderRow(
            enabled=True,
            match_id=0,
            extended=True,
            reply=b"",
            delay_ms=None,
            note="",
        )
        self._working = self._working.with_rows(list(self._working.rows) + [blank])
        self._rebuild()
        kids = self.tree.get_children()
        if kids:
            self.tree.selection_set(kids[-1])

    def _delete_row(self) -> None:
        idx = self._selected_index()
        if idx is None or not self._working:
            return
        rows = list(self._working.rows)
        del rows[idx]
        self._working = self._working.with_rows(rows)
        self._rebuild()

    def _move(self, delta: int) -> None:
        idx = self._selected_index()
        if idx is None or not self._working:
            return
        rows = list(self._working.rows)
        j = idx + delta
        if j < 0 or j >= len(rows):
            return
        rows[idx], rows[j] = rows[j], rows[idx]
        self._working = self._working.with_rows(rows)
        self._rebuild()
        kids = self.tree.get_children()
        self.tree.selection_set(kids[j])

    def _edit_row(self) -> None:
        idx = self._selected_index()
        if idx is None or not self._working:
            return
        dlg = ResponderRowEditDialog(self.winfo_toplevel(), self._working.rows[idx])
        self.wait_window(dlg)
        if dlg.result is None:
            return
        rows = list(self._working.rows)
        rows[idx] = dlg.result
        self._working = self._working.with_rows(rows)
        self._rebuild()

    def _selected_sig_index(self) -> int | None:
        sel = self.sig_tree.selection()
        if not sel:
            return None
        return int(self.sig_tree.index(sel[0]))

    def _on_sig_click(self, event) -> None:
        if self.sig_tree.identify_region(event.x, event.y) != "cell":
            return
        if self.sig_tree.identify_column(event.x) != "#1":
            return
        row_id = self.sig_tree.identify_row(event.y)
        if not row_id or not self._working:
            return
        idx = self.sig_tree.index(row_id)
        sigs = list(self._working.signals)
        if 0 <= idx < len(sigs) and (sigs[idx].kind or "").lower() == "bit":
            sigs[idx].default = not bool(sigs[idx].default)
            self._working = self._working.with_signals(sigs)
            self._rebuild()

    def _add_signal(self) -> None:
        if not self._working:
            messagebox.showwarning(
                "无应答档", "请先选择一个应答档", parent=self.winfo_toplevel()
            )
            return
        blank = SignalDef(
            name="",
            note="",
            kind="bit",
            byte=0,
            bit=0,
            default=False,
            key="",
        )
        self._working = self._working.with_signals(list(self._working.signals) + [blank])
        self._rebuild()
        kids = self.sig_tree.get_children()
        if kids:
            self.sig_tree.selection_set(kids[-1])

    def _delete_signal(self) -> None:
        idx = self._selected_sig_index()
        if idx is None or not self._working:
            return
        sigs = list(self._working.signals)
        del sigs[idx]
        self._working = self._working.with_signals(sigs)
        self._rebuild()

    def _move_signal(self, delta: int) -> None:
        idx = self._selected_sig_index()
        if idx is None or not self._working:
            return
        sigs = list(self._working.signals)
        j = idx + delta
        if j < 0 or j >= len(sigs):
            return
        sigs[idx], sigs[j] = sigs[j], sigs[idx]
        self._working = self._working.with_signals(sigs)
        self._rebuild()
        kids = self.sig_tree.get_children()
        self.sig_tree.selection_set(kids[j])

    def _edit_signal(self) -> None:
        idx = self._selected_sig_index()
        if idx is None or not self._working:
            return
        dlg = SignalEditDialog(self.winfo_toplevel(), self._working.signals[idx])
        self.wait_window(dlg)
        if dlg.result is None:
            return
        sigs = list(self._working.signals)
        sigs[idx] = dlg.result
        self._working = self._working.with_signals(sigs)
        self._rebuild()

    @staticmethod
    def _path_under_package_profiles(p: Path | None) -> bool:
        if p is None:
            return False
        try:
            pkg = package_responder_dir().resolve()
            resolved = p.resolve()
            return pkg == resolved.parent or pkg in resolved.parents
        except OSError:
            return False

    def _user_dest_for_working(self) -> Path:
        """Resolve writable dest; factory profiles/ → ~/.can_tester/responder/ copy."""
        USER_RESPONDER_DIR.mkdir(parents=True, exist_ok=True)
        if self._working is None:
            raise ValueError("无应答档")
        if self._working.path is None:
            stem = re.sub(r"[^\w\-]+", "_", self._working.name) or "responder"
            return USER_RESPONDER_DIR / f"{stem}.json"
        if self._path_under_package_profiles(self._working.path):
            return USER_RESPONDER_DIR / self._working.path.name
        return self._working.path

    def _save(self) -> None:
        if not self._working:
            return
        try:
            dest = self._user_dest_for_working()
            save_responder_module(self._working, dest)
            # save_responder_module updates mod.path to dest (user copy if factory).
            self.status_var.set(f"已保存: {dest}")
            self.app._responder_module = self._working
            self.app._sync_responder_state_from_module()
            if hasattr(self.app, "_refresh_signal_card"):
                self.app._refresh_signal_card()
            self._refresh_list()
        except Exception as exc:
            messagebox.showerror("保存失败", str(exc), parent=self.winfo_toplevel())

    def _save_as(self) -> None:
        if not self._working:
            return
        name = simpledialog.askstring("另存为", "档名:", parent=self.winfo_toplevel())
        if not name:
            return
        USER_RESPONDER_DIR.mkdir(parents=True, exist_ok=True)
        stem = re.sub(r"[^\w\-]+", "_", name.strip()) or "responder"
        dest = USER_RESPONDER_DIR / f"{stem}.json"
        try:
            self._working.name = name.strip() or self._working.name
            save_responder_module(self._working, dest)
            self.status_var.set(f"已另存为: {dest}")
            self.app._responder_module = self._working
            self.app._sync_responder_state_from_module()
            if hasattr(self.app, "_refresh_signal_card"):
                self.app._refresh_signal_card()
            self._refresh_list()
            self._select_key(stem)
        except Exception as exc:
            messagebox.showerror("保存失败", str(exc), parent=self.winfo_toplevel())


class ResponderDetailDialog(tk.Toplevel):
    """Top-level wrapper (kept for non-settings callers; prefer embed in Settings)."""

    def __init__(self, app: "CanTesterApp", preselect: str = "") -> None:
        super().__init__(app)
        self.app = app
        self.title("应答档")
        self.configure(bg=BG)
        self.transient(app)
        self.geometry("640x640")
        self.minsize(500, 420)
        body = ttk.Frame(self, padding=EDGE)
        body.pack(fill=tk.BOTH, expand=True)
        self.panel = ResponderDetailPanel(
            body, app, preselect=preselect, embedded=False
        )
        self.panel.pack(fill=tk.BOTH, expand=True)
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    def _select_key(self, key: str) -> None:
        self.panel._select_key(key)

    def _on_close(self) -> None:
        if getattr(self.app, "_responder_detail", None) is self:
            self.app._responder_detail = None
        self.panel.detach()
        self.destroy()




class CanTesterApp(tk.Tk):
    def __init__(self, initial_iface: str = DEFAULT_IFACE) -> None:
        super().__init__()
        self.title("CAN 通用监听 / 发送")
        self.geometry("980x620")
        self.minsize(860, 480)
        self.configure(bg=BG)
        self._style = _apply_app_style(self)
        self.session = CanSession()
        self._pause = tk.BooleanVar(value=False)
        self._period_job = None
        self._period_running = False
        self._period_fail_count = 0
        self._init_modules: list[InitModule] = []
        self._working_module: InitModule | None = None
        self._init_busy = False
        self._connect_init_count: int | None = None  # frames sent by auto-init this session
        self.settings = load_settings()
        self._responder_state = ResponderState()
        self._responder_module: ResponderModule | None = None
        self._responder_detail: ResponderDetailDialog | None = None
        self._signal_values: dict = {}
        self._signal_vars: dict = {}
        self._await_replies = 0
        self._await_deadline = 0.0
        self._await_got = 0
        # Per-(direction, can_id, extended) last payload for 「仅显示变化」
        self._last_log_by_key: dict[tuple[str, int, bool], bytes] = {}
        self._tx_led_job = None
        self._rx_led_job = None
        self._tx_led_pending = False
        self._rx_led_pending = False
        self._tx_led_last = 0.0
        self._rx_led_last = 0.0
        self._build(initial_iface)
        self._reload_responder_profile()
        self._drop_saved_responder_for_generic()
        self.after(80, self._poll)
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    def _build(self, initial_iface: str) -> None:
        # Menubar — settings only via top bar/menu
        menubar = tk.Menu(self)
        self.config(menu=menubar)
        m_file = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="文件", menu=m_file)
        m_file.add_command(label="设置…", command=self._open_settings)
        m_file.add_separator()
        m_file.add_command(label="退出", command=self._on_close)
        m_help = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="帮助", menu=m_help)
        m_help.add_command(
            label="关于",
            command=lambda: messagebox.showinfo(
                "关于",
                f"CAN 通用监听 / 发送\nUI v{__version__}\n协议: socketcan",
                parent=self,
            ),
        )

        root = ttk.Frame(self, padding=10)
        root.pack(fill=tk.BOTH, expand=True)

        # ----- 1. Connection bar -----
        conn_wrap = ttk.Frame(root)
        conn_wrap.pack(fill=tk.X, pady=(0, 8))
        conn = ttk.Frame(conn_wrap)
        conn.pack(fill=tk.X)

        ttk.Label(conn, text="接口").pack(side=tk.LEFT)
        ifaces = list_can_interfaces()
        if initial_iface and initial_iface not in ifaces:
            ifaces.insert(0, initial_iface)
        self.iface_var = tk.StringVar(
            value=initial_iface if initial_iface in ifaces else ifaces[0]
        )
        self.iface_combo = ttk.Combobox(
            conn, textvariable=self.iface_var, values=ifaces, width=12
        )
        self.iface_combo.pack(side=tk.LEFT, padx=(6, 4))
        self.iface_combo.bind("<<ComboboxSelected>>", lambda _e: self._refresh_idle_iface_hint())
        ttk.Button(
            conn, text="刷新", style="Toolbar.TButton", command=self._refresh_ifaces
        ).pack(side=tk.LEFT)

        ttk.Label(conn, text="波特率").pack(side=tk.LEFT, padx=(14, 0))
        self.baud_var = tk.StringVar(value="125000")
        self.baud_combo = ttk.Combobox(
            conn,
            textvariable=self.baud_var,
            values=BAUD_CHOICES,
            width=10,
            state="readonly",
        )
        self.baud_combo.pack(side=tk.LEFT, padx=(6, 4))
        # SocketCAN bitrate is OS-configured; UI shows intended rate (layout only).

        self.open_btn = ttk.Button(
            conn, text="开总线", style="Primary.TButton", command=self._open_conn
        )
        self.open_btn.pack(side=tk.LEFT, padx=(12, 4))
        self.close_btn = ttk.Button(
            conn,
            text="闭总线",
            style="Secondary.TButton",
            command=self._close_conn,
            state=tk.DISABLED,
        )
        self.close_btn.pack(side=tk.LEFT, padx=2)

        # Right cluster: settings (rightmost) then TX/RX lights to its left
        ttk.Button(
            conn, text="设置", style="Toolbar.TButton", command=self._open_settings
        ).pack(side=tk.RIGHT)
        self._build_txrx_leds(conn)

        self.status_var = tk.StringVar(value="未连接 — 默认通用监听")
        self._status_full = "未连接 — 默认通用监听"
        self.status_label = ttk.Label(
            conn, textvariable=self.status_var, style="StatusMuted.TLabel"
        )
        self.status_label.pack(side=tk.LEFT, padx=(12, 0), fill=tk.X, expand=True)
        self.status_label.bind("<Enter>", self._on_status_enter)
        self.status_label.bind("<Leave>", self._on_status_leave)
        self.status_label.bind("<Button-1>", self._on_status_click)

        # ----- 2. Init summary (only when init module selected) -----
        self.init_var = tk.StringVar(value="")
        self.init_status_var = tk.StringVar(
            value="未运行 — 初始化模块选「未选择」则打开总线后直接监听"
        )
        self.init_summary_var = tk.StringVar(value="初始化 · — · 已启用 0/0")
        self._init_detail: InitDetailDialog | None = None
        self.rows_tree = None  # type: ignore[assignment]
        self.init_combo = None  # type: ignore[assignment]
        self.init_btn = None  # type: ignore[assignment]

        # Placeholder host so summary packs above send area with correct order
        self._init_summary_host = ttk.Frame(root)
        self._init_summary_card = CardFrame(self._init_summary_host)
        sum_body = tk.Frame(self._init_summary_card.body, bg=BG, height=40)
        sum_body.pack(fill=tk.X)
        sum_body.pack_propagate(False)
        sum_inner = ttk.Frame(sum_body)
        sum_inner.pack(fill=tk.BOTH, expand=True, padx=10)
        ttk.Label(sum_inner, textvariable=self.init_summary_var).pack(
            side=tk.LEFT, anchor=tk.W
        )
        btns = ttk.Frame(sum_inner)
        btns.pack(side=tk.RIGHT, anchor=tk.E)
        ttk.Button(
            btns,
            text="发送",
            style="Primary.TButton",
            command=self._run_checked_rows,
        ).pack(side=tk.LEFT, padx=(0, 6))
        ttk.Button(
            btns,
            text="查看详情",
            style="Secondary.TButton",
            command=self._open_init_detail,
        ).pack(side=tk.LEFT)

        self._refresh_modules()

        # ----- 2b. Signal card (between init summary and send; 美工 lock) -----
        self._signal_host = ttk.Frame(root)
        self._signal_card = CardFrame(self._signal_host)
        sig_outer = tk.Frame(self._signal_card.body, bg=BG)
        sig_outer.pack(fill=tk.X, padx=10, pady=(8, 8))
        # Title row: 「信号」 left; 「自动回起弧成功」 text+checkbox on RIGHT (v1.7.30)
        self._signal_title_row = ttk.Frame(sig_outer)
        self._signal_title_row.pack(fill=tk.X)
        ttk.Label(self._signal_title_row, text="信号", style="Muted.TLabel").pack(
            side=tk.LEFT, anchor=tk.W
        )
        self.auto_arc_var = tk.BooleanVar(
            value=bool(getattr(self._responder_state, "auto_arc", True))
        )
        auto_wrap = ttk.Frame(self._signal_title_row)
        auto_wrap.pack(side=tk.RIGHT, anchor=tk.E)
        ttk.Label(auto_wrap, text="自动回起弧成功").pack(side=tk.LEFT)
        self._auto_arc_cb = ttk.Checkbutton(
            auto_wrap,
            variable=self.auto_arc_var,
            command=self._on_auto_arc_toggled,
        )
        self._auto_arc_cb.pack(side=tk.LEFT, padx=(4, 0))
        self._signal_controls = ttk.Frame(sig_outer)
        self._signal_controls.pack(fill=tk.X, pady=(SIGNAL_GAP, 0))

        # ----- 3. Receive | send, side by side -----
        self._mid_row = ttk.Frame(root)
        self._mid_row.pack(fill=tk.X, pady=(0, 8))
        self._mid_row.columnconfigure(0, weight=1, uniform="work")
        self._mid_row.columnconfigure(1, weight=1, uniform="work")
        self._mid_row.rowconfigure(0, weight=1)
        self._build_rx_signal_panel(self._mid_row)

        send_card = CardFrame(self._mid_row)
        self._send_card = send_card
        send_card.grid(row=0, column=0, columnspan=2, sticky="nsew")
        # Init summary / signals pack before send when enabled
        self._refresh_init_summary()
        self._refresh_signal_card()
        mid = ttk.Frame(send_card.body, padding=(8, 6))
        mid.pack(fill=tk.BOTH, expand=True)

        ttk.Label(mid, text="发送", style="Header.TLabel").pack(anchor=tk.W)

        form = ttk.Frame(mid)
        form.pack(fill=tk.X, pady=(4, 0))
        ttk.Label(form, text="配置组合").pack(side=tk.LEFT)
        self.profile_var = tk.StringVar(value=CHOICE_NONE)
        self.profile_combo = ttk.Combobox(
            form,
            textvariable=self.profile_var,
            values=[CHOICE_NONE, COMBO_MEGMEET_GOO],
            state="readonly",
            width=16,
        )
        self.profile_combo.pack(side=tk.LEFT, padx=(6, 0))
        self.profile_combo.bind("<<ComboboxSelected>>", self._on_profile)
        self._role_label = ttk.Label(form, text="角色")
        self._role_label.pack(side=tk.LEFT, padx=(12, 0))
        self._role_var = tk.StringVar(value=ROLE_CLIENT)
        self._role_combo = ttk.Combobox(
            form,
            textvariable=self._role_var,
            values=[ROLE_CLIENT, ROLE_WELDER],
            state="readonly",
            width=10,
        )
        self._role_combo.pack(side=tk.LEFT, padx=(6, 0))
        self._role_combo.bind("<<ComboboxSelected>>", self._on_profile)
        self._send_init_btn = ttk.Button(
            form,
            text="发送初始化",
            style="Secondary.TButton",
            command=self._send_goo_init,
        )
        self._send_init_btn.pack(side=tk.LEFT, padx=(12, 0))
        self._role_label.pack_forget()
        self._role_combo.pack_forget()
        self._send_init_btn.pack_forget()
        self._build_goo_send_panels(mid)

        self._hex_row = ttk.Frame(mid)
        self._hex_row.pack(fill=tk.X, pady=(4, 0))
        ttk.Label(self._hex_row, text="CAN ID").pack(side=tk.LEFT)
        self.id_var = tk.StringVar(value="0x123")
        ttk.Entry(self._hex_row, textvariable=self.id_var, width=12).pack(
            side=tk.LEFT, padx=(6, 0)
        )
        self.ext_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(self._hex_row, text="扩展帧", variable=self.ext_var).pack(
            side=tk.LEFT, padx=(8, 0)
        )
        ttk.Label(self._hex_row, text="数据").pack(side=tk.LEFT, padx=(8, 0))
        self.data_var = tk.StringVar(value="01 02 03 04")
        ttk.Entry(self._hex_row, textvariable=self.data_var).pack(
            side=tk.LEFT, padx=(6, 0), fill=tk.X, expand=True
        )

        act = ttk.Frame(mid)
        act.pack(fill=tk.X, pady=(4, 0))
        ttk.Button(
            act, text="单次发送", style="Primary.TButton", command=self._send_once
        ).pack(side=tk.LEFT)
        ttk.Label(act, text="周期").pack(side=tk.LEFT, padx=(12, 0))
        self.period_var = tk.StringVar(value="100")
        ttk.Entry(act, textvariable=self.period_var, width=6).pack(side=tk.LEFT, padx=(6, 0))
        self.period_btn = ttk.Button(
            act,
            text="开始周期",
            style="Secondary.TButton",
            command=self._toggle_period,
        )
        self.period_btn.pack(side=tk.LEFT, padx=(8, 0))

        # Hidden single-ID filter (kept working; not piled on main — synced empty)
        self.filter_var = tk.StringVar(value="")

        # ----- 4. Timeline -----
        log_card = CardFrame(root)
        log_card.pack(fill=tk.BOTH, expand=True)
        logf = ttk.Frame(log_card.body, padding=(8, 6))
        logf.pack(fill=tk.BOTH, expand=True)

        head = ttk.Frame(logf)
        head.pack(fill=tk.X)
        ttk.Label(head, text="时间线", style="Header.TLabel").pack(side=tk.LEFT)
        ttk.Button(
            head, text="清空", style="Toolbar.TButton", command=self._clear
        ).pack(side=tk.RIGHT)
        ttk.Checkbutton(head, text="暂停滚动", variable=self._pause).pack(
            side=tk.RIGHT, padx=8
        )
        self._changes_only_var = tk.BooleanVar(
            value=bool(self.settings.rx_changes_only)
        )
        ttk.Checkbutton(
            head,
            text="仅显示变化",
            variable=self._changes_only_var,
            command=self._on_changes_only_toggle,
        ).pack(side=tk.RIGHT)

        tree_wrap = tk.Frame(logf, bg=BG)
        tree_wrap.pack(fill=tk.BOTH, expand=True, pady=(6, 0))

        cols2 = ("time", "dir", "can_id", "data", "note")
        self.tree = ttk.Treeview(
            tree_wrap,
            columns=cols2,
            show="headings",
            height=8,
            style="Mono.Treeview",
        )
        headings2 = {
            "time": ("时间", 90),
            "dir": ("方向", 56),
            "can_id": ("ID", 120),
            "data": ("数据", 240),
            "note": ("备注", 280),
        }
        for c, (text, w) in headings2.items():
            self.tree.heading(c, text=text)
            self.tree.column(c, width=w, anchor=tk.W)
        sy2 = ttk.Scrollbar(tree_wrap, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=sy2.set)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        sy2.pack(side=tk.RIGHT, fill=tk.Y)

        self._empty_label = tk.Label(
            tree_wrap,
            text="未开总线",
            fg=SECONDARY_FG,
            bg="#FFFFFF",
            font=FONT_UI,
        )
        self._empty_label.place(relx=0.5, rely=0.5, anchor=tk.CENTER)
        self.tree.bind("<Configure>", lambda _e: self._update_empty_state())
        self._update_empty_state()

        # Hint var retained for settings sync (not shown on main — settings only)
        self.print_hint_var = tk.StringVar()
        self._refresh_print_hint()

    def _update_empty_state(self) -> None:
        kids = self.tree.get_children()
        if kids:
            self._empty_label.place_forget()
            return
        if self.session.connected:
            self._empty_label.configure(text="无帧")
        else:
            self._empty_label.configure(text="未开总线")
        self._empty_label.place(relx=0.5, rely=0.5, anchor=tk.CENTER)
        self._empty_label.lift()

    def _set_conn_buttons(self, connected: bool) -> None:
        if connected:
            self.open_btn.configure(state=tk.DISABLED)
            self.close_btn.configure(state=tk.NORMAL)
            self.iface_combo.configure(state="disabled")
            self.baud_combo.configure(state="disabled")
        else:
            self.open_btn.configure(state=tk.NORMAL)
            self.close_btn.configure(state=tk.DISABLED)
            self.iface_combo.configure(state="normal")
            self.baud_combo.configure(state="readonly")

    # ----- settings / print filter -----

    def _module_choice_keys(self) -> list[str]:
        """One entry per module — use display name (fallback stem). Never list both."""
        out: list[str] = []
        seen: set[str] = set()
        for m in self._init_modules:
            key = (m.name or "").strip() or (m.path.stem if m.path else "")
            if not key or key in seen:
                continue
            seen.add(key)
            out.append(key)
        return out

    def _refresh_print_hint(self) -> None:
        bits = []
        if self.settings.enable_init_on_connect and self.settings.init_module:
            bits.append(f"连接初始化: {self.settings.init_module}")
        else:
            bits.append("连接初始化: 关")
        if self.settings.print_client_data:
            bits.append("打印客户端: 开")
        else:
            bits.append(f"打印客户端: 关（回帧×{self.settings.reply_count}）")
        wl = parse_rx_id_whitelist(self.settings.rx_id_filter)
        if wl:
            bits.append(f"RX白名单×{len(wl)}")
        else:
            bits.append("RX白名单: 全")
        if self.settings.rx_changes_only:
            bits.append("仅显示变化")
        self.print_hint_var.set(" | ".join(bits))

    def _fill_megmeet_rx_filter(self) -> None:
        """Keep Megmeet whitelist fill working (also in Settings dialog)."""
        ids = f"{megmeet.PLUGIN_TX_ID:08X},{megmeet.WELDER_RX_ID:08X}"
        self.settings.rx_id_filter = ids
        self.settings = self.settings.clamp()
        try:
            save_settings(self.settings)
        except OSError as exc:
            messagebox.showwarning("保存设置失败", str(exc))
        self._last_log_by_key.clear()
        self._refresh_print_hint()
        self.status_var.set(f"已填入 Megmeet RX 白名单: {ids}")

    def _open_settings(self) -> None:
        r_choices = self._responder_choice_keys()
        dlg = SettingsDialog(
            self, self.settings, self._module_choice_keys(), r_choices
        )
        self.wait_window(dlg)
        if dlg.result is None:
            return
        self.settings = dlg.result
        try:
            save_settings(self.settings)
        except OSError as exc:
            messagebox.showwarning("保存设置失败", str(exc))
        self._last_log_by_key.clear()
        try:
            self._changes_only_var.set(bool(self.settings.rx_changes_only))
        except (tk.TclError, AttributeError):
            pass
        self._refresh_print_hint()
        key = (self.settings.init_module or "").strip()
        if key:
            for m in self._init_modules:
                if m.name == key or (m.path and m.path.stem == key):
                    self.init_var.set(self._module_label(m))
                    self._load_selected_module()
                    break
        else:
            self.init_var.set("")
            self._working_module = None
            self._rebuild_rows_tree()
        self._refresh_init_summary()
        self._reload_responder_profile()
        if self.session.connected:
            self._set_connected_status("监听中")
        else:
            self.status_var.set("已更新设置")
        self._refresh_conn_status_suffix()

    def _arm_reply_capture(self) -> None:
        if self.settings.print_client_data:
            self._await_replies = 0
            return
        self._await_replies = int(self.settings.reply_count)
        self._await_got = 0
        self._await_deadline = time.time() + self.settings.reply_timeout_ms / 1000.0

    def _check_reply_timeout(self) -> None:
        if self._await_replies <= 0:
            return
        if time.time() < self._await_deadline:
            return
        missing = self._await_replies
        self._await_replies = 0
        self._append_notice(
            f"回帧超时：期望 {self.settings.reply_count} 条，"
            f"已收到 {self._await_got} 条，仍缺 {missing} 条"
            f"（超时 {self.settings.reply_timeout_ms:g} ms）"
        )

    def _append_notice(self, text: str) -> None:
        t = time.strftime("%H:%M:%S")
        self.tree.insert(
            "",
            tk.END,
            values=(t, "—", "—", "", text),
        )
        self._update_empty_state()
        if not self._pause.get():
            kids = self.tree.get_children()
            if kids:
                self.tree.see(kids[-1])

    # ----- init summary / detail (v1.6.0) -----

    def _refresh_init_summary(self) -> None:
        """Show/hide summary row; update label from working module."""
        if self._goo_combo_active():
            self._init_summary_host.pack_forget()
            if hasattr(self, "_refresh_signal_card"):
                self._refresh_signal_card()
            return
        enabled = bool(self.settings.enable_init_on_connect)
        if not enabled:
            self._init_summary_host.pack_forget()
            detail = self._init_detail
            # Settings embeds the editor (embedded=True). Do not close it
            # just because connect-time auto-init is off.
            if detail is not None and not getattr(detail, "embedded", False):
                try:
                    if hasattr(detail, "detach"):
                        detail.detach()
                    elif hasattr(detail, "panel"):
                        detail.panel.detach()
                    detail.destroy()
                except tk.TclError:
                    pass
                self._init_detail = None
                self.rows_tree = None  # type: ignore[assignment]
                self.init_combo = None  # type: ignore[assignment]
                self.init_btn = None  # type: ignore[assignment]
            if hasattr(self, "_refresh_signal_card"):
                self._refresh_signal_card()
            return

        mod = self._working_module
        if mod is None:
            name = (self.settings.init_module or "").strip() or "—"
            n_on, n_tot = 0, 0
        else:
            name = mod.name
            n_tot = len(mod.rows)
            n_on = sum(1 for r in mod.rows if r.enabled)
        self.init_summary_var.set(f"初始化 · {name} · 已启用 {n_on}/{n_tot}")

        # Pack between connection bar and the receive/send row.
        mid_row = getattr(self, "_mid_row", None)
        if mid_row is None:
            return
        if not self._init_summary_host.winfo_ismapped():
            self._init_summary_card.pack(fill=tk.X, pady=(0, 8))
            self._init_summary_host.pack(fill=tk.X, before=mid_row)
        # Keep signal card between init summary and send
        if hasattr(self, "_refresh_signal_card"):
            self._refresh_signal_card()

    def _open_init_detail(self) -> None:
        if not self.settings.enable_init_on_connect:
            return
        if self._init_detail is not None:
            try:
                # Embedded panel lives inside Settings — just focus settings
                if isinstance(self._init_detail, InitDetailPanel):
                    top = self._init_detail.winfo_toplevel()
                    top.lift()
                    top.focus_set()
                    return
                self._init_detail.lift()
                self._init_detail.focus_set()
                return
            except tk.TclError:
                self._init_detail = None
        self._init_detail = InitDetailDialog(self)

    # ----- init modules / rows -----


    def _module_label(self, mod: InitModule) -> str:
        # Dropdown shows JSON `name` only — never stem / mode / 「初始」 suffix.
        return (mod.name or "").strip() or (mod.path.stem if mod.path else "")

    def _refresh_modules(self) -> None:
        self._init_modules = list_modules()
        labels = [CHOICE_NONE] + [self._module_label(m) for m in self._init_modules]
        if self.init_combo is not None:
            try:
                self.init_combo["values"] = labels
            except tk.TclError:
                pass
        preferred = None
        key = (self.settings.init_module or "").strip()
        if key and key != CHOICE_NONE:
            for m in self._init_modules:
                if m.name == key or (m.path and m.path.stem == key):
                    preferred = self._module_label(m)
                    break
        cur = self.init_var.get()
        if preferred:
            self.init_var.set(preferred)
        elif cur in labels:
            pass
        else:
            self.init_var.set(CHOICE_NONE)
        if _is_choice_none(self.init_var.get()) or not self._init_modules:
            self._working_module = None
            self._rebuild_rows_tree()
            if not self._init_modules:
                self.init_status_var.set("未找到模块（modules/ 或 ~/.can_tester/modules）")
        else:
            self._load_selected_module()
        self._refresh_init_summary()

    def _selected_module_meta(self) -> InitModule | None:
        label = self.init_var.get()
        if _is_choice_none(label):
            return None
        for m in self._init_modules:
            if self._module_label(m) == label:
                return m
        return None

    def _load_selected_module(self) -> None:
        meta = self._selected_module_meta()
        if meta is None:
            self._working_module = None
            self._rebuild_rows_tree()
            self._refresh_init_summary()
            return
        try:
            if meta.path:
                mod = load_module(meta.path)
            else:
                mod = copy.deepcopy(meta)
        except Exception as exc:
            messagebox.showerror("加载失败", str(exc))
            return
        self._working_module = mod
        self._rebuild_rows_tree()
        n_on = sum(1 for r in mod.rows if r.enabled)
        self.init_status_var.set(
            f"已加载: {mod.name}（{len(mod.rows)} 行，启用 {n_on}；"
            f"默认间隔 {mod.default_interval_ms} ms）"
        )
        self._refresh_init_summary()

    def _rebuild_rows_tree(self) -> None:
        if self.rows_tree is None:
            self._refresh_init_summary()
            return
        try:
            for iid in self.rows_tree.get_children():
                self.rows_tree.delete(iid)
        except tk.TclError:
            return
        mod = self._working_module
        if mod is None:
            self._refresh_init_summary()
            return
        for i, row in enumerate(mod.rows):
            iv = "" if row.interval_ms is None else str(int(row.interval_ms))
            self.rows_tree.insert(
                "",
                tk.END,
                iid=str(i),
                values=(
                    "✓" if row.enabled else "",
                    format_can_id(row.can_id, row.extended),
                    format_data_hex(row.data),
                    iv,
                    row.note or "",
                ),
            )
        self._refresh_init_summary()

    def _selected_row_index(self) -> int:
        if self.rows_tree is None:
            return -1
        try:
            sel = self.rows_tree.selection()
        except tk.TclError:
            return -1
        if not sel:
            return -1
        try:
            return int(sel[0])
        except ValueError:
            return -1

    def _on_row_click(self, event) -> None:
        """Toggle enabled when clicking the 启用 column."""
        if self.rows_tree is None:
            return
        region = self.rows_tree.identify_region(event.x, event.y)
        if region != "cell":
            return
        col = self.rows_tree.identify_column(event.x)
        if col != "#1":
            return
        row_id = self.rows_tree.identify_row(event.y)
        if not row_id or self._working_module is None:
            return
        try:
            idx = int(row_id)
        except ValueError:
            return
        if 0 <= idx < len(self._working_module.rows):
            r = self._working_module.rows[idx]
            r.enabled = not r.enabled
            self._rebuild_rows_tree()
            try:
                self.rows_tree.selection_set(str(idx))
            except tk.TclError:
                pass

    def _on_row_double_click(self, _event=None) -> None:
        self._edit_selected_row()

    def _add_row(self) -> None:
        if self._working_module is None:
            messagebox.showwarning(
                "无模块", "请先选择或新建模块", parent=self._edit_dialog_parent()
            )
            return
        mod = self._working_module
        prev = mod.rows[-1] if mod.rows else None
        blank = InitRow(
            enabled=True,
            can_id=int(prev.can_id) if prev is not None else 0,
            extended=bool(prev.extended) if prev is not None else False,
            data=b"",
            interval_ms=None,
            note="",
        )
        mod.rows.append(blank)
        idx = len(mod.rows) - 1
        self._rebuild_rows_tree()
        self._select_init_row(idx)
        parent = self._edit_dialog_parent()
        dlg = RowEditDialog(parent, blank)
        parent.wait_window(dlg)
        if dlg.result is None:
            if 0 <= idx < len(mod.rows) and mod.rows[idx] is blank:
                del mod.rows[idx]
            self._rebuild_rows_tree()
            if mod.rows:
                self._select_init_row(min(idx, len(mod.rows) - 1))
            return
        mod.rows[idx] = dlg.result
        self._rebuild_rows_tree()
        self._select_init_row(idx)

    def _select_init_row(self, idx: int) -> None:
        if self.rows_tree is None or idx < 0:
            return
        try:
            self.rows_tree.selection_set(str(idx))
            self.rows_tree.see(str(idx))
        except tk.TclError:
            pass

    def _delete_row(self) -> None:
        mod = self._working_module
        i = self._selected_row_index()
        if mod is None or i < 0:
            messagebox.showinfo("提示", "请先选择一行")
            return
        del mod.rows[i]
        self._rebuild_rows_tree()
        if mod.rows and self.rows_tree is not None:
            try:
                self.rows_tree.selection_set(str(min(i, len(mod.rows) - 1)))
            except tk.TclError:
                pass

    def _move_row(self, delta: int) -> None:
        mod = self._working_module
        if mod is None or not mod.rows:
            return
        i = self._selected_row_index()
        j = i + delta
        if i < 0 or j < 0 or j >= len(mod.rows):
            return
        mod.rows[i], mod.rows[j] = mod.rows[j], mod.rows[i]
        self._rebuild_rows_tree()
        if self.rows_tree is not None:
            try:
                self.rows_tree.selection_set(str(j))
            except tk.TclError:
                pass

    def _edit_dialog_parent(self) -> tk.Misc:
        """Prefer Settings / editor toplevel so grab_set nested dialogs work."""
        for attr in ("_init_detail", "_responder_detail"):
            detail = getattr(self, attr, None)
            if detail is None:
                continue
            try:
                top = detail.winfo_toplevel()
                if top is not None and int(top.winfo_exists()):
                    return top
            except tk.TclError:
                continue
        return self

    def _edit_selected_row(self) -> None:
        mod = self._working_module
        if mod is None:
            messagebox.showwarning(
                "无模块", "请先选择或新建模块", parent=self._edit_dialog_parent()
            )
            return
        i = self._selected_row_index()
        if i < 0 or i >= len(mod.rows):
            self._add_row()
            return
        parent = self._edit_dialog_parent()
        dlg = RowEditDialog(parent, mod.rows[i])
        parent.wait_window(dlg)
        if dlg.result is None:
            return
        mod.rows[i] = dlg.result
        self._rebuild_rows_tree()
        if self.rows_tree is not None:
            try:
                self.rows_tree.selection_set(str(i))
            except tk.TclError:
                pass

    def _save_working_module(self) -> None:
        mod = self._working_module
        if mod is None:
            messagebox.showwarning("无模块", "没有可保存的模块")
            return
        path = mod.path
        if path is None:
            return self._save_module_as()
        try:
            save_module(mod, path)
        except OSError:
            USER_MODULES_DIR.mkdir(parents=True, exist_ok=True)
            alt = USER_MODULES_DIR / path.name
            try:
                save_module(mod, alt)
                path = alt
            except Exception as exc:
                messagebox.showerror("保存失败", str(exc))
                return
        except Exception as exc:
            messagebox.showerror("保存失败", str(exc))
            return
        self.init_status_var.set(f"已保存: {path}")
        self._refresh_modules()

    def _save_module_as(self) -> None:
        mod = self._working_module
        if mod is None:
            messagebox.showwarning("无模块", "没有可保存的模块")
            return
        USER_MODULES_DIR.mkdir(parents=True, exist_ok=True)
        stem = simpledialog.askstring(
            "另存为", "文件名（不含 .json）:", parent=self
        )
        if not stem:
            return
        path = USER_MODULES_DIR / f"{stem}.json"
        name = simpledialog.askstring(
            "另存为", "模块显示名:", initialvalue=mod.name, parent=self
        )
        if name:
            mod.name = name.strip() or mod.name
        try:
            save_module(mod, path)
        except Exception as exc:
            messagebox.showerror("保存失败", str(exc))
            return
        self.init_status_var.set(f"已另存为: {path}")
        self._refresh_modules()

    def _run_checked_rows(self) -> None:
        # Product: one trigger → one round. If connect already auto-inited, don't
        # silently send a second round (6 checked → 12 TX).
        n_done = self._connect_init_count
        if n_done is not None and n_done > 0:
            if not messagebox.askyesno(
                "再次发送？",
                f"本轮开总线已自动发送初始化 {n_done} 帧。\n"
                "再点「发送」会再跑一轮（勾选行会再发一遍）。\n\n"
                "确定再发？",
                default=messagebox.NO,
            ):
                self.status_var.set(
                    f"已开 {getattr(self.session, 'channel', '')} — "
                    f"已初始化 {n_done} 帧（未重发）"
                )
                return
        self._start_init_run(only_enabled=True, indices=None)

    def _run_selected_row(self) -> None:
        i = self._selected_row_index()
        if self._working_module is None or i < 0:
            messagebox.showwarning("无行", "请先选择一行")
            return
        self._start_init_run(only_enabled=False, indices=[i])

    def _start_init_run(
        self,
        *,
        only_enabled: bool,
        indices: list[int] | None,
        from_connect: bool = False,
    ) -> None:
        if self._init_busy:
            return
        mod = self._working_module
        if mod is None:
            if not from_connect:
                messagebox.showwarning("无模块", "请先选择初始化模块（或刷新模块列表）")
            return
        if not self.session.connected:
            if not from_connect:
                messagebox.showwarning("未连接", "请先打开总线，再发送")
            return
        if only_enabled and not any(r.enabled for r in mod.rows):
            if not from_connect:
                messagebox.showwarning("未勾选", "请至少启用一行再发送")
            return
        self._init_busy = True
        for btn in (self.init_btn, getattr(self, "_send_init_btn", None)):
            if btn is None:
                continue
            try:
                btn.configure(state=tk.DISABLED)
            except tk.TclError:
                pass
        n = (
            len(indices)
            if indices is not None
            else sum(1 for r in mod.rows if r.enabled)
        )
        self.init_status_var.set(f"开始发送: {mod.name}（{n} 行）…")

        def on_progress(i, total, row, msg):
            self.after(
                0,
                lambda: self.init_status_var.set(
                    f"行 {i + 1}/{total}: {row.display} — {msg}"
                ),
            )

        def worker():
            result = run_module(
                mod,
                self.session,
                dry_run=False,
                on_progress=on_progress,
                only_enabled=only_enabled,
                indices=indices,
                default_interval_ms=self.settings.default_interval_ms,
            )
            self.after(0, lambda: self._init_done(result, from_connect=from_connect))

        threading.Thread(target=worker, name="init-module", daemon=True).start()

    def _init_done(self, result, *, from_connect: bool = False) -> None:
        self._init_busy = False
        for btn in (self.init_btn, getattr(self, "_send_init_btn", None)):
            if btn is None:
                continue
            try:
                btn.configure(state=tk.NORMAL)
            except tk.TclError:
                pass
        self._refresh_init_summary()
        self.init_status_var.set(result.message)
        for sr in result.results:
            row = sr.row
            if sr.ok:
                self._append_frame(
                    "TX",
                    row.can_id,
                    bool(row.extended),
                    row.data,
                    time.time(),
                )
                self._arm_reply_capture()
        if result.ok:
            n_tx = sum(1 for sr in result.results if sr.ok)
            if from_connect:
                self._connect_init_count = n_tx
            # Init modules no longer force a hardcoded Megmeet send profile.
            if self.session.connected:
                if from_connect:
                    tag = f"已初始化 {n_tx} 帧，监听中"
                else:
                    tag = f"已发送初始化 {n_tx} 帧，监听中"
                    # Manual send also counts as "done this session" so a third
                    # click still gets the confirm (prevents accidental spam).
                    self._connect_init_count = n_tx
                self._set_status_normal(
                    f"已开 {self.session.channel} ({self.session.backend}) — {tag}"
                )
                self.init_status_var.set(f"完成：已发送 {n_tx} 帧（仅勾选行，一轮）")
        else:
            if from_connect:
                self._append_notice(f"连接初始化失败: {result.message}")
                self.status_var.set(
                    f"已开 {self.session.channel} — 初始化失败，已进入监听"
                )
            else:
                messagebox.showerror("发送失败", result.message)

    def _maybe_auto_init_on_connect(self) -> None:
        if self.settings.listen_only_shared and self.session.backend == "socketcan":
            # TX gated - skip auto-init send sequence
            self.status_var.set(
                f"已开 {self.session.channel} ({self.session.backend}/socketcan)"
                f"（只监听不独占） — 监听中"
            )
            if self.settings.enable_init_on_connect:
                self._append_notice(
                    "只监听不独占：已跳过连接初始化发送（请关闭该选项后再初始化）"
                )
            return
        if not self.settings.enable_init_on_connect:
            self._set_connected_status("监听中")
            return
        key = (self.settings.init_module or "").strip()
        if not key:
            self.status_var.set(
                f"已开 {self.session.channel} — 已启用连接初始化但未选模块，直接监听"
            )
            return
        found = find_module_by_name_or_stem(key)
        if found is None:
            self._append_notice(f"连接初始化：找不到模块 {key!r}，直接监听")
            self.status_var.set(
                f"已开 {self.session.channel} — 模块未找到，监听中"
            )
            return
        for m in self._init_modules:
            if m.path == found.path or m.name == found.name:
                self.init_var.set(self._module_label(m))
                break
        try:
            self._working_module = (
                load_module(found.path) if found.path else copy.deepcopy(found)
            )
        except Exception as exc:
            self._append_notice(f"连接初始化加载失败: {exc}")
            return
        self._rebuild_rows_tree()
        self.status_var.set(
            f"已开 {self.session.channel} — 正在运行连接初始化…"
        )
        self._start_init_run(only_enabled=True, indices=None, from_connect=True)

    def _on_profile(self, _evt=None) -> None:
        v = (self.profile_var.get() or "").strip()
        if v != COMBO_MEGMEET_GOO:
            self._layout_goo_panels()
            self._drop_saved_responder_for_generic()
            self.status_var.set("通用模式：仅原始 HEX")
            self._refresh_init_summary()
            return
        self._apply_goo_combo()

    def _goo_combo_active(self) -> bool:
        var = getattr(self, "profile_var", None)
        if var is None:
            return False
        try:
            return (var.get() or "").strip() == COMBO_MEGMEET_GOO
        except tk.TclError:
            return False

    def _goo_role(self) -> str:
        var = getattr(self, "_role_var", None)
        if var is None:
            return ROLE_CLIENT
        try:
            role = (var.get() or "").strip()
        except tk.TclError:
            return ROLE_CLIENT
        return role if role in (ROLE_CLIENT, ROLE_WELDER) else ROLE_CLIENT

    def _build_rx_signal_panel(self, parent: ttk.Frame) -> None:
        """Received-signal card. Shown in the left column when a combo is on."""
        self._rx_host = ttk.Frame(parent)
        self._rx_card = CardFrame(self._rx_host)
        body = tk.Frame(self._rx_card.body, bg=BG)
        body.pack(fill=tk.BOTH, expand=True, padx=8, pady=6)
        self._rx_title_var = tk.StringVar(value="接收")
        ttk.Label(body, textvariable=self._rx_title_var, style="Header.TLabel").pack(
            anchor=tk.W
        )
        brow = tk.Frame(body, bg=BG)
        brow.pack(fill=tk.X, pady=(6, 0))
        self._rx_bytes: list[tk.Label] = []
        for i in range(8):
            lab = tk.Label(
                brow,
                text="--",
                width=3,
                relief="solid",
                bd=1,
                font=FONT_MONO,
                bg="#FFFFFF",
                fg="#2A2E34",
            )
            lab.grid(row=0, column=i, sticky="ew", padx=2)
            brow.columnconfigure(i, weight=1, uniform="rxbyte")
            self._rx_bytes.append(lab)
        self._rx_chips_row = ttk.Frame(body)
        self._rx_chips_row.pack(fill=tk.X, pady=(6, 0))
        self._rx_chip_labels: dict[str, tk.Label] = {}
        self._rx_line_var = tk.StringVar(value="等待帧")
        ttk.Label(body, textvariable=self._rx_line_var, style="Muted.TLabel").pack(
            anchor=tk.W, pady=(2, 0)
        )

    def _build_goo_send_panels(self, mid: ttk.Frame) -> None:
        """Send-side controls for the two Megmeet-GOO roles."""
        self._client_box = ttk.Frame(mid)
        bits = ttk.Frame(self._client_box)
        bits.pack(fill=tk.X, pady=(4, 0))
        self._poll_weld = tk.BooleanVar(value=False)
        self._poll_seek = tk.BooleanVar(value=False)
        self._poll_gas = tk.BooleanVar(value=False)
        self._poll_feed = tk.BooleanVar(value=False)
        self._poll_retract = tk.BooleanVar(value=False)
        self._poll_err = tk.BooleanVar(value=False)
        for text, var in (
            ("起焊", self._poll_weld),
            ("寻位", self._poll_seek),
            ("检气", self._poll_gas),
            ("送丝", self._poll_feed),
            ("回抽", self._poll_retract),
            ("机故", self._poll_err),
        ):
            ttk.Checkbutton(
                bits, text=text, variable=var, command=self._sync_client_poll_fields
            ).pack(side=tk.LEFT, padx=(0, 8))
        nums = ttk.Frame(self._client_box)
        nums.pack(fill=tk.X, pady=(4, 0))
        ttk.Label(nums, text="模式").pack(side=tk.LEFT)
        self._poll_mode = tk.StringVar(value="直流一元")
        mode_box = ttk.Combobox(
            nums,
            textvariable=self._poll_mode,
            values=tuple(JOB_MODES.values()),
            state="readonly",
            width=10,
        )
        mode_box.pack(side=tk.LEFT, padx=(6, 0))
        mode_box.bind("<<ComboboxSelected>>", lambda _e: self._sync_client_poll_fields())
        ttk.Label(nums, text="JOB").pack(side=tk.LEFT, padx=(10, 0))
        self._poll_job = tk.StringVar(value="0")
        job_ent = ttk.Entry(nums, textvariable=self._poll_job, width=4)
        job_ent.pack(side=tk.LEFT, padx=(6, 0))
        job_ent.bind("<KeyRelease>", lambda _e: self._sync_client_poll_fields())
        ttk.Label(nums, text="电流 A").pack(side=tk.LEFT, padx=(10, 0))
        self._poll_i = tk.StringVar(value="0")
        i_ent = ttk.Entry(nums, textvariable=self._poll_i, width=6)
        i_ent.pack(side=tk.LEFT, padx=(6, 0))
        i_ent.bind("<KeyRelease>", lambda _e: self._sync_client_poll_fields())
        ttk.Label(nums, text="电压 V").pack(side=tk.LEFT, padx=(10, 0))
        self._poll_u = tk.StringVar(value="0.0")
        u_ent = ttk.Entry(nums, textvariable=self._poll_u, width=6)
        u_ent.pack(side=tk.LEFT, padx=(6, 0))
        u_ent.bind("<KeyRelease>", lambda _e: self._sync_client_poll_fields())

        self._welder_box = ttk.Frame(mid)
        wbits = ttk.Frame(self._welder_box)
        wbits.pack(fill=tk.X, pady=(4, 0))
        for col in range(4):
            wbits.columnconfigure(col, weight=1)
        self._welder_ready = tk.BooleanVar(value=True)
        self._welder_error = tk.BooleanVar(value=False)
        self._welder_touch = tk.BooleanVar(value=False)
        self._welder_arc = tk.BooleanVar(value=False)
        self._welder_arc_ex = tk.BooleanVar(value=False)
        self._welder_gas_ex = tk.BooleanVar(value=False)
        self._welder_stick = tk.BooleanVar(value=False)
        self._welder_over = tk.BooleanVar(value=False)
        for i, (text, var) in enumerate(
            (
                ("就绪", self._welder_ready),
                ("故障", self._welder_error),
                ("寻位成功", self._welder_touch),
                ("起弧成功", self._welder_arc),
                ("电弧异常", self._welder_arc_ex),
                ("气体流量异常", self._welder_gas_ex),
                ("粘丝", self._welder_stick),
                ("给定超范围", self._welder_over),
            )
        ):
            ttk.Checkbutton(
                wbits, text=text, variable=var, command=self._sync_welder_send_fields
            ).grid(row=i // 4, column=i % 4, sticky="w", padx=(0, 4), pady=1)
        wnums = ttk.Frame(self._welder_box)
        wnums.pack(fill=tk.X, pady=(4, 0))
        ttk.Label(wnums, text="电流 A").pack(side=tk.LEFT)
        self._welder_i = tk.StringVar(value="180")
        wi = ttk.Entry(wnums, textvariable=self._welder_i, width=6)
        wi.pack(side=tk.LEFT, padx=(6, 0))
        wi.bind("<KeyRelease>", lambda _e: self._sync_welder_send_fields())
        ttk.Label(wnums, text="电压 V").pack(side=tk.LEFT, padx=(10, 0))
        self._welder_u = tk.StringVar(value="22.5")
        wu = ttk.Entry(wnums, textvariable=self._welder_u, width=6)
        wu.pack(side=tk.LEFT, padx=(6, 0))
        wu.bind("<KeyRelease>", lambda _e: self._sync_welder_send_fields())
        ttk.Label(wnums, text="错误码").pack(side=tk.LEFT, padx=(10, 0))
        self._welder_err = tk.StringVar(value="0")
        we = ttk.Entry(wnums, textvariable=self._welder_err, width=4)
        we.pack(side=tk.LEFT, padx=(6, 0))
        we.bind("<KeyRelease>", lambda _e: self._sync_welder_send_fields())
        wauto = ttk.Frame(self._welder_box)
        wauto.pack(fill=tk.X, pady=(4, 0))
        self._welder_auto = tk.BooleanVar(value=True)
        self._welder_auto_arc = tk.BooleanVar(value=True)
        ttk.Checkbutton(
            wauto,
            text="自动应答",
            variable=self._welder_auto,
            command=self._sync_welder_send_fields,
        ).pack(side=tk.LEFT, padx=(0, 12))
        ttk.Checkbutton(
            wauto,
            text="起焊沿起弧",
            variable=self._welder_auto_arc,
            command=self._on_welder_auto_arc,
        ).pack(side=tk.LEFT)
        self._layout_goo_panels()

    def _layout_goo_panels(self) -> None:
        """Show RX under the bus bar and the matching send block. Hide the other."""
        client = getattr(self, "_client_box", None)
        welder = getattr(self, "_welder_box", None)
        role_combo = getattr(self, "_role_combo", None)
        if client is None or welder is None:
            return
        active = self._goo_combo_active()
        role_label = getattr(self, "_role_label", None)
        init_btn = getattr(self, "_send_init_btn", None)
        if role_combo is not None and role_label is not None:
            try:
                if active:
                    if not role_label.winfo_ismapped():
                        role_label.pack(side=tk.LEFT, padx=(12, 0))
                    if not role_combo.winfo_ismapped():
                        role_combo.pack(side=tk.LEFT, padx=(6, 0))
                else:
                    role_label.pack_forget()
                    role_combo.pack_forget()
                if init_btn is not None:
                    show_init = active and self._goo_role() == ROLE_CLIENT
                    if show_init and not init_btn.winfo_ismapped():
                        init_btn.pack(side=tk.LEFT, padx=(12, 0))
                    elif not show_init:
                        init_btn.pack_forget()
            except tk.TclError:
                pass
        client.pack_forget()
        welder.pack_forget()
        if not active:
            self._place_work(False)
            return
        role = self._goo_role()
        anchor = None
        seen_box = False
        try:
            for child in client.master.winfo_children():
                if child in (client, welder):
                    seen_box = True
                    continue
                if seen_box and child.winfo_ismapped():
                    anchor = child
                    break
        except tk.TclError:
            anchor = None
        box = client if role == ROLE_CLIENT else welder
        try:
            if anchor is not None:
                box.pack(fill=tk.X, before=anchor)
            else:
                box.pack(fill=tk.X)
        except tk.TclError:
            box.pack(fill=tk.X)
        if role == ROLE_CLIENT:
            self._rx_title_var.set("接收 · 焊机")
            self._ensure_rx_chips(
                ["就绪", "故障", "寻位成功", "起弧成功", "电弧异常", "气体流量异常", "粘丝", "给定超范围"]
            )
        else:
            self._rx_title_var.set("接收 · 机器人")
            self._ensure_rx_chips(["起焊", "寻位", "检气", "送丝", "回抽", "机故"])
        self._place_work(True)

    def _place_work(self, show_rx: bool) -> None:
        """Left = received signals, right = send. Generic mode uses the full width."""
        rx = getattr(self, "_rx_host", None)
        send = getattr(self, "_send_card", None)
        if rx is None or send is None:
            return
        try:
            rx.grid_remove()
            send.grid_remove()
        except tk.TclError:
            return
        hex_row = getattr(self, "_hex_row", None)
        if show_rx:
            try:
                self._rx_card.pack(fill=tk.BOTH, expand=True)
            except tk.TclError:
                pass
            rx.grid(row=0, column=0, sticky="nsew", padx=(0, 6))
            send.grid(row=0, column=1, sticky="nsew")
            if hex_row is not None and not hex_row.winfo_ismapped():
                hex_row.pack(fill=tk.X, pady=(2, 0))
            return
        send.grid(row=0, column=0, columnspan=2, sticky="ew")
        if hex_row is not None and not hex_row.winfo_ismapped():
            hex_row.pack(fill=tk.X, pady=(2, 0))

    def _pack_rx_panel(self) -> None:
        self._place_work(True)

    def _ensure_rx_chips(self, names: list[str]) -> None:
        row = getattr(self, "_rx_chips_row", None)
        if row is None:
            return
        if list(self._rx_chip_labels.keys()) == names:
            return
        for w in row.winfo_children():
            w.destroy()
        self._rx_chip_labels = {}
        cols = len(names) if names and sum(len(n) for n in names) <= 14 else 4
        cols = max(1, min(cols, len(names) or 1))
        for c in range(8):
            row.columnconfigure(c, weight=1 if c < cols else 0)
        for i, name in enumerate(names):
            lab = tk.Label(
                row,
                text=name,
                font=FONT_UI_SM,
                bg="#EEF0F3",
                fg=MUTED_FG,
                padx=6,
                pady=2,
            )
            lab.grid(row=i // cols, column=i % cols, sticky="w", padx=(0, 8), pady=1)
            self._rx_chip_labels[name] = lab

    def _paint_rx_view(self, view: dict) -> None:
        raw = bytes(view.get("raw") or b"")
        for i, lab in enumerate(self._rx_bytes):
            lab.configure(text=f"{raw[i]:02X}" if i < len(raw) else "--")
        chips = {name: on for name, on in view.get("chips") or []}
        for name, lab in self._rx_chip_labels.items():
            on = bool(chips.get(name))
            lab.configure(
                bg="#E7F6EC" if on else "#EEF0F3",
                fg="#1B7A3A" if on else MUTED_FG,
            )
        self._rx_line_var.set(str(view.get("line") or ""))

    def _note_goo_rx(self, can_id: int, data: bytes) -> None:
        if not self._goo_combo_active():
            return
        role = self._goo_role()
        if role == ROLE_CLIENT and int(can_id) == WELDER_ID:
            self._paint_rx_view(describe_welder(data))
        elif role == ROLE_WELDER and int(can_id) == ROBOT_ID:
            self._paint_rx_view(describe_robot(data))

    def _sync_client_poll_fields(self) -> None:
        if not hasattr(self, "id_var"):
            return
        try:
            job = int(float(self._poll_job.get() or "0"))
        except ValueError:
            job = 0
        try:
            current = int(float(self._poll_i.get() or "0"))
        except ValueError:
            current = 0
        try:
            voltage = float(self._poll_u.get() or "0")
        except ValueError:
            voltage = 0.0
        data = pack_robot_poll(
            robot_err=bool(self._poll_err.get()),
            seek=bool(self._poll_seek.get()),
            weld=bool(self._poll_weld.get()),
            gas=bool(self._poll_gas.get()),
            feed=bool(self._poll_feed.get()),
            retract=bool(self._poll_retract.get()),
            mode=MODE_BY_NAME.get(self._poll_mode.get(), 0),
            job=job,
            current=current,
            voltage=voltage,
        )
        self.id_var.set(f"0x{ROBOT_ID:08X}")
        self.ext_var.set(True)
        self.data_var.set(format_data_hex(data))

    def _sync_welder_send_fields(self) -> None:
        st = getattr(self, "_responder_state", None)
        if st is None or not hasattr(self, "id_var"):
            return
        st.ready = bool(self._welder_ready.get())
        st.error = bool(self._welder_error.get())
        st.touch_ok = bool(self._welder_touch.get())
        st.arc_ok = bool(self._welder_arc.get())
        st.arc_ex = bool(self._welder_arc_ex.get())
        st.other_ex = bool(self._welder_gas_ex.get())
        st.stick = bool(self._welder_stick.get())
        st.range_over = bool(self._welder_over.get())
        st.auto_reply = bool(self._welder_auto.get())
        try:
            st.current = int(float(self._welder_i.get() or "0"))
        except ValueError:
            pass
        try:
            st.voltage = float(self._welder_u.get() or "0")
        except ValueError:
            pass
        try:
            st.errcode = int(float(self._welder_err.get() or "0"))
        except ValueError:
            pass
        if hasattr(self, "auto_arc_var"):
            st.auto_arc = bool(self._welder_auto_arc.get())
        data = build_reply(st)
        self.id_var.set(f"0x{WELDER_ID:08X}")
        self.ext_var.set(True)
        self.data_var.set(format_data_hex(data))

    def _on_welder_auto_arc(self) -> None:
        if hasattr(self, "auto_arc_var"):
            self.auto_arc_var.set(bool(self._welder_auto_arc.get()))
        self._on_auto_arc_toggled()
        self._sync_welder_send_fields()

    def _pull_welder_panel_from_state(self) -> None:
        st = getattr(self, "_responder_state", None)
        if st is None or not hasattr(self, "_welder_ready"):
            return
        pairs = (
            (self._welder_ready, bool(st.ready)),
            (self._welder_error, bool(st.error)),
            (self._welder_touch, bool(st.touch_ok)),
            (self._welder_arc, bool(st.arc_ok)),
            (self._welder_arc_ex, bool(st.arc_ex)),
            (self._welder_gas_ex, bool(st.other_ex)),
            (self._welder_stick, bool(st.stick)),
            (self._welder_over, bool(getattr(st, "range_over", False))),
            (self._welder_auto, bool(st.auto_reply)),
            (self._welder_auto_arc, bool(st.auto_arc)),
        )
        for var, val in pairs:
            if bool(var.get()) != val:
                var.set(val)
        cur = str(int(st.current))
        if self._welder_i.get() != cur:
            self._welder_i.set(cur)
        volt = f"{float(st.voltage):.1f}"
        if self._welder_u.get() != volt:
            self._welder_u.set(volt)
        err = str(int(st.errcode))
        if self._welder_err.get() != err:
            self._welder_err.set(err)

    def _send_goo_init(self) -> None:
        """Send 固高 F0–F5 once. Does not turn on connect-time auto init."""
        if self._init_busy:
            return
        if not self.session.connected:
            messagebox.showwarning("未连接", "请先打开总线，再发送初始化")
            return
        mod = find_module_by_name_or_stem(INIT_MODULE_NAME)
        if mod is None:
            messagebox.showwarning("无模块", f"找不到初始化模块「{INIT_MODULE_NAME}」")
            return
        self._working_module = mod
        self._start_init_run(only_enabled=True, indices=None)

    def _apply_goo_combo(self) -> None:
        """Bind init + responder + send fields for the selected role."""
        role = self._goo_role()
        if role == ROLE_CLIENT:
            self.settings.init_module = ""
            self.settings.responder_module = ""
        else:
            self.settings.init_module = ""
            self.settings.responder_module = RESPONDER_MODULE_NAME
        self.settings = self.settings.clamp()
        try:
            save_settings(self.settings)
        except OSError as exc:
            messagebox.showwarning("保存设置失败", str(exc))
        self._refresh_modules()
        self._reload_responder_profile()
        self._layout_goo_panels()
        self._refresh_init_summary()
        if role == ROLE_CLIENT:
            self._sync_client_poll_fields()
            self.status_var.set("Megmeet - GOO · 给焊机发（轮询；点「发送初始化」发 F0–F5）")
        else:
            self._pull_welder_panel_from_state()
            self._sync_welder_send_fields()
            self.status_var.set("Megmeet - GOO · 模拟焊机（看机器人帧，自由回帧）")

    # ----- TX/RX activity LEDs + iface commands (v1.7.12+) -----

    def _build_txrx_leds(self, parent: ttk.Frame) -> None:
        """Two Ø10 LEDs left of 设置: TX green / RX blue; idle = border gray."""
        led_fr = ttk.Frame(parent)
        led_fr.pack(side=tk.RIGHT, padx=(8, 8))
        d = LED_DIAMETER
        # TX
        ttk.Label(led_fr, text="TX", style="Header.TLabel").pack(side=tk.LEFT)
        self._tx_led_canvas = tk.Canvas(
            led_fr,
            width=d,
            height=d,
            highlightthickness=0,
            bd=0,
            bg=BG,
        )
        self._tx_led_canvas.pack(side=tk.LEFT, padx=(3, 0))
        self._tx_led_oval = self._tx_led_canvas.create_oval(
            1, 1, d - 1, d - 1, fill=LED_IDLE, outline=LED_IDLE
        )
        # RX (padx LED_GAP = 6 between the two Ø10 lights)
        ttk.Label(led_fr, text="RX", style="Header.TLabel").pack(
            side=tk.LEFT, padx=(LED_GAP, 0)
        )
        self._rx_led_canvas = tk.Canvas(
            led_fr,
            width=d,
            height=d,
            highlightthickness=0,
            bd=0,
            bg=BG,
        )
        self._rx_led_canvas.pack(side=tk.LEFT, padx=(3, 0))
        self._rx_led_oval = self._rx_led_canvas.create_oval(
            1, 1, d - 1, d - 1, fill=LED_IDLE, outline=LED_IDLE
        )

    def _led_prefix(self, which: str) -> str | None:
        which = (which or "").upper()
        if which == "TX":
            return "tx"
        if which == "RX":
            return "rx"
        return None

    def _led_set_color(self, which: str, color: str) -> None:
        prefix = self._led_prefix(which)
        if not prefix:
            return
        canvas = getattr(self, f"_{prefix}_led_canvas", None)
        oval = getattr(self, f"_{prefix}_led_oval", None)
        if canvas is None or oval is None:
            return
        try:
            canvas.itemconfigure(oval, fill=color, outline=color)
        except tk.TclError:
            pass

    def _led_cancel_job(self, which: str) -> None:
        prefix = self._led_prefix(which)
        if not prefix:
            return
        job_attr = f"_{prefix}_led_job"
        prev = getattr(self, job_attr, None)
        if prev is not None:
            try:
                self.after_cancel(prev)
            except Exception:
                pass
            setattr(self, job_attr, None)

    def _pulse_led(self, which: str) -> None:
        """Blink TX/RX on bus activity, including frames kept off the timeline.

        A burst while the lamp is already on (or in the off gap) queues one more
        blink, so a steady stream toggles instead of staying solid or staying gray.
        """
        prefix = self._led_prefix(which)
        if not prefix:
            return
        which = prefix.upper()
        if getattr(self, f"_{prefix}_led_job", None) is not None:
            setattr(self, f"_{prefix}_led_pending", True)
            return
        self._led_turn_on(which)

    def _led_turn_on(self, which: str) -> None:
        prefix = self._led_prefix(which)
        if not prefix:
            return
        which = prefix.upper()
        on = LED_TX_ON if which == "TX" else LED_RX_ON
        self._led_set_color(which, on)
        setattr(self, f"_{prefix}_led_last", time.monotonic())
        setattr(
            self,
            f"_{prefix}_led_job",
            self.after(LED_ON_MS, lambda w=which: self._led_flash_off(w)),
        )

    def _led_flash_off(self, which: str) -> None:
        prefix = self._led_prefix(which)
        if not prefix:
            return
        which = prefix.upper()
        job_attr = f"_{prefix}_led_job"
        setattr(self, job_attr, None)
        self._led_set_color(which, LED_IDLE)
        if getattr(self, f"_{prefix}_led_pending", False):
            setattr(self, f"_{prefix}_led_pending", False)
            setattr(
                self,
                job_attr,
                self.after(LED_GAP_MS, lambda w=which: self._led_turn_on(w)),
            )

    def _led_idle(self, which: str) -> None:
        """Force idle gray and clear flash job (e.g. teardown)."""
        prefix = self._led_prefix(which)
        if not prefix:
            return
        which = prefix.upper()
        self._led_cancel_job(which)
        setattr(self, f"_{prefix}_led_pending", False)
        self._led_set_color(which, LED_IDLE)

    def _iface_cmd_target(
        self, name: str | None = None, *, parent: tk.Misc | None = None
    ) -> str | None:
        dlg = parent or self
        if name is None:
            name = (self.iface_var.get() or "").strip()
        else:
            name = (name or "").strip()
        if not name or name == VIRTUAL_IFACE:
            messagebox.showinfo(
                "不适用",
                f"「{VIRTUAL_IFACE}」是进程内软件回环，无需 ip link。\n"
                "请改用 can0 / vcan0 等 SocketCAN 接口名。",
                parent=dlg,
            )
            return None
        return name

    def _iface_looks_vcan(self, name: str) -> bool:
        if name.startswith("vcan"):
            return True
        # Created can0-as-vcan: detect via ip -details ("vcan" keyword)
        try:
            proc = subprocess.run(
                ["ip", "-details", "link", "show", "dev", name],
                capture_output=True,
                text=True,
                timeout=5,
                check=False,
            )
            out = ((proc.stdout or "") + "\n" + (proc.stderr or "")).lower()
            return "vcan" in out
        except Exception:
            return False

    def _format_shell_cmd(self, argv: list[str], *, with_sudo: bool = True) -> str:
        body = " ".join(shlex.quote(a) for a in argv)
        return f"sudo {body}" if with_sudo else body

    def _run_ip_system_cmd(
        self,
        argv: list[str],
        *,
        title: str = "",
        confirm: bool = True,
        soft_fail: bool = False,
        parent: tk.Misc | None = None,
    ) -> bool:
        """Optionally confirm, then run with sudo -n. Result → timeline (+ status)."""
        dlg = parent or self
        display = self._format_shell_cmd(argv, with_sudo=True)
        if confirm:
            body = title + "\n\n" if title else ""
            if not messagebox.askyesno(
                "确认执行",
                f"{body}将执行（非交互 sudo -n）：\n{display}\n\n继续？",
                parent=dlg,
            ):
                self._append_notice(f"已取消：{display}")
                return False
        full = ["sudo", "-n", *argv]
        try:
            proc = subprocess.run(
                full,
                capture_output=True,
                text=True,
                timeout=20,
                check=False,
            )
        except FileNotFoundError:
            msg = "找不到 sudo 或 ip 命令"
            self._append_notice(f"失败：{display} — {msg}")
            if not soft_fail:
                self._set_status_error(msg)
            return False
        except subprocess.TimeoutExpired:
            msg = "命令超时"
            self._append_notice(f"失败：{display} — {msg}")
            if not soft_fail:
                self._set_status_error(msg)
            return False
        stdout = (proc.stdout or "").strip()
        stderr = (proc.stderr or "").strip()
        if proc.returncode == 0:
            note = f"成功：{display}"
            if stdout:
                note = f"{note} — {stdout.splitlines()[0][:120]}"
            self._append_notice(note)
            self._set_status_normal(f"已执行：{argv[0]} …")
            return True
        err = stderr or stdout or f"exit {proc.returncode}"
        if soft_fail:
            self._append_notice(f"提示：{display} — {err}（已忽略，继续）")
        else:
            self._append_notice(f"失败：{display} — {err}")
            self._set_status_error(err)
        return False

    def _cmd_iface_create(
        self, name: str | None = None, *, parent: tk.Misc | None = None
    ) -> None:
        """Create virtual CAN iface: always `ip link add … type vcan` (name default can0)."""
        dlg = parent or self
        name = (name or "").strip() or "can0"
        if name == VIRTUAL_IFACE:
            messagebox.showinfo(
                "不适用",
                f"「{VIRTUAL_IFACE}」是进程内软件回环，无需 ip link。\n"
                "请改用 can0 / vcan0 等接口名。",
                parent=dlg,
            )
            return
        # Always type vcan with user-chosen name (default can0); modprobe soft-fail
        steps = [
            ["modprobe", "vcan"],
            ["ip", "link", "add", "dev", name, "type", "vcan"],
        ]
        display = "\n".join(self._format_shell_cmd(s) for s in steps)
        if not messagebox.askyesno(
            "确认执行",
            f"创建接口 {name}（type vcan）\n\n将依次执行（非交互 sudo -n）：\n{display}\n\n继续？",
            parent=dlg,
        ):
            self._append_notice(f"已取消：创建 ({name})")
            return
        self._run_ip_system_cmd(steps[0], confirm=False, soft_fail=True, parent=dlg)
        ok = self._run_ip_system_cmd(steps[1], confirm=False, parent=dlg)
        if ok:
            self._refresh_ifaces()
            self.iface_var.set(name)
            self._refresh_idle_iface_hint()

    def _cmd_iface_up(
        self, name: str | None = None, *, parent: tk.Misc | None = None
    ) -> None:
        dlg = parent or self
        name = self._iface_cmd_target(name, parent=dlg)
        if not name:
            return
        if self._iface_looks_vcan(name):
            argv = ["ip", "link", "set", name, "up"]
            title = f"拉起 {name}（vcan，无需 bitrate）"
        else:
            br = (self.baud_var.get() or "125000").strip() or "125000"
            argv = ["ip", "link", "set", name, "up", "type", "can", "bitrate", br]
            title = f"拉起 {name}（bitrate {br}）"
        if self._run_ip_system_cmd(argv, title=title, parent=dlg):
            self._refresh_ifaces()

    def _cmd_iface_down(
        self, name: str | None = None, *, parent: tk.Misc | None = None
    ) -> None:
        dlg = parent or self
        name = self._iface_cmd_target(name, parent=dlg)
        if not name:
            return
        argv = ["ip", "link", "set", name, "down"]
        if self._run_ip_system_cmd(argv, title=f"关闭接口 {name}", parent=dlg):
            self._refresh_ifaces()

    def _refresh_ifaces(self) -> None:

        vals = list_can_interfaces()
        cur = self.iface_var.get()
        self.iface_combo.configure(values=vals)
        if cur not in vals and vals:
            self.iface_var.set(vals[0])

    def _open_conn(self) -> None:
        if self.session.connected:
            return
        name = self.iface_var.get().strip()
        if name != VIRTUAL_IFACE and not is_socketcan_present(name):
            if not messagebox.askyesno(
                "接口未就绪",
                f"{name} 不是当前系统的 socketcan 接口。\n仍要尝试？\n（也可改选 {VIRTUAL_IFACE}）",
            ):
                return
        try:
            self.session.connect(
                name,
                listen_only_shared=bool(self.settings.listen_only_shared),
            )
        except Exception as exc:
            msg = str(exc)
            title = (
                "接口占用"
                if OCCUPANCY_HINT in msg or msg.strip() == OCCUPANCY_HINT
                else "打开失败"
            )
            messagebox.showerror(title, msg)
            return
        self._set_conn_buttons(True)
        self._update_empty_state()
        self._maybe_auto_init_on_connect()

    def _close_conn(self) -> None:
        if not self.session.connected:
            return
        self._stop_period()
        self._connect_init_count = None
        self.session.disconnect()
        self._set_conn_buttons(False)
        self.status_var.set("未连接")
        self._await_replies = 0
        if hasattr(self, "_responder_state"):
            self._responder_state.reset_edge()
        self._update_empty_state()

    def _filter_ok(self, can_id: int) -> bool:
        f = self.filter_var.get().strip()
        if not f:
            return True
        try:
            want, _ = parse_can_id_auto(f, None)
        except ValueError:
            return True
        return can_id == want

    def _on_changes_only_toggle(self) -> None:
        """Main-timeline 「仅显示变化」 — persist; unchanged frames stay off the list."""
        on = bool(self._changes_only_var.get())
        self.settings.rx_changes_only = on
        self.settings = self.settings.clamp()
        try:
            save_settings(self.settings)
        except OSError:
            pass
        self._refresh_print_hint()

    def _append_frame(
        self,
        direction: str,
        can_id: int,
        extended: bool,
        data: bytes,
        ts: float,
        *,
        extra_note: str = "",
        always: bool = False,
    ) -> None:
        # TX lamp follows the send, even when the row is not added.
        if direction == "TX":
            self._pulse_led("TX")
        if not self._filter_ok(can_id):
            return
        key = frame_log_key(direction, can_id, extended)
        changed = is_new_or_changed_payload(self._last_log_by_key, key, data)
        if self.settings.rx_changes_only and not changed and not always:
            return
        t = time.strftime("%H:%M:%S", time.localtime(ts))
        note = ""
        # Optional decode helper when用户手选了通用以外文案（无硬编码档位表）
        pv = (self.profile_var.get() or "")
        if pv and pv not in (CHOICE_NONE, "通用（仅原始 HEX）", getattr(megmeet, "PROFILE_NONE", "")):
            if "Megmeet" in pv or "焊机" in pv:
                note = megmeet.decode_frame(can_id, data)
        kind = "扩展" if extended else "标准"
        if note:
            note = f"{kind} · {note}"
        else:
            note = kind
        if extra_note:
            note = f"{note} · {extra_note}"
        self.tree.insert(
            "",
            tk.END,
            values=(
                t,
                direction,
                format_can_id(can_id, extended),
                format_data_hex(data),
                note,
            ),
        )
        self._update_empty_state()
        if not self._pause.get():
            kids = self.tree.get_children()
            if kids:
                self.tree.see(kids[-1])

    def _clear(self) -> None:
        self._last_log_by_key.clear()
        for i in self.tree.get_children():
            self.tree.delete(i)
        self._update_empty_state()

    def _parse_tx_fields(self) -> tuple[int, bytes, bool]:
        """Parse ID/data/ext from send form. Raises ValueError on bad input."""
        if self.ext_var.get():
            can_id, extended = parse_can_id_auto(self.id_var.get(), True)
        else:
            can_id, extended = parse_can_id_auto(self.id_var.get(), None)
            if extended:
                self.ext_var.set(True)
        data = parse_can_data(self.data_var.get())
        return can_id, data, extended

    def _send_once(self) -> None:
        """One-shot TX; may show a single dialog on failure."""
        if not self.session.connected:
            messagebox.showwarning("未连接", "请先打开总线")
            return
        try:
            can_id, data, extended = self._parse_tx_fields()
            fr = self.session.send(can_id, data, extended)
        except Exception as exc:
            messagebox.showerror("发送失败", str(exc))
            return
        self._append_frame(
            "TX", fr.can_id, fr.is_extended, fr.data, fr.timestamp, always=True
        )
        self._arm_reply_capture()

    def _toggle_period(self) -> None:
        if self._period_running or self._period_job is not None:
            self._stop_period()
            return
        if not self.session.connected:
            messagebox.showwarning("未连接", "请先打开总线")
            return
        try:
            ms = float(self.period_var.get())
            if ms < 1:
                raise ValueError("周期至少 1 ms")
            # Validate fields once up front (bad ID still dialogs once)
            self._parse_tx_fields()
        except Exception as exc:
            messagebox.showerror("周期无效", str(exc))
            return
        self._period_fail_count = 0
        self._period_running = True
        self.period_btn.configure(text="停止周期")
        self._period_tick(ms)

    def _period_tick(self, ms: float) -> None:
        """Periodic TX: never spam messagebox; auto-stop after silent retries."""
        if not self._period_running:
            return
        if not self.session.connected:
            self._period_fail_stop("未连接")
            return
        try:
            can_id, data, extended = self._parse_tx_fields()
            fr = self.session.send(can_id, data, extended)
        except Exception as exc:
            self._period_fail_count += 1
            if self._period_fail_count <= PERIOD_FAIL_SILENT_MAX:
                # Up to N silent retries — keep UI responsive, no modal
                self._period_job = self.after(int(ms), lambda: self._period_tick(ms))
                return
            self._period_fail_stop(str(exc))
            return
        self._period_fail_count = 0
        self._append_frame("TX", fr.can_id, fr.is_extended, fr.data, fr.timestamp)
        self._arm_reply_capture()
        self._period_job = self.after(int(ms), lambda: self._period_tick(ms))

    def _period_fail_stop(self, reason: str) -> None:
        """One timeline line + status; stop timer; no modal."""
        brief = (reason or "未知错误").strip().replace("\n", " ")
        if len(brief) > 80:
            brief = brief[:77] + "…"
        line = f"周期发送失败：{brief}（已停止）"
        self._append_notice(line)
        self.status_var.set(f"周期发送失败：{brief}，已停止")
        self._stop_period()

    def _stop_period(self) -> None:
        self._period_running = False
        if self._period_job is not None:
            try:
                self.after_cancel(self._period_job)
            except Exception:
                pass
            self._period_job = None
        self._period_fail_count = 0
        self.period_btn.configure(text="开始周期")


    def _truncate_status(self, text: str, max_chars: int = 48) -> str:
        text = (text or "").replace("\n", " ").strip()
        if len(text) <= max_chars:
            return text
        return text[: max(1, max_chars - 1)] + "…"

    def _set_status_normal(self, text: str) -> None:
        self._status_full = text
        self.status_var.set(self._truncate_status(text, 64))
        try:
            self.status_label.configure(style="StatusMuted.TLabel")
        except tk.TclError:
            pass

    def _set_status_error(self, text: str) -> None:
        self._status_full = text
        # Prefer first line for the bar; full text on hover/click.
        first = (text or "").split("\n", 1)[0].strip()
        self.status_var.set(self._truncate_status(first, 42))
        try:
            self.status_label.configure(style="StatusError.TLabel")
        except tk.TclError:
            pass

    def _on_status_enter(self, _event=None) -> None:
        full = getattr(self, "_status_full", "") or self.status_var.get()
        if full and full != self.status_var.get():
            try:
                self.status_label.configure(cursor="hand2")
            except tk.TclError:
                pass
            # Native tooltip via wm — lightweight balloon
            if getattr(self, "_status_tip", None) is not None:
                return
            tip = tk.Toplevel(self)
            tip.wm_overrideredirect(True)
            tip.attributes("-topmost", True)
            x = self.status_label.winfo_rootx()
            y = self.status_label.winfo_rooty() + self.status_label.winfo_height() + 4
            tip.geometry(f"+{x}+{y}")
            lbl = tk.Label(
                tip,
                text=full,
                justify=tk.LEFT,
                background="#FFF8E7",
                foreground="#333333",
                relief=tk.SOLID,
                borderwidth=1,
                font=FONT_UI_SM,
                wraplength=420,
                padx=8,
                pady=6,
            )
            lbl.pack()
            self._status_tip = tip

    def _on_status_leave(self, _event=None) -> None:
        tip = getattr(self, "_status_tip", None)
        if tip is not None:
            try:
                tip.destroy()
            except tk.TclError:
                pass
            self._status_tip = None
        try:
            self.status_label.configure(cursor="")
        except tk.TclError:
            pass

    def _on_status_click(self, _event=None) -> None:
        full = getattr(self, "_status_full", "") or self.status_var.get()
        if not full:
            return
        if full == self.status_var.get() and "…" not in self.status_var.get():
            return
        messagebox.showinfo("状态详情", full)

    def _poll(self) -> None:
        self._check_reply_timeout()
        whitelist = parse_rx_id_whitelist(self.settings.rx_id_filter)
        for fr in self.session.drain_rx():
            is_echo = self.session.consume_self_echo(
                fr.can_id, fr.is_extended, fr.data
            )
            # Self-echo of frames we already logged as TX — hide by default.
            if is_echo and not self.settings.show_self_echo:
                continue
            # Activity lamp is independent of timeline filtering.
            self._pulse_led("RX")
            self._note_goo_rx(fr.can_id, fr.data)
            # Auto-reply before display filtering so floods still get answers
            # (skip own echo — do not answer ourselves).
            if self.settings.enable_responder and not is_echo:
                self._maybe_auto_respond(fr)
            if whitelist and fr.can_id not in whitelist:
                continue
            if is_echo:
                self._append_frame(
                    "RX",
                    fr.can_id,
                    fr.is_extended,
                    fr.data,
                    fr.timestamp,
                    extra_note="本机回环",
                )
                continue
            if (not self.settings.print_client_data) and self._await_replies > 0:
                self._await_got += 1
                self._await_replies -= 1
            self._append_frame(
                "RX", fr.can_id, fr.is_extended, fr.data, fr.timestamp
            )
        while True:
            try:
                err = self.session.error_queue.get_nowait()
            except Exception:
                break
            self._set_status_error(err)
            # Keep primary open button style — never flip to danger/red on RX errors.
            try:
                self.open_btn.configure(style="Primary.TButton")
            except tk.TclError:
                pass
        self.after(80, self._poll)

    def _responder_choice_keys(self) -> list[str]:
        """One entry per profiles/*.json — JSON `name` only (never stem)."""
        out: list[str] = []
        seen: set[str] = set()
        for m in list_responder_modules():
            key = (m.name or "").strip() or (m.path.stem if m.path else "")
            if not key or key in seen:
                continue
            seen.add(key)
            out.append(key)
        return out

    def _reload_responder_profile(self) -> None:
        key = (self.settings.responder_module or "").strip()
        # Empty / 「未选择」 → clear active profile (do not auto-pick first).
        if not key or key == CHOICE_NONE:
            self._responder_module = None
            self._sync_responder_state_from_module()
            return
        mod = find_responder_by_name_or_stem(key)
        self._responder_module = mod
        self._sync_responder_state_from_module()

    def _sync_responder_state_from_module(self) -> None:
        mod = self._responder_module
        if mod is None:
            self._responder_state = ResponderState()
            self._signal_values = {}
            self._sync_auto_arc_var_from_state()
            if hasattr(self, "_refresh_signal_card"):
                self._refresh_signal_card()
            return
        self._responder_state = state_from_mapping(mod.state)
        # Defaults from JSON signals; overlay module.state via key mapping.
        vals = signal_defaults_dict(mod.signals)
        for i, s in enumerate(mod.signals):
            name = (s.name or "").strip() or f"signal_{i}"
            if s.key and isinstance(mod.state, dict) and s.key in mod.state:
                vals[name] = mod.state[s.key]
        self._signal_values = vals
        # Push signal values into ResponderState for megmeet engine.
        mapped = values_from_signals_for_state(mod.signals, vals)
        for k, v in mapped.items():
            if hasattr(self._responder_state, k):
                try:
                    setattr(self._responder_state, k, v)
                except Exception:
                    pass
        # Keep auto_arc from profile.state (default True); UI switch mirrors it.
        if isinstance(mod.state, dict) and "auto_arc" in mod.state:
            self._responder_state.auto_arc = bool(mod.state["auto_arc"])
        self._sync_auto_arc_var_from_state()
        if hasattr(self, "_refresh_signal_card"):
            self._refresh_signal_card()

    def _on_auto_arc_toggled(self) -> None:
        """Main-UI switch for megmeet auto_arc (hardcoded arc edge; default on)."""
        on = bool(self.auto_arc_var.get())
        st = getattr(self, "_responder_state", None)
        if st is not None:
            st.auto_arc = on
        mod = getattr(self, "_responder_module", None)
        if mod is not None and isinstance(mod.state, dict):
            mod.state["auto_arc"] = on

    def _sync_auto_arc_var_from_state(self) -> None:
        st = getattr(self, "_responder_state", None)
        var = getattr(self, "auto_arc_var", None)
        if st is None or var is None:
            return
        want = bool(getattr(st, "auto_arc", True))
        try:
            if bool(var.get()) != want:
                var.set(want)
        except tk.TclError:
            pass

    def _sync_signal_vars_from_state(self) -> None:
        """Push ResponderState arc_ok/touch_ok/… into main-UI signal checkboxes."""
        mod = getattr(self, "_responder_module", None)
        st = getattr(self, "_responder_state", None)
        vars_ = getattr(self, "_signal_vars", None) or {}
        if mod is None or st is None:
            return
        for i, s in enumerate(mod.signals):
            key = getattr(s, "key", None) or ""
            if not key or not hasattr(st, key):
                continue
            name = (s.name or "").strip() or f"signal_{i}"
            val = getattr(st, key)
            self._signal_values[name] = val
            var = vars_.get(name)
            if var is None:
                continue
            kind = (s.kind or "bit").strip().lower()
            try:
                if kind == "bit":
                    if bool(var.get()) != bool(val):
                        var.set(bool(val))
                else:
                    s_val = str(val)
                    if str(var.get()) != s_val:
                        var.set(s_val)
            except tk.TclError:
                pass
        self._pull_welder_panel_from_state()

    def _refresh_signal_card(self) -> None:
        """Legacy 「信号」 card stays off the main window.

        Welder bits live on the 模拟焊机 role. 未选择 is generic HEX only.
        """
        host = getattr(self, "_signal_host", None)
        if host is None:
            return
        try:
            host.pack_forget()
        except tk.TclError:
            pass

    def _drop_saved_responder_for_generic(self) -> None:
        """未选择 does not keep a saved welder profile running in the background."""
        if self._goo_combo_active():
            return
        if not (self.settings.responder_module or "").strip():
            self._refresh_signal_card()
            return
        self.settings.responder_module = ""
        self.settings = self.settings.clamp()
        try:
            save_settings(self.settings)
        except OSError:
            pass
        self._reload_responder_profile()

    def _on_signal_changed(self, sig: SignalDef, name: str, value) -> None:
        self._signal_values[name] = value
        mod = self._responder_module
        if mod is None:
            return
        mapped = values_from_signals_for_state([sig], {name: value})
        for k, v in mapped.items():
            if hasattr(self._responder_state, k):
                try:
                    setattr(self._responder_state, k, v)
                except Exception:
                    pass
            if isinstance(mod.state, dict):
                mod.state[k] = v
        # Keep module.state auto_reply etc.; also refresh packed snapshot for static rows.
        if not mod.is_megmeet_engine() and mod.rows:
            try:
                packed = apply_signals_to_buffer(
                    mod.rows[0].reply, mod.signals, self._signal_values
                )
                mod.rows[0].reply = packed
            except Exception:
                pass

    def _open_responder_detail(self, preselect: str = "") -> None:
        if getattr(self, "_responder_detail", None) is not None:
            try:
                detail = self._responder_detail
                if isinstance(detail, ResponderDetailPanel):
                    top = detail.winfo_toplevel()
                    top.lift()
                    top.focus_set()
                    # Panel API is _select_key (not a misspelled variant).
                    if preselect and hasattr(detail, "_select_key"):
                        detail._select_key(preselect)
                    return
                detail.lift()
                detail.focus_set()
                if preselect and hasattr(detail, "_select_key"):
                    detail._select_key(preselect)
                return
            except tk.TclError:
                self._responder_detail = None
        key = preselect or (self.settings.responder_module or "")
        self._responder_detail = ResponderDetailDialog(self, preselect=key)

    def _responder_status_tag(self) -> str:
        name = "—"
        mod = getattr(self, "_responder_module", None)
        if mod is not None and getattr(mod, "name", ""):
            name = mod.name
        elif getattr(self.settings, "responder_module", ""):
            name = self.settings.responder_module
        return f"应答服务 · {name} · 运行中"

    @staticmethod
    def _virtual_hint() -> str:
        return "仅本进程可见，双开请用 vcan0"

    def _iface_is_virtual(self, name: str | None = None) -> bool:
        ch = (name if name is not None else getattr(self.session, "channel", "")) or ""
        if not ch:
            ch = self.iface_var.get().strip()
        return ch == VIRTUAL_IFACE

    def _compose_status_note(self, note: str) -> str:
        if self.settings.enable_responder:
            note = self._responder_status_tag()
        if self._iface_is_virtual():
            hint = self._virtual_hint()
            if hint not in note:
                note = f"{note} · {hint}" if note else hint
        return note

    def _set_connected_status(self, note: str) -> None:
        ch = getattr(self.session, "channel", "")
        be = getattr(self.session, "backend", "")
        note = self._compose_status_note(note)
        self._set_status_normal(f"已开 {ch} ({be}) — {note}")

    def _refresh_idle_iface_hint(self) -> None:
        """When disconnected, show virtual dual-open hint in muted status."""
        if getattr(self.session, "connected", False):
            return
        if self._iface_is_virtual(self.iface_var.get().strip()):
            self.status_var.set(f"未连接 — {self._virtual_hint()}")
        else:
            cur = self.status_var.get()
            if self._virtual_hint() in cur:
                self.status_var.set("未连接 — 默认通用监听")

    def _refresh_conn_status_suffix(self) -> None:
        if not getattr(self.session, "connected", False):
            self._refresh_idle_iface_hint()
            return
        base = self.status_var.get()
        head = base.split(" — ", 1)[0] if " — " in base else base
        note = "监听中"
        if self.settings.enable_responder:
            note = self._responder_status_tag()
        if self._iface_is_virtual():
            hint = self._virtual_hint()
            if hint not in note:
                note = f"{note} · {hint}"
        self.status_var.set(f"{head} — {note}")

    def _maybe_auto_respond(self, fr) -> None:
        if not self.settings.enable_responder or not self.session.connected:
            return
        mod = self._responder_module
        if mod is None:
            return
        rows = matching_rows(mod, int(fr.can_id))
        if not rows:
            return
        for row in rows:
            delay = (
                row.effective_delay_ms(mod.default_delay_ms)
                if hasattr(row, "effective_delay_ms")
                else (row.delay_ms or mod.default_delay_ms or 0)
            )
            data = bytes(fr.data)

            def _send(row=row, data=data) -> None:
                try:
                    self._emit_responder_reply(row, data)
                except Exception as exc:
                    brief = str(exc).replace("\n", " ")
                    if len(brief) > 80:
                        brief = brief[:77] + "…"
                    self.status_var.set(f"应答发送失败: {brief}")

            if int(delay) <= 0:
                _send()
            else:
                self.after(int(delay), _send)

    def _emit_responder_reply(self, row: ResponderRow, robot_data: bytes) -> None:
        mod = self._responder_module
        if mod is None:
            return
        # Prefer shared planner (row already matched); emit first plan for this row id
        plans = plan_reply(mod, self._responder_state, int(row.match_id), bytes(robot_data))
        if not plans:
            return
        plan = plans[0]
        # plan_reply / on_robot_frame may have auto-set arc_ok / touch_ok — mirror UI.
        self._sync_signal_vars_from_state()
        fr = self.session.send(plan.can_id, plan.data, plan.extended)
        ts = getattr(fr, "timestamp", None) or __import__("time").time()
        self._append_frame("TX", fr.can_id, fr.is_extended, fr.data, ts)

    def _manual_send_responder_frame(self) -> None:
        if not self.session.connected:
            messagebox.showwarning("未连接", "请先打开总线")
            return
        if not self.settings.enable_responder:
            messagebox.showwarning("未启用", "请先在设置中选择应答档（非「未选择」）")
            return
        mod = self._responder_module
        if mod is None:
            messagebox.showwarning("无应答档", "请先在设置中选择应答档")
            return
        rows = [r for r in mod.rows if r.enabled]
        if not rows:
            messagebox.showwarning("无启用行", "应答档没有启用的行")
            return
        try:
            self._emit_responder_reply(rows[0], bytes([0xFF, 0, 0, 0, 0, 0, 0, 0]))
            self.status_var.set("已手动发送一帧应答")
            self._refresh_conn_status_suffix()
        except Exception as exc:
            messagebox.showerror("发送失败", str(exc))

    def _on_close(self) -> None:
        self._stop_period()
        self.session.disconnect()
        self.destroy()


def run_gui(initial_iface: str = DEFAULT_IFACE) -> None:
    CanTesterApp(initial_iface=initial_iface).mainloop()

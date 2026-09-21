# -*- coding: utf-8 -*-
"""Rewrite CursorUsage settings window drawing to 美工 locked card UI.
Run on DESKTOP-00Q09L3 (Windows) where D:\\GitFiles\\... exists.
"""
from pathlib import Path
import re
import sys

CPP = Path(r"D:\GitFiles\leon-tools\cursor-usage-widget\native-cpp\CursorUsage.cpp")
BLOCK = Path(__file__).with_name("settings_beauty_block.cpp")
if len(sys.argv) > 1:
    CPP = Path(sys.argv[1])
if len(sys.argv) > 2:
    BLOCK = Path(sys.argv[2])

text = CPP.read_text(encoding="utf-8")
nl = "\r\n" if "\r\n" in text else "\n"
c = text.replace("\r\n", "\n")
new_block = BLOCK.read_text(encoding="utf-8").replace("\r\n", "\n")
if not new_block.endswith("\n"):
    new_block += "\n"

# Prefer replacing existing settings block (美工 comment or Settings window)
patterns = [
    r"// ---- Settings window \(美工.*?\nstatic void OpenSettings\(\) \{.*?\n\}\n",
    r"// ---- Settings window.*?\nstatic void OpenSettings\(\) \{.*?\n\}\n",
    r"// ---- settings window.*?\nstatic void OpenSettings\(\) \{.*?\n\}\n",
]
replaced = False
for pat in patterns:
    m = re.search(pat, c, re.S)
    if m:
        c = c[: m.start()] + new_block + c[m.end() :]
        replaced = True
        print("replaced_via", pat[:40])
        break

if not replaced:
    # Insert before DrawQuickLaunch if settings symbols missing
    if "PaintSettings" in c and "SettingsLayout" in c:
        # Try tighter: from IDM_SETTINGS / struct SettingsDlg through OpenSettings
        m = re.search(
            r"static const int IDM_SETTINGS = 18;.*?static void OpenSettings\(\) \{.*?\n\}\n",
            c,
            re.S,
        )
        if m:
            c = c[: m.start()] + new_block + c[m.end() :]
            replaced = True
            print("replaced_via IDM_SETTINGS..OpenSettings")
    if not replaced:
        marker = "static void DrawQuickLaunch"
        idx = c.find(marker)
        if idx < 0:
            raise SystemExit("FAIL: cannot find settings block or DrawQuickLaunch")
        c = c[:idx] + new_block + "\n" + c[idx:]
        print("inserted_before DrawQuickLaunch")

# Ensure RoundRectPath not duplicated outside block (keep first if any leftover twin)
# If two identical RoundRectPath statics remain adjacent to settings only — OK inside block once.

# OpenSettings forward decl may already exist
if "static void OpenSettings();" not in c:
    # place near Repaint forward if present
    m = re.search(r"static void Repaint\(HWND h\);", c)
    if m:
        c = c[: m.end()] + "\nstatic void OpenSettings();" + c[m.end() :]
        print("added OpenSettings forward decl")

out = c.replace("\n", nl)
CPP.write_text(out, encoding="utf-8", newline="")
print("OK wrote", CPP, "lines", out.count("\n") + 1)
print("has PaintSettings", "PaintSettings" in c)
print("has SettingsContentH", "SettingsContentH" in c)
print("card title 卡片透明度", "\\u5361\\u7247\\u900f\\u660e\\u5ea6" in c or "卡片透明度" in c)

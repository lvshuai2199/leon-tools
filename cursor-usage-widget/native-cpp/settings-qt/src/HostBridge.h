#pragma once
#include "ConfigStore.h"

// Talk to the running Win32 tray process (CursorUsageWidget).
class HostBridge {
public:
    // Ask tray to reload ui.ini (+ shortcuts after tray patch) and Place/Repaint.
    static bool notifyConfigChanged();
    // Ask tray to run ClearCursorHistory with flags (does not duplicate path logic).
    static bool requestClearHistory(const ClearOptions& opt);
    // Ask tray to change wallpaper immediately (WpStart).
    static bool requestWallpaperNow();
    // Ask tray to open the installed-apps manage dialog (OpenManageShortcuts).
    static bool requestOpenManageShortcuts();
    // Bring existing settings singleton to front, or report none.
    static bool findOwnWindow();
};

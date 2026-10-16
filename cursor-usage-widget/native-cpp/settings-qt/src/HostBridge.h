#pragma once
#include "ConfigStore.h"

// Talk to the running Win32 tray process (CursorUsageWidget).
class HostBridge {
public:
    // Ask tray to reload ui.ini and Place/Repaint.
    static bool notifyConfigChanged();
    // Ask tray to run ClearCursorHistory with flags (does not duplicate path logic).
    static bool requestClearHistory(const ClearOptions& opt);
    // Bring existing settings singleton to front, or report none.
    static bool findOwnWindow();
};

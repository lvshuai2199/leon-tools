#include "HostBridge.h"
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

// Must match CursorUsage.cpp after patch.
#ifndef WM_CUW_RELOAD_CONFIG
#define WM_CUW_RELOAD_CONFIG (WM_APP + 0x41)
#endif
#ifndef WM_CUW_CLEAR_HISTORY
#define WM_CUW_CLEAR_HISTORY (WM_APP + 0x42)
#endif
#ifndef WM_CUW_WALLPAPER_NOW
#define WM_CUW_WALLPAPER_NOW (WM_APP + 0x43)
#endif
#ifndef WM_CUW_OPEN_MANAGE_SHORTCUTS
#define WM_CUW_OPEN_MANAGE_SHORTCUTS (WM_APP + 0x44)
#endif

bool HostBridge::notifyConfigChanged() {
#ifdef _WIN32
    HWND h = FindWindowW(L"CursorUsageWidget", nullptr);
    if (!h) return false;
    return PostMessageW(h, WM_CUW_RELOAD_CONFIG, 0, 0) != 0;
#else
    return false;
#endif
}

bool HostBridge::requestClearHistory(const ClearOptions& opt) {
#ifdef _WIN32
    HWND h = FindWindowW(L"CursorUsageWidget", nullptr);
    if (!h) return false;
    WPARAM flags = 0;
    if (opt.local) flags |= 1;
    if (opt.archived) flags |= 2;
    if (opt.cache) flags |= 4;
    if (opt.sidebar) flags |= 8;
    return PostMessageW(h, WM_CUW_CLEAR_HISTORY, flags, 0) != 0;
#else
    (void)opt;
    return false;
#endif
}

bool HostBridge::requestWallpaperNow() {
#ifdef _WIN32
    HWND h = FindWindowW(L"CursorUsageWidget", nullptr);
    if (!h) return false;
    return PostMessageW(h, WM_CUW_WALLPAPER_NOW, 0, 0) != 0;
#else
    return false;
#endif
}

bool HostBridge::requestOpenManageShortcuts() {
#ifdef _WIN32
    HWND h = FindWindowW(L"CursorUsageWidget", nullptr);
    if (!h) return false;
    return PostMessageW(h, WM_CUW_OPEN_MANAGE_SHORTCUTS, 0, 0) != 0;
#else
    return false;
#endif
}

bool HostBridge::findOwnWindow() {
#ifdef _WIN32
    HWND h = FindWindowW(L"CursorUsageSettingsQt", nullptr);
    if (!h) return false;
    ShowWindow(h, SW_SHOW);
    SetForegroundWindow(h);
    return true;
#else
    return false;
#endif
}

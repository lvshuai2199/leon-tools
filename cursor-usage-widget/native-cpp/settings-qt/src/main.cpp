#include <QApplication>
#include "SettingsWindow.h"
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

int main(int argc, char* argv[]) {
#ifdef _WIN32
    HANDLE mutex = CreateMutexW(nullptr, TRUE, L"Local\\CursorUsageSettingsQt");
    if (mutex && GetLastError() == ERROR_ALREADY_EXISTS) {
        HWND h = FindWindowW(nullptr, L"设置");
        if (h) {
            ShowWindow(h, SW_SHOW);
            SetForegroundWindow(h);
        }
        if (mutex) CloseHandle(mutex);
        return 0;
    }
#endif
    QApplication app(argc, argv);
    app.setQuitOnLastWindowClosed(true);
    app.setApplicationName(QStringLiteral("CursorUsageSettings"));
    app.setOrganizationName(QStringLiteral("cursor-usage-widget"));

    SettingsWindow w;
    w.show();
    const int rc = app.exec();
#ifdef _WIN32
    if (mutex) { ReleaseMutex(mutex); CloseHandle(mutex); }
#endif
    return rc;
}

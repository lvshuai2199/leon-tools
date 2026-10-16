#include <QApplication>
#include <QIcon>
#include <QFileInfo>
#include <QDir>
#include <QCoreApplication>
#include "SettingsWindow.h"
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

static QIcon loadAppIcon() {
    // RC-embedded EXE icon covers Explorer/taskbar; this sets Qt window/title icon.
    const QString dir = QCoreApplication::applicationDirPath();
    const QStringList candidates = {
        dir + QStringLiteral("/app.ico"),
        dir + QStringLiteral("/usage-mark.ico"),
        QDir(dir).absoluteFilePath(QStringLiteral("../assets/app.ico")),
    };
    for (const QString& p : candidates) {
        if (QFileInfo::exists(p)) {
            QIcon ic(p);
            if (!ic.isNull()) return ic;
        }
    }
    return {};
}

int main(int argc, char* argv[]) {
#ifdef _WIN32
    HANDLE mutex = CreateMutexW(nullptr, TRUE, L"Local\\CursorUsageSettingsQt");
    if (mutex && GetLastError() == ERROR_ALREADY_EXISTS) {
        // Must match SettingsWindow::setWindowTitle(QStringLiteral("设置"))
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

    const QIcon appIcon = loadAppIcon();
    if (!appIcon.isNull())
        app.setWindowIcon(appIcon);

    SettingsWindow w;
    if (!appIcon.isNull())
        w.setWindowIcon(appIcon);
    w.show();
    const int rc = app.exec();
#ifdef _WIN32
    if (mutex) { ReleaseMutex(mutex); CloseHandle(mutex); }
#endif
    return rc;
}

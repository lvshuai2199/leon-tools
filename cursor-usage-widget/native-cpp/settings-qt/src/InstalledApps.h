#pragma once
#include <QIcon>
#include <QString>
#include <QVector>

struct InstalledApp {
    QString name;
    QString launchPath;  // .lnk / .exe / shell:AppsFolder\\AUMID
    QString target;      // resolved exe when known
    QString aumid;       // AppUserModelId when known
    bool isSystem = false;
};

class InstalledApps {
public:
    // Start Menu .lnk + Uninstall registry + shell:AppsFolder (Win32 + UWP), name-deduped.
    static QVector<InstalledApp> enumerate();
    static QString resolveLnk(const QString& lnkPath);
    static bool isSystemApp(const QString& name, const QString& launchPath, const QString& target);
    static bool isShellAppsPath(const QString& path);
    // High-res extract then smooth-scale into multi-size QIcon (never upscale 16px).
    static QIcon iconFor(const QString& launchPath, const QString& target = {});
    static QIcon placeholderIcon();
};

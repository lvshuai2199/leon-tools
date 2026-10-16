#pragma once
#include <QString>
#include <QStringList>
#include <QVector>

struct InstalledApp {
    QString name;
    QString launchPath;  // .lnk or .exe written to shortcuts.txt
    QString target;      // resolved exe when known
    bool isSystem = false;
};

class InstalledApps {
public:
    // Start Menu .lnk (user + common) + Uninstall registry (HKCU/HKLM/Wow6432Node).
    static QVector<InstalledApp> enumerate();
    static QString resolveLnk(const QString& lnkPath);
    static bool isSystemApp(const QString& name, const QString& launchPath, const QString& target);
};

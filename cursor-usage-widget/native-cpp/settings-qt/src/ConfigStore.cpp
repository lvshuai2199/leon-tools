#include "ConfigStore.h"
#include <QMap>
#include <QSet>
#include <QStringConverter>
#include <QDir>
#include <QFile>
#include <QStandardPaths>
#include <QTextStream>

QString ConfigStore::configPath() {
    const QString dir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    // Match Win32: %APPDATA%\cursor-usage-widget\ui.ini
    // Qt AppDataLocation on Windows is already %APPDATA%/<org>/<app>; override manually.
    const QString appdata = qEnvironmentVariable("APPDATA");
    QString base = appdata.isEmpty()
        ? QStandardPaths::writableLocation(QStandardPaths::GenericConfigLocation)
        : appdata;
    QDir().mkpath(base + "/cursor-usage-widget");
    return base + "/cursor-usage-widget/ui.ini";
}

static QString readVal(const QMap<QString, QString>& m, const QString& k, const QString& def = {}) {
    return m.value(k, def);
}

AppConfig ConfigStore::load() {
    AppConfig c;
    QFile f(configPath());
    if (!f.open(QIODevice::ReadOnly | QIODevice::Text))
        return c;
    QMap<QString, QString> m;
    QTextStream in(&f);
    in.setEncoding(QStringConverter::Utf8);
    while (!in.atEnd()) {
        const QString line = in.readLine().trimmed();
        const int eq = line.indexOf('=');
        if (eq <= 0) continue;
        m.insert(line.left(eq), line.mid(eq + 1));
    }
    const QString dock = readVal(m, "dock", "right");
    if (dock == "left") c.dockEdge = 0;
    else if (dock == "top") c.dockEdge = 2;
    else c.dockEdge = 1;
    c.autoHide = readVal(m, "autoHide", "1") != "0";
    c.showBot = readVal(m, "showBot", "0") == "1";
    c.showApi = readVal(m, "showApi", "1") == "1";
    c.showModels = readVal(m, "showModels", "1") == "1";
    c.ringMode = readVal(m, "ringMode", "0") == "1" ? 1 : 0;
    c.wpEnabled = readVal(m, "wpEnabled", "0") == "1";
    c.wpUrl = readVal(m, "wpUrl");
    c.wpInterval = readVal(m, "wpInterval", "3").toInt();
    if (c.wpInterval < 0 || c.wpInterval > 4) c.wpInterval = 3;
    return c;
}

bool ConfigStore::save(const AppConfig& c) {
    // Preserve unknown keys (scale, y, wpLastChange, ...)
    QMap<QString, QString> m;
    QFile fin(configPath());
    if (fin.open(QIODevice::ReadOnly | QIODevice::Text)) {
        QTextStream in(&fin);
        in.setEncoding(QStringConverter::Utf8);
        while (!in.atEnd()) {
            const QString line = in.readLine();
            const int eq = line.indexOf('=');
            if (eq <= 0) continue;
            m.insert(line.left(eq).trimmed(), line.mid(eq + 1).trimmed());
        }
        fin.close();
    }
    m["dock"] = c.dockEdge == 0 ? "left" : (c.dockEdge == 2 ? "top" : "right");
    m["autoHide"] = c.autoHide ? "1" : "0";
    m["showBot"] = c.showBot ? "1" : "0";
    m["showApi"] = c.showApi ? "1" : "0";
    m["showModels"] = c.showModels ? "1" : "0";
    m["ringMode"] = QString::number(c.ringMode);
    m["wpEnabled"] = c.wpEnabled ? "1" : "0";
    QString url = c.wpUrl;
    url.replace('\r', "").replace('\n', "");
    m["wpUrl"] = url;
    m["wpInterval"] = QString::number(c.wpInterval);

    QFile fout(configPath());
    if (!fout.open(QIODevice::WriteOnly | QIODevice::Text | QIODevice::Truncate))
        return false;
    QTextStream out(&fout);
    out.setEncoding(QStringConverter::Utf8);
    // Stable order similar to Win32 SaveConfig
    const QStringList order = {
        "scale", "dock", "y", "showBot", "showApi", "showModels", "ringMode", "autoHide",
        "wpEnabled", "wpUrl", "wpInterval", "wpLastChange", "wpStatus", "wpStatusAt"
    };
    QSet<QString> written;
    for (const QString& k : order) {
        if (!m.contains(k)) continue;
        out << k << '=' << m[k] << '\n';
        written.insert(k);
    }
    for (auto it = m.begin(); it != m.end(); ++it) {
        if (written.contains(it.key())) continue;
        out << it.key() << '=' << it.value() << '\n';
    }
    return true;
}

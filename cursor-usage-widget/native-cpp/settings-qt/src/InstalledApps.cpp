#include "InstalledApps.h"
#include <string>

#include <QDir>
#include <QDirIterator>
#include <QFileInfo>
#include <QSet>
#include <algorithm>

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <shlobj.h>
#include <objbase.h>
#include <shlguid.h>
#endif

namespace {

QString lower(const QString& s) { return s.toLower(); }

QString fileTitle(const QString& path) {
    QFileInfo fi(path);
    const QString base = fi.completeBaseName();
    return base.isEmpty() ? fi.fileName() : base;
}

bool endsWithI(const QString& s, const QString& ext) {
    return s.endsWith(ext, Qt::CaseInsensitive);
}

bool skipInstalled(const QString& name, const QString& target) {
    const QString n = lower(name);
    const QString t = lower(target);
    static const char* bad[] = {
        "uninstall", u8"卸载", "help", u8"帮助", "readme", "release notes",
        "documentation", "website", u8"手册", "eula", "license"
    };
    for (const char* b : bad) {
        if (n.contains(QString::fromUtf8(b))) return true;
    }
    if (t.contains(QStringLiteral("uninstall"))) return true;
    if (t.contains(QStringLiteral("unins000"))) return true;
    return false;
}

QString stripIconPath(QString s) {
    s = s.trimmed();
    if (s.size() >= 2 && s.front() == QLatin1Char('"')) {
        const int e = s.indexOf(QLatin1Char('"'), 1);
        if (e > 0) s = s.mid(1, e - 1);
    }
    const int c = s.lastIndexOf(QLatin1Char(','));
    if (c > 2) {
        bool idx = true;
        for (int i = c + 1; i < s.size(); ++i) {
            const QChar ch = s[i];
            if (ch == QLatin1Char('-') && i == c + 1) continue;
            if (!ch.isDigit()) { idx = false; break; }
        }
        if (idx) s = s.left(c);
    }
    return s.trimmed();
}

void addInstalled(QVector<InstalledApp>& out, QSet<QString>& seen,
                  const QString& name, const QString& launch, const QString& target) {
    if (name.isEmpty() || launch.isEmpty()) return;
    if (skipInstalled(name, target)) return;
    const QString key = lower(target.isEmpty() ? launch : target);
    if (key.isEmpty() || seen.contains(key)) return;
    seen.insert(key);
    InstalledApp a;
    a.name = name;
    a.launchPath = QDir::toNativeSeparators(launch);
    a.target = target.isEmpty() ? QString() : QDir::toNativeSeparators(target);
    a.isSystem = InstalledApps::isSystemApp(a.name, a.launchPath, a.target);
    out.push_back(a);
}

#ifdef _WIN32
void scanDirLnks(const QString& dir, QVector<InstalledApp>& out, QSet<QString>& seen) {
    QDirIterator it(dir, QStringList() << QStringLiteral("*.lnk"),
                    QDir::Files, QDirIterator::Subdirectories);
    while (it.hasNext()) {
        const QString full = it.next();
        const QString target = InstalledApps::resolveLnk(full);
        addInstalled(out, seen, fileTitle(full), full, target);
    }
}

void scanUninstallKey(HKEY root, const wchar_t* sub, QVector<InstalledApp>& out, QSet<QString>& seen) {
    HKEY k = nullptr;
    if (RegOpenKeyExW(root, sub, 0, KEY_READ, &k) != ERROR_SUCCESS) return;
    for (DWORD i = 0;; ++i) {
        wchar_t keyName[256];
        DWORD nlen = 256;
        if (RegEnumKeyExW(k, i, keyName, &nlen, nullptr, nullptr, nullptr, nullptr) != ERROR_SUCCESS)
            break;
        HKEY a = nullptr;
        if (RegOpenKeyExW(k, keyName, 0, KEY_READ, &a) != ERROR_SUCCESS) continue;
        wchar_t display[512] = {};
        DWORD sz = sizeof(display);
        DWORD type = 0;
        if (RegQueryValueExW(a, L"DisplayName", nullptr, &type, (LPBYTE)display, &sz) != ERROR_SUCCESS || !display[0]) {
            RegCloseKey(a);
            continue;
        }
        DWORD sys = 0;
        sz = sizeof(sys);
        if (RegQueryValueExW(a, L"SystemComponent", nullptr, &type, (LPBYTE)&sys, &sz) == ERROR_SUCCESS && sys == 1) {
            RegCloseKey(a);
            continue;
        }
        wchar_t icon[MAX_PATH] = {};
        sz = sizeof(icon);
        if (RegQueryValueExW(a, L"DisplayIcon", nullptr, &type, (LPBYTE)icon, &sz) != ERROR_SUCCESS || !icon[0]) {
            RegCloseKey(a);
            continue;
        }
        QString exe = stripIconPath(QString::fromWCharArray(icon));
        if (!endsWithI(exe, QStringLiteral(".exe"))) {
            RegCloseKey(a);
            continue;
        }
        if (!QFileInfo::exists(exe)) {
            RegCloseKey(a);
            continue;
        }
        addInstalled(out, seen, QString::fromWCharArray(display), exe, exe);
        RegCloseKey(a);
    }
    RegCloseKey(k);
}
#endif

} // namespace

bool InstalledApps::isSystemApp(const QString& name, const QString& launchPath, const QString& target) {
    const QString pth = lower(target.isEmpty() ? launchPath : target);
    const QString n = lower(name);
    static const char* keys[] = {
        "\\windows\\", "\\system32\\", "\\syswow64\\",
        "\\windowsapps\\", "\\systemapps\\",
        "\\program files\\windows nt\\",
        "\\program files\\windows photo viewer\\",
        "\\windowsdefender\\", "microsoftedge", "\\ime\\",
        "\\inputmethod\\", "\\accessibility\\",
        "\\internet explorer\\",
        "\\program files\\internet explorer\\",
        "\\program files (x86)\\internet explorer\\"
    };
    for (const char* k : keys) {
        if (pth.contains(QString::fromLatin1(k))) return true;
    }
    if (pth.contains(QStringLiteral("\\windows accessories\\"))) return true;
    if (pth.contains(QStringLiteral("\\administrative tools\\"))) return true;
    if (pth.contains(QStringLiteral("\\system tools\\"))) return true;
    if (n == QStringLiteral("cmd") || n == QStringLiteral("powershell") ||
        n.contains(QStringLiteral("windows powershell"))) return true;
    if (n.contains(QStringLiteral("internet explorer"))) return true;
    if (n.contains(QStringLiteral("developer command prompt"))) return true;
    if (n.contains(QStringLiteral("x64 native tools")) || n.contains(QStringLiteral("x86 native tools")))
        return true;
    return false;
}

QString InstalledApps::resolveLnk(const QString& lnkPath) {
#ifdef _WIN32
    QString target;
    IShellLinkW* sl = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER,
                                  IID_IShellLinkW, (void**)&sl);
    if (FAILED(hr) || !sl) return {};
    IPersistFile* pf = nullptr;
    if (SUCCEEDED(sl->QueryInterface(IID_IPersistFile, (void**)&pf)) && pf) {
        const std::wstring w = lnkPath.toStdWString();
        if (SUCCEEDED(pf->Load(w.c_str(), STGM_READ))) {
            sl->Resolve(nullptr, SLR_NO_UI | SLR_NOUPDATE | SLR_NOSEARCH);
            wchar_t buf[MAX_PATH] = {};
            WIN32_FIND_DATAW fd{};
            if (SUCCEEDED(sl->GetPath(buf, MAX_PATH, &fd, SLGP_RAWPATH)) && buf[0])
                target = QString::fromWCharArray(buf);
        }
        pf->Release();
    }
    sl->Release();
    return QDir::toNativeSeparators(target);
#else
    Q_UNUSED(lnkPath);
    return {};
#endif
}

QVector<InstalledApp> InstalledApps::enumerate() {
    QVector<InstalledApp> out;
    QSet<QString> seen;
#ifdef _WIN32
    wchar_t folder[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_PROGRAMS, nullptr, SHGFP_TYPE_CURRENT, folder)))
        scanDirLnks(QString::fromWCharArray(folder), out, seen);
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_COMMON_PROGRAMS, nullptr, SHGFP_TYPE_CURRENT, folder)))
        scanDirLnks(QString::fromWCharArray(folder), out, seen);
    scanUninstallKey(HKEY_CURRENT_USER,
                     L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, seen);
    scanUninstallKey(HKEY_LOCAL_MACHINE,
                     L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, seen);
    scanUninstallKey(HKEY_LOCAL_MACHINE,
                     L"Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, seen);
#endif
    std::sort(out.begin(), out.end(), [](const InstalledApp& a, const InstalledApp& b) {
        return QString::localeAwareCompare(a.name, b.name) < 0;
    });
    return out;
}

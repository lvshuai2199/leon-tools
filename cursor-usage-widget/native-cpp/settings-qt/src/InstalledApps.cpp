#include "InstalledApps.h"

#include <QDir>
#include <QDirIterator>
#include <QFileInfo>
#include <QFileIconProvider>
#include <QImage>
#include <QPainter>
#include <QPixmap>
#include <QSet>
#include <algorithm>
#include <string>

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <shlobj.h>
#include <shobjidl.h>
#include <objbase.h>
#include <shlguid.h>
#include <propvarutil.h>
#endif

#ifdef _WIN32
// kFolderIdAppsFolder without INITGUID
static const GUID kFolderIdAppsFolder =
    {0x1e87508d, 0x89c2, 0x42f0, {0x8a, 0x7e, 0x64, 0x5a, 0x0f, 0x50, 0xca, 0x58}};
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
    if (skipInstalled(name, target.isEmpty() ? launch : target)) return;
    // Dedup: prefer real path key; for shell: use full launch
    QString key = lower(target);
    if (key.isEmpty()) key = lower(launch);
    if (key.isEmpty() || seen.contains(key)) return;
    // Also block duplicate shell/exe for same aumid vs path already seen via launch
    if (seen.contains(lower(launch))) return;
    seen.insert(key);
    seen.insert(lower(launch));
    InstalledApp a;
    a.name = name;
    a.launchPath = InstalledApps::isShellAppsPath(launch) ? launch : QDir::toNativeSeparators(launch);
    a.target = target.isEmpty() ? QString()
        : (InstalledApps::isShellAppsPath(target) ? target : QDir::toNativeSeparators(target));
    a.isSystem = InstalledApps::isSystemApp(a.name, a.launchPath, a.target);
    out.push_back(a);
}

#ifdef _WIN32
// Manual PROPERTYKEYs (avoid INITGUID pulling entire propkey.h)
static const PROPERTYKEY kPKeyAumid = {
    {0x9f4c2855, 0x9f79, 0x4B39, {0xa8, 0xd0, 0xe1, 0xd4, 0x2d, 0xe1, 0xd5, 0xf3}}, 5};
static const PROPERTYKEY kPKeyLinkTarget = {
    {0xB9B4B3FC, 0x2B51, 0x4A42, {0xBE, 0x5D, 0x46, 0x9B, 0x10, 0x67, 0x59, 0x3A}}, 2};

static QString propToString(const PROPVARIANT& pv) {
    if (pv.vt == VT_LPWSTR && pv.pwszVal) return QString::fromWCharArray(pv.pwszVal);
    if (pv.vt == VT_BSTR && pv.bstrVal) return QString::fromWCharArray(pv.bstrVal);
    wchar_t buf[1024] = {};
    if (SUCCEEDED(PropVariantToString(pv, buf, 1024)) && buf[0])
        return QString::fromWCharArray(buf);
    return {};
}

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

        QString exe;
        wchar_t icon[MAX_PATH] = {};
        sz = sizeof(icon);
        if (RegQueryValueExW(a, L"DisplayIcon", nullptr, &type, (LPBYTE)icon, &sz) == ERROR_SUCCESS && icon[0]) {
            exe = stripIconPath(QString::fromWCharArray(icon));
            if (!endsWithI(exe, QStringLiteral(".exe")) || !QFileInfo::exists(exe))
                exe.clear();
        }
        // No DisplayIcon / broken icon: try InstallLocation for a reasonable .exe (keep entry if found).
        if (exe.isEmpty()) {
            wchar_t loc[MAX_PATH] = {};
            sz = sizeof(loc);
            if (RegQueryValueExW(a, L"InstallLocation", nullptr, &type, (LPBYTE)loc, &sz) == ERROR_SUCCESS && loc[0]) {
                const QString dir = QString::fromWCharArray(loc);
                QDirIterator it(dir, QStringList() << QStringLiteral("*.exe"), QDir::Files);
                QString best;
                while (it.hasNext()) {
                    const QString p = it.next();
                    const QString bn = QFileInfo(p).fileName().toLower();
                    if (bn.contains(QStringLiteral("uninstall")) || bn.contains(QStringLiteral("update"))
                        || bn.contains(QStringLiteral("setup")) || bn.contains(QStringLiteral("helper")))
                        continue;
                    best = p;
                    break;
                }
                exe = best;
            }
        }
        // Still no launchable exe: keep name with empty target skipped — AppsFolder covers UWP.
        if (exe.isEmpty()) {
            RegCloseKey(a);
            continue;
        }
        addInstalled(out, seen, QString::fromWCharArray(display), exe, exe);
        RegCloseKey(a);
    }
    RegCloseKey(k);
}

void scanAppsFolder(QVector<InstalledApp>& out, QSet<QString>& seen) {
    IShellItem* folder = nullptr;
    HRESULT hr = SHGetKnownFolderItem(kFolderIdAppsFolder, KF_FLAG_DEFAULT, nullptr,
                                      IID_IShellItem, (void**)&folder);
    if (FAILED(hr) || !folder) {
        hr = SHCreateItemFromParsingName(L"shell:AppsFolder", nullptr, IID_IShellItem, (void**)&folder);
        if (FAILED(hr) || !folder) return;
    }
    IEnumShellItems* en = nullptr;
    hr = folder->BindToHandler(nullptr, BHID_EnumItems, IID_IEnumShellItems, (void**)&en);
    if (FAILED(hr) || !en) {
        folder->Release();
        return;
    }
    IShellItem* item = nullptr;
    while (en->Next(1, &item, nullptr) == S_OK) {
        wchar_t* nameW = nullptr;
        QString name;
        if (SUCCEEDED(item->GetDisplayName(SIGDN_NORMALDISPLAY, &nameW)) && nameW) {
            name = QString::fromWCharArray(nameW);
            CoTaskMemFree(nameW);
        }
        if (name.isEmpty()) {
            item->Release();
            continue;
        }

        QString aumid;
        QString targetExe;
        IPropertyStore* store = nullptr;
        if (SUCCEEDED(item->BindToHandler(nullptr, BHID_PropertyStore, IID_IPropertyStore, (void**)&store)) && store) {
            PROPVARIANT pv;
            PropVariantInit(&pv);
            if (SUCCEEDED(store->GetValue(kPKeyAumid, &pv))) {
                aumid = propToString(pv);
                PropVariantClear(&pv);
            }
            PropVariantInit(&pv);
            if (SUCCEEDED(store->GetValue(kPKeyLinkTarget, &pv))) {
                targetExe = propToString(pv);
                PropVariantClear(&pv);
            }
            store->Release();
        }

        if (!targetExe.isEmpty() && endsWithI(targetExe, QStringLiteral(".exe")) && QFileInfo::exists(targetExe)) {
            // Win32 tile in AppsFolder — launch the exe (dedup vs Start Menu by target).
            addInstalled(out, seen, name, targetExe, targetExe);
        } else if (!aumid.isEmpty()) {
            const QString launch = QStringLiteral("shell:AppsFolder\\") + aumid;
            addInstalled(out, seen, name, launch, QString());
        } else {
            // Nameless protocol / odd tile: still show with parsing name if available.
            wchar_t* parseW = nullptr;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_DESKTOPABSOLUTEPARSING, &parseW)) && parseW) {
                const QString parse = QString::fromWCharArray(parseW);
                CoTaskMemFree(parseW);
                if (!parse.isEmpty())
                    addInstalled(out, seen, name, parse, QString());
            }
        }
        item->Release();
    }
    en->Release();
    folder->Release();
}

static QPixmap pixmapFromHBITMAP(HBITMAP hbm) {
    if (!hbm) return {};
    BITMAP bm{};
    if (!GetObject(hbm, sizeof(bm), &bm) || bm.bmWidth <= 0 || bm.bmHeight <= 0) return {};
    BITMAPINFO bmi{};
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = bm.bmWidth;
    bmi.bmiHeader.biHeight = -bm.bmHeight;
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 32;
    bmi.bmiHeader.biCompression = BI_RGB;
    QImage img(bm.bmWidth, bm.bmHeight, QImage::Format_ARGB32_Premultiplied);
    if (img.isNull()) return {};
    HDC hdc = GetDC(nullptr);
    const int got = GetDIBits(hdc, hbm, 0, bm.bmHeight, img.bits(), &bmi, DIB_RGB_COLORS);
    ReleaseDC(nullptr, hdc);
    if (!got) return {};
    return QPixmap::fromImage(img);
}

static QIcon iconFromShellParsingName(const QString& parsing) {
    if (parsing.isEmpty()) return {};
    IShellItem* item = nullptr;
    const std::wstring w = parsing.toStdWString();
    if (FAILED(SHCreateItemFromParsingName(w.c_str(), nullptr, IID_IShellItem, (void**)&item)) || !item)
        return {};
    IShellItemImageFactory* factory = nullptr;
    QIcon out;
    if (SUCCEEDED(item->QueryInterface(IID_IShellItemImageFactory, (void**)&factory)) && factory) {
        SIZE sz{32, 32};
        HBITMAP hbm = nullptr;
        if (SUCCEEDED(factory->GetImage(sz, SIIGBF_RESIZETOFIT | SIIGBF_ICONBACKGROUND, &hbm)) && hbm) {
            const QPixmap pm = pixmapFromHBITMAP(hbm);
            DeleteObject(hbm);
            if (!pm.isNull()) out = QIcon(pm);
        }
        factory->Release();
    }
    item->Release();
    return out;
}
#endif

} // namespace

bool InstalledApps::isShellAppsPath(const QString& path) {
    return path.startsWith(QStringLiteral("shell:"), Qt::CaseInsensitive);
}

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
    // UWP / shell:AppsFolder system-ish AUMIDs
    if (isShellAppsPath(launchPath)) {
        const QString id = lower(launchPath);
        static const char* sysAumid[] = {
            "shellexperiencehost", "startmenuexperiencehost", "searchui", "searchapp",
            "xboxgamecallableui", "lockapp", "narratorquickstart", "peopleexperiencehost",
            "microsoft.aad.brokerplugin", "microsoft.accountscontrol",
            "microsoft.windows.cloudexperiencehost", "microsoft.windows.contentdeliverymanager",
            "windows.immersivecontrolpanel", "microsoft.windows.cortana"
        };
        for (const char* s : sysAumid) {
            if (id.contains(QString::fromLatin1(s))) return true;
        }
    }
    return false;
}

QIcon InstalledApps::placeholderIcon() {
    QPixmap pm(32, 32);
    pm.fill(Qt::transparent);
    QPainter p(&pm);
    p.setRenderHint(QPainter::Antialiasing);
    p.setBrush(QColor(0xD1, 0xD5, 0xDB));
    p.setPen(QColor(0x9C, 0xA3, 0xAF));
    p.drawRoundedRect(QRectF(2, 2, 28, 28), 6, 6);
    p.setPen(QPen(QColor(0x6B, 0x72, 0x80), 1.5));
    p.drawLine(11, 16, 21, 16);
    p.drawLine(16, 11, 16, 21);
    p.end();
    return QIcon(pm);
}

QIcon InstalledApps::iconFor(const QString& launchPath, const QString& target) {
#ifdef _WIN32
    if (isShellAppsPath(launchPath)) {
        QIcon ic = iconFromShellParsingName(launchPath);
        if (!ic.isNull()) return ic;
        return placeholderIcon();
    }
#endif
    const QString path = !target.isEmpty() ? target : launchPath;
    if (!path.isEmpty() && QFileInfo::exists(path)) {
        QFileIconProvider prov;
        QIcon ic = prov.icon(QFileInfo(path));
        if (!ic.isNull()) return ic;
    }
#ifdef _WIN32
    // .lnk may still yield a shell icon via parsing name
    if (endsWithI(launchPath, QStringLiteral(".lnk"))) {
        QIcon ic = iconFromShellParsingName(launchPath);
        if (!ic.isNull()) return ic;
    }
#endif
    return placeholderIcon();
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
    // AppsFolder last so Win32 tiles dedupe against Start Menu; UWP/AUMID fill gaps.
    scanAppsFolder(out, seen);
#endif
    std::sort(out.begin(), out.end(), [](const InstalledApp& a, const InstalledApp& b) {
        return QString::localeAwareCompare(a.name, b.name) < 0;
    });
    return out;
}

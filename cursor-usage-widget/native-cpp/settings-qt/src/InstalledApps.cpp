#include "InstalledApps.h"

#include <QDir>
#include <QDirIterator>
#include <QFileInfo>
#include <QFileIconProvider>
#include <QImage>
#include <QPainter>
#include <QPixmap>
#include <QSet>
#include <QHash>
#include <QGuiApplication>
#include <algorithm>
#include <string>
#include <cstring>

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
#include <shellapi.h>
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

QString normName(QString n) {
    n = n.trimmed().toLower();
    // strip common noise
    static const char* junk[] = {
        " (x64)", " (x86)", " x64", " x86", ".exe"
    };
    for (const char* j : junk) {
        if (n.endsWith(QString::fromLatin1(j)))
            n.chop(int(strlen(j)));
    }
    // collapse spaces
    n.replace(QStringLiteral("  "), QStringLiteral(" "));
    return n.trimmed();
}

QString aumidFromLaunch(const QString& launch) {
    if (!InstalledApps::isShellAppsPath(launch)) return {};
    const QString prefix = QStringLiteral("shell:appsfolder\\");
    QString rest = launch;
    if (rest.startsWith(prefix, Qt::CaseInsensitive))
        rest = rest.mid(prefix.size());
    return rest;
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

// Prefer Start Menu .lnk > real .exe > shell:AUMID; bonus if target exe exists.
int preferScore(const InstalledApp& a) {
    int s = 0;
    if (endsWithI(a.launchPath, QStringLiteral(".lnk"))) s += 40;
    else if (endsWithI(a.launchPath, QStringLiteral(".exe"))) s += 25;
    else if (InstalledApps::isShellAppsPath(a.launchPath)) s += 8;
    if (!a.target.isEmpty() && endsWithI(a.target, QStringLiteral(".exe")) && QFileInfo::exists(a.target))
        s += 15;
    else if (endsWithI(a.launchPath, QStringLiteral(".exe")) && QFileInfo::exists(a.launchPath))
        s += 10;
    // Prefer Program Files / Start Menu style paths over Softs duplicates of same name
    const QString p = lower(!a.target.isEmpty() ? a.target : a.launchPath);
    if (p.contains(QStringLiteral("\\program files"))) s += 5;
    if (p.contains(QStringLiteral("\\start menu\\"))) s += 5;
    return s;
}

void upsertApp(QVector<InstalledApp>& out, QHash<QString, int>& byName,
               QSet<QString>& pathKeys, const InstalledApp& cand) {
    if (cand.name.isEmpty() || cand.launchPath.isEmpty()) return;
    if (skipInstalled(cand.name, cand.target.isEmpty() ? cand.launchPath : cand.target)) return;

    // Path / AUMID hard keys
    QStringList hard;
    hard << lower(cand.launchPath);
    if (!cand.target.isEmpty()) hard << lower(cand.target);
    if (!cand.aumid.isEmpty()) hard << QStringLiteral("aumid:") + lower(cand.aumid);
    else {
        const QString aid = aumidFromLaunch(cand.launchPath);
        if (!aid.isEmpty()) hard << QStringLiteral("aumid:") + lower(aid);
    }
    for (const QString& k : hard) {
        if (pathKeys.contains(k)) {
            // Same path/AUMID already present — maybe upgrade if better score
            for (int i = 0; i < out.size(); ++i) {
                const InstalledApp& cur = out[i];
                QStringList curKeys;
                curKeys << lower(cur.launchPath);
                if (!cur.target.isEmpty()) curKeys << lower(cur.target);
                QString ca = cur.aumid.isEmpty() ? aumidFromLaunch(cur.launchPath) : cur.aumid;
                if (!ca.isEmpty()) curKeys << QStringLiteral("aumid:") + lower(ca);
                bool hit = false;
                for (const QString& ck : curKeys) if (hard.contains(ck)) { hit = true; break; }
                if (!hit) continue;
                if (preferScore(cand) > preferScore(cur)) {
                    // replace path keys
                    for (const QString& ck : curKeys) pathKeys.remove(ck);
                    out[i] = cand;
                    for (const QString& hk : hard) pathKeys.insert(hk);
                    byName.insert(normName(cand.name), i);
                }
                return;
            }
            return;
        }
    }

    const QString nk = normName(cand.name);
    if (byName.contains(nk)) {
        const int idx = byName.value(nk);
        if (idx >= 0 && idx < out.size()) {
            if (preferScore(cand) > preferScore(out[idx])) {
                // drop old hard keys
                InstalledApp old = out[idx];
                pathKeys.remove(lower(old.launchPath));
                if (!old.target.isEmpty()) pathKeys.remove(lower(old.target));
                QString oa = old.aumid.isEmpty() ? aumidFromLaunch(old.launchPath) : old.aumid;
                if (!oa.isEmpty()) pathKeys.remove(QStringLiteral("aumid:") + lower(oa));
                out[idx] = cand;
                for (const QString& hk : hard) pathKeys.insert(hk);
            }
            return;
        }
    }

    const int idx = out.size();
    out.push_back(cand);
    byName.insert(nk, idx);
    for (const QString& hk : hard) pathKeys.insert(hk);
}

void addInstalled(QVector<InstalledApp>& out, QHash<QString, int>& byName, QSet<QString>& pathKeys,
                  const QString& name, const QString& launch, const QString& target,
                  const QString& aumid = {}) {
    InstalledApp a;
    a.name = name.trimmed();
    a.launchPath = InstalledApps::isShellAppsPath(launch) ? launch : QDir::toNativeSeparators(launch);
    a.target = target.isEmpty() ? QString()
        : (InstalledApps::isShellAppsPath(target) ? target : QDir::toNativeSeparators(target));
    a.aumid = aumid;
    if (a.aumid.isEmpty()) a.aumid = aumidFromLaunch(a.launchPath);
    a.isSystem = InstalledApps::isSystemApp(a.name, a.launchPath, a.target);
    upsertApp(out, byName, pathKeys, a);
}

#ifdef _WIN32
static const PROPERTYKEY kPKeyAumid = {
    {0x9f4c2855, 0x9f79, 0x4B39, {0xa8, 0xd0, 0xe1, 0xd4, 0x2d, 0xe1, 0xd5, 0xf3}}, 5};
static const PROPERTYKEY kPKeyLinkTarget = {
    {0xB9B4B3FC, 0x2B51, 0x4A42, {0xBE, 0x5D, 0x46, 0x9B, 0x10, 0x67, 0x59, 0x3A}}, 2};
static const GUID kFolderIdAppsFolder =
    {0x1e87508d, 0x89c2, 0x42f0, {0x8a, 0x7e, 0x64, 0x5a, 0x0f, 0x50, 0xca, 0x58}};

static QString propToString(const PROPVARIANT& pv) {
    if (pv.vt == VT_LPWSTR && pv.pwszVal) return QString::fromWCharArray(pv.pwszVal);
    if (pv.vt == VT_BSTR && pv.bstrVal) return QString::fromWCharArray(pv.bstrVal);
    wchar_t buf[1024] = {};
    if (SUCCEEDED(PropVariantToString(pv, buf, 1024)) && buf[0])
        return QString::fromWCharArray(buf);
    return {};
}

void scanDirLnks(const QString& dir, QVector<InstalledApp>& out,
                 QHash<QString, int>& byName, QSet<QString>& pathKeys) {
    QDirIterator it(dir, QStringList() << QStringLiteral("*.lnk"),
                    QDir::Files, QDirIterator::Subdirectories);
    while (it.hasNext()) {
        const QString full = it.next();
        const QString target = InstalledApps::resolveLnk(full);
        addInstalled(out, byName, pathKeys, fileTitle(full), full, target);
    }
}

void scanUninstallKey(HKEY root, const wchar_t* sub, QVector<InstalledApp>& out,
                      QHash<QString, int>& byName, QSet<QString>& pathKeys) {
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
        if (exe.isEmpty()) {
            wchar_t loc[MAX_PATH] = {};
            sz = sizeof(loc);
            if (RegQueryValueExW(a, L"InstallLocation", nullptr, &type, (LPBYTE)loc, &sz) == ERROR_SUCCESS && loc[0]) {
                const QString dir = QString::fromWCharArray(loc).remove(QLatin1Char('"'));
                QDirIterator it(dir, QStringList() << QStringLiteral("*.exe"), QDir::Files);
                while (it.hasNext()) {
                    const QString p = it.next();
                    const QString bn = QFileInfo(p).fileName().toLower();
                    if (bn.contains(QStringLiteral("uninstall")) || bn.contains(QStringLiteral("update"))
                        || bn.contains(QStringLiteral("setup")) || bn.contains(QStringLiteral("helper")))
                        continue;
                    exe = p;
                    break;
                }
            }
        }
        if (exe.isEmpty()) {
            RegCloseKey(a);
            continue;
        }
        addInstalled(out, byName, pathKeys, QString::fromWCharArray(display), exe, exe);
        RegCloseKey(a);
    }
    RegCloseKey(k);
}

void scanAppsFolder(QVector<InstalledApp>& out, QHash<QString, int>& byName, QSet<QString>& pathKeys) {
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
            addInstalled(out, byName, pathKeys, name, targetExe, targetExe, aumid);
        } else if (!aumid.isEmpty()) {
            const QString launch = QStringLiteral("shell:AppsFolder\\") + aumid;
            addInstalled(out, byName, pathKeys, name, launch, QString(), aumid);
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

static QPixmap pixmapFromHICON(HICON ico) {
    if (!ico) return {};
    ICONINFO ii{};
    if (!GetIconInfo(ico, &ii)) return {};
    QPixmap pm = pixmapFromHBITMAP(ii.hbmColor ? ii.hbmColor : ii.hbmMask);
    if (ii.hbmColor) DeleteObject(ii.hbmColor);
    if (ii.hbmMask) DeleteObject(ii.hbmMask);
    return pm;
}

static QIcon iconFromLargePixmap(const QPixmap& large) {
    if (large.isNull()) return {};
    QIcon ic;
    const int src = qMax(large.width(), large.height());
    // Logical UI sizes used by left list (32) and right grid (48), plus DPR variants.
    const int logicalSizes[] = {16, 20, 24, 28, 32, 40, 48, 64};
    const qreal dprs[] = {1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0};
    for (int s : logicalSizes) {
        for (qreal dpr : dprs) {
            const int px = qMax(1, int(qRound(s * dpr)));
            // Never upscale a tiny source into a huge glyph (keeps muddy 16px from looking "HQ").
            if (src > 0 && src < 48 && px > src * 2) continue;
            QPixmap p = large.scaled(px, px, Qt::KeepAspectRatio, Qt::SmoothTransformation);
            if (p.isNull()) continue;
            p.setDevicePixelRatio(dpr);
            ic.addPixmap(p);
        }
    }
    // Also keep a native-resolution entry for IconMode / style pickers.
    if (src >= 32) {
        QPixmap native = large;
        if (native.width() != native.height()) {
            const int side = qMin(native.width(), native.height());
            native = native.copy((native.width() - side) / 2, (native.height() - side) / 2, side, side);
        }
        ic.addPixmap(native);
    }
    return ic;
}

static QPixmap extractExeIconHQ(const QString& exePath) {
    if (exePath.isEmpty() || !QFileInfo::exists(exePath)) return {};
    const std::wstring w = QDir::toNativeSeparators(exePath).toStdWString();
    HICON ico = nullptr;
    // Prefer jumbo / 256 then 128 then 48
    UINT n = PrivateExtractIconsW(w.c_str(), 0, 256, 256, &ico, nullptr, 1, LR_DEFAULTCOLOR);
    if (!(n && ico)) {
        ico = nullptr;
        n = PrivateExtractIconsW(w.c_str(), 0, 128, 128, &ico, nullptr, 1, LR_DEFAULTCOLOR);
    }
    if (!(n && ico)) {
        ico = nullptr;
        n = PrivateExtractIconsW(w.c_str(), 0, 48, 48, &ico, nullptr, 1, LR_DEFAULTCOLOR);
    }
    if (n && ico) {
        QPixmap pm = pixmapFromHICON(ico);
        DestroyIcon(ico);
        if (!pm.isNull() && pm.width() >= 32) return pm;
    }
    return {};
}

static QPixmap extractShellIconHQ(const QString& parsing) {
    if (parsing.isEmpty()) return {};
    IShellItem* item = nullptr;
    const std::wstring w = parsing.toStdWString();
    if (FAILED(SHCreateItemFromParsingName(w.c_str(), nullptr, IID_IShellItem, (void**)&item)) || !item)
        return {};
    IShellItemImageFactory* factory = nullptr;
    QPixmap out;
    if (SUCCEEDED(item->QueryInterface(IID_IShellItemImageFactory, (void**)&factory)) && factory) {
        // Request large; SIIGBF_BIGGERSIZEOK allows larger than asked when available
        SIZE sz{256, 256};
        HBITMAP hbm = nullptr;
        HRESULT hr = factory->GetImage(sz, SIIGBF_RESIZETOFIT | SIIGBF_BIGGERSIZEOK, &hbm);
        if (FAILED(hr) || !hbm) {
            sz = SIZE{128, 128};
            hr = factory->GetImage(sz, SIIGBF_RESIZETOFIT | SIIGBF_BIGGERSIZEOK, &hbm);
        }
        if (SUCCEEDED(hr) && hbm) {
            out = pixmapFromHBITMAP(hbm);
            DeleteObject(hbm);
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
    QPixmap pm(64, 64);
    pm.fill(Qt::transparent);
    QPainter p(&pm);
    p.setRenderHint(QPainter::Antialiasing);
    p.setBrush(QColor(0xD1, 0xD5, 0xDB));
    p.setPen(QColor(0x9C, 0xA3, 0xAF));
    p.drawRoundedRect(QRectF(4, 4, 56, 56), 10, 10);
    p.setPen(QPen(QColor(0x6B, 0x72, 0x80), 2.0));
    p.drawLine(22, 32, 42, 32);
    p.drawLine(32, 22, 32, 42);
    p.end();
#ifdef _WIN32
    return iconFromLargePixmap(pm);
#else
    return QIcon(pm);
#endif
}

QIcon InstalledApps::iconFor(const QString& launchPath, const QString& target) {
#ifdef _WIN32
    if (isShellAppsPath(launchPath)) {
        QPixmap pm = extractShellIconHQ(launchPath);
        if (!pm.isNull()) return iconFromLargePixmap(pm);
        return placeholderIcon();
    }
    // Prefer resolved exe for crisp ICO resources
    const QString exe = (!target.isEmpty() && endsWithI(target, QStringLiteral(".exe")))
                           ? target
                           : (endsWithI(launchPath, QStringLiteral(".exe")) ? launchPath : QString());
    if (!exe.isEmpty()) {
        QPixmap pm = extractExeIconHQ(exe);
        if (!pm.isNull()) return iconFromLargePixmap(pm);
    }
    if (endsWithI(launchPath, QStringLiteral(".lnk"))) {
        // Try resolved target first (already tried), then shell item for .lnk
        QPixmap pm = extractShellIconHQ(launchPath);
        if (!pm.isNull()) return iconFromLargePixmap(pm);
        const QString resolved = resolveLnk(launchPath);
        if (!resolved.isEmpty()) {
            pm = extractExeIconHQ(resolved);
            if (!pm.isNull()) return iconFromLargePixmap(pm);
        }
    }
#else
    Q_UNUSED(launchPath);
    Q_UNUSED(target);
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
    QHash<QString, int> byName;
    QSet<QString> pathKeys;
#ifdef _WIN32
    // Order: Start Menu first (preferred .lnk), then uninstall, then AppsFolder fill gaps.
    wchar_t folder[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_PROGRAMS, nullptr, SHGFP_TYPE_CURRENT, folder)))
        scanDirLnks(QString::fromWCharArray(folder), out, byName, pathKeys);
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_COMMON_PROGRAMS, nullptr, SHGFP_TYPE_CURRENT, folder)))
        scanDirLnks(QString::fromWCharArray(folder), out, byName, pathKeys);
    scanUninstallKey(HKEY_CURRENT_USER,
                     L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, byName, pathKeys);
    scanUninstallKey(HKEY_LOCAL_MACHINE,
                     L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, byName, pathKeys);
    scanUninstallKey(HKEY_LOCAL_MACHINE,
                     L"Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, byName, pathKeys);
    scanAppsFolder(out, byName, pathKeys);
#endif
    std::sort(out.begin(), out.end(), [](const InstalledApp& a, const InstalledApp& b) {
        return QString::localeAwareCompare(a.name, b.name) < 0;
    });
    return out;
}

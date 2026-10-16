#define _CRT_SECURE_NO_WARNINGS
#define NOMINMAX
#include <windows.h>
#include <shlobj.h>
#include <shlwapi.h>
#include <tlhelp32.h>
#include <string>

#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "advapi32.lib")

static std::wstring Join(const std::wstring& a, const wchar_t* b) {
    if (a.empty()) return b;
    if (a.back() == L'\\' || a.back() == L'/') return a + b;
    return a + L"\\" + b;
}

static std::wstring LocalAppData() {
    wchar_t buf[MAX_PATH] = {};
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_LOCAL_APPDATA, nullptr, SHGFP_TYPE_CURRENT, buf)))
        return buf;
    GetEnvironmentVariableW(L"LOCALAPPDATA", buf, MAX_PATH);
    return buf;
}

static std::wstring RoamingAppData() {
    wchar_t buf[MAX_PATH] = {};
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_APPDATA, nullptr, SHGFP_TYPE_CURRENT, buf)))
        return buf;
    GetEnvironmentVariableW(L"APPDATA", buf, MAX_PATH);
    return buf;
}

static bool WriteAll(const std::wstring& path, const void* data, DWORD size) {
    HANDLE f = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (f == INVALID_HANDLE_VALUE) return false;
    DWORD w = 0;
    BOOL ok = WriteFile(f, data, size, &w, nullptr);
    CloseHandle(f);
    return ok && w == size;
}

static bool ExtractResourceToFile(int id, const std::wstring& path) {
    HRSRC rs = FindResourceW(nullptr, MAKEINTRESOURCEW(id), RT_RCDATA);
    if (!rs) return false;
    HGLOBAL g = LoadResource(nullptr, rs);
    if (!g) return false;
    DWORD n = SizeofResource(nullptr, rs);
    void* p = LockResource(g);
    if (!p || !n) return false;
    return WriteAll(path, p, n);
}

static bool RunHidden(const std::wstring& cmd) {
    STARTUPINFOW si{ sizeof(si) };
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi{};
    std::wstring mutableCmd = cmd;
    if (!CreateProcessW(nullptr, &mutableCmd[0], nullptr, nullptr, FALSE,
                        CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi))
        return false;
    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 1;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return code == 0;
}

static bool ExtractPayload(const std::wstring& destDir) {
    wchar_t tmpDir[MAX_PATH] = {};
    GetTempPathW(MAX_PATH, tmpDir);
    std::wstring zip = Join(tmpDir, L"cuw-setup-payload.zip");
    DeleteFileW(zip.c_str());
    if (!ExtractResourceToFile(100, zip)) return false;
    CreateDirectoryW(destDir.c_str(), nullptr);
    std::wstring cmd = L"tar.exe -xf \"" + zip + L"\" -C \"" + destDir + L"\"";
    bool ok = RunHidden(cmd);
    if (!ok) {
        cmd = L"powershell.exe -NoProfile -ExecutionPolicy Bypass -Command \"Expand-Archive -LiteralPath '"
            + zip + L"' -DestinationPath '" + destDir + L"' -Force\"";
        ok = RunHidden(cmd);
    }
    DeleteFileW(zip.c_str());
    if (!ok) return false;
    return GetFileAttributesW(Join(destDir, L"CursorUsage.exe").c_str()) != INVALID_FILE_ATTRIBUTES;
}

static bool PickFolder(HWND owner, const std::wstring& initial, std::wstring& out) {
    IFileOpenDialog* dlg = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER,
                                  IID_IFileOpenDialog, (void**)&dlg);
    if (FAILED(hr) || !dlg) return false;
    DWORD opt = 0;
    dlg->GetOptions(&opt);
    dlg->SetOptions(opt | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
    dlg->SetTitle(L"选择安装位置");
    dlg->SetOkButtonLabel(L"安装到这里");
    if (!initial.empty()) {
        IShellItem* folder = nullptr;
        if (SUCCEEDED(SHCreateItemFromParsingName(initial.c_str(), nullptr, IID_IShellItem, (void**)&folder)) && folder) {
            dlg->SetDefaultFolder(folder);
            dlg->SetFolder(folder);
            folder->Release();
        }
    }
    hr = dlg->Show(owner);
    bool ok = false;
    if (SUCCEEDED(hr)) {
        IShellItem* item = nullptr;
        if (SUCCEEDED(dlg->GetResult(&item)) && item) {
            PWSTR path = nullptr;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path)) && path) {
                out = path;
                CoTaskMemFree(path);
                ok = true;
            }
            item->Release();
        }
    }
    dlg->Release();
    return ok;
}

static void EnableAutoStart(const std::wstring& exe) {
    HKEY k = nullptr;
    if (RegCreateKeyExW(HKEY_CURRENT_USER,
            L"Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            0, nullptr, 0, KEY_SET_VALUE, nullptr, &k, nullptr) != ERROR_SUCCESS)
        return;
    std::wstring quoted = L"\"" + exe + L"\"";
    RegSetValueExW(k, L"CursorUsageWidget", 0, REG_SZ,
                   (const BYTE*)quoted.c_str(), (DWORD)((quoted.size() + 1) * sizeof(wchar_t)));
    RegCloseKey(k);
    HKEY a = nullptr;
    if (RegOpenKeyExW(HKEY_CURRENT_USER,
            L"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run",
            0, KEY_SET_VALUE, &a) == ERROR_SUCCESS) {
        BYTE approved[] = { 2,0,0,0, 0,0,0,0, 0,0,0,0 };
        RegSetValueExW(a, L"CursorUsageWidget", 0, REG_BINARY, approved, sizeof(approved));
        RegCloseKey(a);
    }
}

static void DisableAutoStart() {
    RegDeleteKeyValueW(HKEY_CURRENT_USER,
        L"Software\\Microsoft\\Windows\\CurrentVersion\\Run", L"CursorUsageWidget");
    RegDeleteKeyValueW(HKEY_CURRENT_USER,
        L"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run", L"CursorUsageWidget");
}

static void KillByName(const wchar_t* exeName) {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return;
    PROCESSENTRY32W pe{ sizeof(pe) };
    if (Process32FirstW(snap, &pe)) {
        do {
            if (_wcsicmp(pe.szExeFile, exeName) == 0) {
                HANDLE h = OpenProcess(PROCESS_TERMINATE, FALSE, pe.th32ProcessID);
                if (h) { TerminateProcess(h, 0); CloseHandle(h); }
            }
        } while (Process32NextW(snap, &pe));
    }
    CloseHandle(snap);
}

static void StopApp() {
    HWND w = FindWindowW(L"CursorUsageWidget", nullptr);
    if (w) PostMessageW(w, WM_CLOSE, 0, 0);
    KillByName(L"CursorUsage.exe");
    KillByName(L"CursorUsageSettings.exe");
}

static void RemoveTree(const std::wstring& path) {
    std::wstring from = path;
    if (!from.empty() && from.back() != L'\\') from.push_back(L'\\');
    from.push_back(L'\0');
    SHFILEOPSTRUCTW op{};
    op.wFunc = FO_DELETE;
    op.pFrom = from.c_str();
    op.fFlags = FOF_NOCONFIRMATION | FOF_NOERRORUI | FOF_SILENT;
    SHFileOperationW(&op);
}

static void WriteUninstallBat(const std::wstring& dest, const std::wstring& setupPath) {
    std::wstring bat = dest + L"\\卸载.bat";
    std::string body =
        "@echo off\r\n"
        "chcp 65001 >nul\r\n"
        "if exist \"%~dp0CursorUsage-Setup.exe\" (\r\n"
        "  start \"\" \"%~dp0CursorUsage-Setup.exe\" /uninstall\r\n"
        "  exit /b 0\r\n"
        ")\r\n"
        "taskkill /IM CursorUsageSettings.exe /F >nul 2>&1\r\n"
        "taskkill /IM CursorUsage.exe /F >nul 2>&1\r\n"
        "reg delete \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v CursorUsageWidget /f >nul 2>&1\r\n"
        "rd /s /q \"%~dp0qt-runtime\" >nul 2>&1\r\n"
        "del /f /q \"%~dp0CursorUsage.exe\" >nul 2>&1\r\n"
        "del /f /q \"%~dp0卸载.bat\" >nul 2>&1\r\n";
    WriteAll(bat, body.data(), (DWORD)body.size());
    CopyFileW(setupPath.c_str(), Join(dest, L"CursorUsage-Setup.exe").c_str(), FALSE);
}

static std::wstring ReadInstallPath() {
    std::wstring p = Join(RoamingAppData(), L"cursor-usage-widget\\install.path");
    HANDLE f = CreateFileW(p.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, 0, nullptr);
    if (f == INVALID_HANDLE_VALUE) return {};
    char buf[1024] = {};
    DWORD n = 0;
    ReadFile(f, buf, sizeof(buf) - 1, &n, nullptr);
    CloseHandle(f);
    if (!n) return {};
    int wlen = MultiByteToWideChar(CP_UTF8, 0, buf, (int)n, nullptr, 0);
    std::wstring out(wlen, 0);
    MultiByteToWideChar(CP_UTF8, 0, buf, (int)n, &out[0], wlen);
    while (!out.empty() && (out.back() == L'\r' || out.back() == L'\n' || out.back() == L' '))
        out.pop_back();
    return out;
}

static void SaveInstallPath(const std::wstring& dest) {
    std::wstring dir = Join(RoamingAppData(), L"cursor-usage-widget");
    CreateDirectoryW(dir.c_str(), nullptr);
    std::wstring path = Join(dir, L"install.path");
    int n = WideCharToMultiByte(CP_UTF8, 0, dest.c_str(), -1, nullptr, 0, nullptr, nullptr);
    std::string u8(n > 0 ? n - 1 : 0, 0);
    if (n > 1) WideCharToMultiByte(CP_UTF8, 0, dest.c_str(), -1, &u8[0], n, nullptr, nullptr);
    WriteAll(path, u8.data(), (DWORD)u8.size());
}

static int DoUninstall() {
    StopApp();
    Sleep(400);
    std::wstring dest = ReadInstallPath();
    if (dest.empty()) dest = Join(LocalAppData(), L"CursorUsage");
    DisableAutoStart();
    RemoveTree(Join(dest, L"qt-runtime"));
    DeleteFileW(Join(dest, L"CursorUsage.exe").c_str());
    DeleteFileW(Join(dest, L"卸载.bat").c_str());
    std::wstring self = Join(dest, L"CursorUsage-Setup.exe");
    MoveFileExW(self.c_str(), nullptr, MOVEFILE_DELAY_UNTIL_REBOOT);
    DeleteFileW(self.c_str());
    DeleteFileW(Join(RoamingAppData(), L"cursor-usage-widget\\install.path").c_str());
    MessageBoxW(nullptr,
                L"已卸载程序文件。\n配置仍保留在 %APPDATA%\\cursor-usage-widget\\（ui.ini / shortcuts.txt）。",
                L"卸载完成", MB_OK | MB_ICONINFORMATION);
    return 0;
}

static int DoInstall() {
    std::wstring initial = LocalAppData();
    std::wstring prev = ReadInstallPath();
    if (!prev.empty()) {
        wchar_t parent[MAX_PATH];
        lstrcpynW(parent, prev.c_str(), MAX_PATH);
        PathRemoveFileSpecW(parent);
        if (parent[0]) initial = parent;
    }
    std::wstring picked;
    if (!PickFolder(nullptr, initial, picked)) return 1;
    while (!picked.empty() && (picked.back() == L'\\' || picked.back() == L'/'))
        picked.pop_back();
    std::wstring dest = picked;
    if (_wcsicmp(PathFindFileNameW(picked.c_str()), L"CursorUsage") != 0)
        dest = Join(picked, L"CursorUsage");
    CreateDirectoryW(dest.c_str(), nullptr);
    StopApp();
    Sleep(300);
    if (!ExtractPayload(dest)) {
        MessageBoxW(nullptr,
                    L"解压安装包失败。请换一个可写目录，或确认系统自带 tar/PowerShell 可用。",
                    L"安装失败", MB_OK | MB_ICONERROR);
        return 1;
    }
    wchar_t me[MAX_PATH] = {};
    GetModuleFileNameW(nullptr, me, MAX_PATH);
    WriteUninstallBat(dest, me);
    SaveInstallPath(dest);
    std::wstring exe = Join(dest, L"CursorUsage.exe");
    EnableAutoStart(exe);
    ShellExecuteW(nullptr, L"open", exe.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
    std::wstring done = L"已安装到：\n" + dest +
        L"\n\n已写入开机启动。请先打开并登录 Cursor。\n"
        L"设置窗口：托盘菜单打开，或运行 qt-runtime\\CursorUsageSettings.exe\n"
        L"卸载：安装目录里的「卸载.bat」。";
    MessageBoxW(nullptr, done.c_str(), L"安装完成", MB_OK | MB_ICONINFORMATION);
    return 0;
}

int WINAPI wWinMain(HINSTANCE, HINSTANCE, LPWSTR cmd, int) {
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    int code = 0;
    if (cmd && wcsstr(cmd, L"/uninstall"))
        code = DoUninstall();
    else
        code = DoInstall();
    CoUninitialize();
    return code;
}

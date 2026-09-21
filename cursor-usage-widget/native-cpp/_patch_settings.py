# -*- coding: utf-8 -*-
"""Insert OpenSettings custom window and slim RC menu."""
from pathlib import Path

p = Path(r"D:\GitFiles\leon-tools\cursor-usage-widget\native-cpp\CursorUsage.cpp")
text = p.read_text(encoding="utf-8")
nl = "\r\n" if "\r\n" in text else "\n"
c = text.replace("\r\n", "\n")

if "OpenSettings()" in c and "SettingsProc" in c:
    print("ALREADY_HAS_SETTINGS")
    raise SystemExit(0)

SETTINGS_BLOCK = r'''
// ---- Settings window (美工: 320 / #F8F8FA / r12 / #D8DCE1) ----
static const int IDM_SETTINGS = 18;
static const int kSettingsW = 320;

struct SettingsDlg {
    HWND hwnd = nullptr;
    RECT dockBtn[3]{};
    RECT alphaBtn[4]{};
    RECT botRow{};
    RECT addBtn{};
    RECT closeBtn{};
    RECT emptyHit{};
    bool botHot = false;
} g_settings;

static int SettingsContentH() {
    // pad16 + title22 + gaps + 4 sections + close
    int listH = (int)g_shortcuts.size() * 22;
    if (listH < 28) listH = 28;
    if (listH > 120) listH = 120;
    return 16 + 24 + 16
        + 20 + 8 + 28 + 16   // dock
        + 20 + 8 + 28 + 16   // alpha
        + 20 + 8 + 28 + 16   // bot
        + 20 + 8 + listH + 8 + 28 + 16  // shortcuts + add
        + 32 + 16;           // close + pad
}

static void SettingsLayout(int /*cw*/, int /*ch*/) {
    const int pad = 16;
    const int inner = kSettingsW - pad * 2;
    int y = pad + 24 + 16;
    // dock buttons
    int bw = (inner - 8 * 2) / 3;
    for (int i = 0; i < 3; ++i) {
        g_settings.dockBtn[i] = { pad + i * (bw + 8), y + 20 + 8, pad + i * (bw + 8) + bw, y + 20 + 8 + 28 };
    }
    y += 20 + 8 + 28 + 16;
    int aw = (inner - 6 * 3) / 4;
    for (int i = 0; i < 4; ++i) {
        g_settings.alphaBtn[i] = { pad + i * (aw + 6), y + 20 + 8, pad + i * (aw + 6) + aw, y + 20 + 8 + 28 };
    }
    y += 20 + 8 + 28 + 16;
    g_settings.botRow = { pad, y + 20 + 8, pad + inner, y + 20 + 8 + 28 };
    y += 20 + 8 + 28 + 16;
    int listH = (int)g_shortcuts.size() * 22;
    if (listH < 28) listH = 28;
    if (listH > 120) listH = 120;
    g_settings.emptyHit = { pad, y + 20 + 8, pad + inner, y + 20 + 8 + listH };
    g_settings.addBtn = { pad, y + 20 + 8 + listH + 8, pad + 90, y + 20 + 8 + listH + 8 + 28 };
    y += 20 + 8 + listH + 8 + 28 + 16;
    g_settings.closeBtn = { pad, y, pad + inner, y + 32 };
}

static void RoundRectPath(GraphicsPath& path, float x, float y, float w, float h, float r) {
    path.AddArc(x, y, r * 2, r * 2, 180, 90);
    path.AddArc(x + w - r * 2, y, r * 2, r * 2, 270, 90);
    path.AddArc(x + w - r * 2, y + h - r * 2, r * 2, r * 2, 0, 90);
    path.AddArc(x, y + h - r * 2, r * 2, r * 2, 90, 90);
    path.CloseFigure();
}

static void DrawChip(Graphics& gph, const RECT& rc, const wchar_t* label, bool on, Font& f) {
    float x = (float)rc.left, y = (float)rc.top, w = (float)(rc.right - rc.left), h = (float)(rc.bottom - rc.top);
    GraphicsPath path;
    RoundRectPath(path, x, y, w, h, 6.f);
    SolidBrush fill(on ? Color(255, 0xEE, 0xF2, 0xF7) : Color(255, 0xFF, 0xFF, 0xFF));
    gph.FillPath(&fill, &path);
    Pen border(on ? Color(255, 0xA8, 0xB2, 0xC0) : Color(255, 0xD8, 0xDC, 0xE1), 1.f);
    gph.DrawPath(&border, &path);
    SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
    StringFormat fmt;
    fmt.SetAlignment(StringAlignmentCenter);
    fmt.SetLineAlignment(StringAlignmentCenter);
    gph.DrawString(label, -1, &f, RectF(x, y, w, h), &fmt, &ink);
}

static void PaintSettings(HWND h) {
    RECT crc; GetClientRect(h, &crc);
    int cw = crc.right, ch = crc.bottom;
    SettingsLayout(cw, ch);
    PAINTSTRUCT ps;
    HDC hdc = BeginPaint(h, &ps);
    HDC mem = CreateCompatibleDC(hdc);
    BITMAPINFO bmi{};
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = cw;
    bmi.bmiHeader.biHeight = -ch;
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 32;
    bmi.bmiHeader.biCompression = BI_RGB;
    void* bits = nullptr;
    HBITMAP bmp = CreateDIBSection(nullptr, &bmi, DIB_RGB_COLORS, &bits, nullptr, 0);
    HGDIOBJ old = SelectObject(mem, bmp);
    {
        Graphics gph(mem);
        gph.SetSmoothingMode(SmoothingModeAntiAlias);
        gph.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
        // panel
        SolidBrush bg(Color(255, 0xF8, 0xF8, 0xFA));
        gph.FillRectangle(&bg, 0, 0, cw, ch);
        GraphicsPath frame;
        RoundRectPath(frame, 0.5f, 0.5f, (float)cw - 1.f, (float)ch - 1.f, 12.f);
        Pen stroke(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
        gph.DrawPath(&stroke, &frame);

        FontFamily yahei(L"Microsoft YaHei UI");
        const FontFamily* fam = (yahei.GetLastStatus() == Gdiplus::Ok) ? &yahei : FontFamily::GenericSansSerif();
        Font title(fam, 16.f, Gdiplus::FontStyleBold, Gdiplus::UnitPixel);
        Font sec(fam, 12.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        Font ui(fam, 12.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        SolidBrush titleBr(Color(255, 0x20, 0x20, 0x22));
        SolidBrush secBr(Color(255, 0x5A, 0x60, 0x69));
        SolidBrush muted(Color(255, 0x78, 0x7D, 0x85));

        const int pad = 16;
        gph.DrawString(L"\u8bbe\u7f6e", -1, &title, PointF((float)pad, (float)pad), &titleBr);

        int y = pad + 24 + 16;
        gph.DrawString(L"\u8d34\u8fb9", -1, &sec, PointF((float)pad, (float)y), &secBr);
        const wchar_t* docks[] = { L"\u5de6", L"\u53f3", L"\u9876" };
        for (int i = 0; i < 3; ++i)
            DrawChip(gph, g_settings.dockBtn[i], docks[i], g.dockEdge == i, ui);

        y = g_settings.alphaBtn[0].top - 20 - 8;
        gph.DrawString(L"\u80cc\u666f\u900f\u660e\u5ea6", -1, &sec, PointF((float)pad, (float)y), &secBr);
        const int alphas[] = { 100, 85, 70, 55 };
        wchar_t alab[8];
        for (int i = 0; i < 4; ++i) {
            swprintf(alab, 8, L"%d%%", alphas[i]);
            DrawChip(gph, g_settings.alphaBtn[i], alab, g.bgAlpha == alphas[i], ui);
        }

        y = g_settings.botRow.top - 20 - 8;
        gph.DrawString(L"Bot", -1, &sec, PointF((float)pad, (float)y), &secBr);
        // toggle
        {
            RECT rc = g_settings.botRow;
            float tx = (float)rc.left, ty = (float)rc.top + 4, tw = 44, th = 22;
            GraphicsPath tpath;
            RoundRectPath(tpath, tx, ty, tw, th, th / 2);
            SolidBrush tfill(g.showBot ? Color(255, 0x3B, 0x82, 0xF6) : Color(255, 0xD0, 0xD4, 0xDA));
            gph.FillPath(&tfill, &tpath);
            float knob = th - 4;
            float kx = g.showBot ? (tx + tw - knob - 2) : (tx + 2);
            SolidBrush knobBr(Color(255, 255, 255, 255));
            gph.FillEllipse(&knobBr, kx, ty + 2, knob, knob);
            gph.DrawString(L"\u663e\u793a Bot \u7528\u91cf", -1, &ui, PointF(tx + tw + 10, ty), &titleBr);
        }

        y = g_settings.emptyHit.top - 20 - 8;
        gph.DrawString(L"\u5feb\u6377\u65b9\u5f0f", -1, &sec, PointF((float)pad, (float)y), &secBr);
        if (g_shortcuts.empty()) {
            StringFormat fmt;
            fmt.SetAlignment(StringAlignmentCenter);
            fmt.SetLineAlignment(StringAlignmentCenter);
            RECT er = g_settings.emptyHit;
            gph.DrawString(L"\u6dfb\u52a0\u5e38\u7528\u8f6f\u4ef6", -1, &ui,
                RectF((float)er.left, (float)er.top, (float)(er.right - er.left), (float)(er.bottom - er.top)), &fmt, &muted);
        } else {
            int ly = g_settings.emptyHit.top;
            for (size_t i = 0; i < g_shortcuts.size() && i < 9; ++i) {
                std::wstring name = g_shortcuts[i].name;
                if (name.empty()) {
                    const wchar_t* n = g_shortcuts[i].path.c_str();
                    for (const wchar_t* q = n; *q; ++q)
                        if (*q == L'\\' || *q == L'/') n = q + 1;
                    name = n;
                }
                gph.DrawString(name.c_str(), -1, &ui, PointF((float)pad, (float)ly), &titleBr);
                ly += 22;
            }
        }
        DrawChip(gph, g_settings.addBtn, L"\u6dfb\u52a0", false, ui);
        DrawChip(gph, g_settings.closeBtn, L"\u5173\u95ed", true, ui);
    }
    BitBlt(hdc, 0, 0, cw, ch, mem, 0, 0, SRCCOPY);
    SelectObject(mem, old);
    DeleteObject(bmp);
    DeleteDC(mem);
    EndPaint(h, &ps);
}

static bool PtIn(const RECT& r, int x, int y) {
    return x >= r.left && x < r.right && y >= r.top && y < r.bottom;
}

static void SettingsApplyMain() {
    if (!g.hwnd) return;
    Place(g.hwnd);
    Repaint(g.hwnd);
}

static LRESULT CALLBACK SettingsProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
    case WM_CREATE:
        g_settings.hwnd = h;
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT:
        PaintSettings(h);
        return 0;
    case WM_LBUTTONUP: {
        int x = GET_X_LPARAM(l), y = GET_Y_LPARAM(l);
        for (int i = 0; i < 3; ++i) if (PtIn(g_settings.dockBtn[i], x, y)) {
            if (g.dockEdge != i) { g.dockEdge = i; g.y = -1; }
            SaveConfig(); SettingsApplyMain(); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        const int alphas[] = { 100, 85, 70, 55 };
        for (int i = 0; i < 4; ++i) if (PtIn(g_settings.alphaBtn[i], x, y)) {
            g.bgAlpha = alphas[i]; SaveConfig(); SettingsApplyMain(); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        if (PtIn(g_settings.botRow, x, y)) {
            g.showBot = !g.showBot; SaveConfig(); SettingsApplyMain(); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        if (PtIn(g_settings.addBtn, x, y) || (g_shortcuts.empty() && PtIn(g_settings.emptyHit, x, y))) {
            OpenManageShortcuts();
            return 0;
        }
        if (PtIn(g_settings.closeBtn, x, y)) { DestroyWindow(h); return 0; }
        return 0;
    }
    case WM_CLOSE:
        DestroyWindow(h);
        return 0;
    case WM_DESTROY:
        g_settings.hwnd = nullptr;
        return 0;
    }
    return DefWindowProcW(h, m, w, l);
}

static void OpenSettings() {
    if (g_settings.hwnd && IsWindow(g_settings.hwnd)) {
        ShowWindow(g_settings.hwnd, SW_SHOW);
        SetForegroundWindow(g_settings.hwnd);
        InvalidateRect(g_settings.hwnd, nullptr, FALSE);
        return;
    }
    g_settings.hwnd = nullptr;
    static ATOM atom = 0;
    if (!atom) {
        WNDCLASSEXW wc{ sizeof(wc) };
        wc.lpfnWndProc = SettingsProc;
        wc.hInstance = GetModuleHandleW(nullptr);
        wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
        wc.hbrBackground = nullptr;
        wc.lpszClassName = L"CursorUsageSettings";
        wc.style = CS_HREDRAW | CS_VREDRAW;
        atom = RegisterClassExW(&wc);
    }
    int hgt = SettingsContentH();
    RECT wa; SystemParametersInfo(SPI_GETWORKAREA, 0, &wa, 0);
    int x = wa.left + (wa.right - wa.left - kSettingsW) / 2;
    int y = wa.top + (wa.bottom - wa.top - hgt) / 3;
    HWND hw = CreateWindowExW(WS_EX_TOOLWINDOW | WS_EX_TOPMOST,
        L"CursorUsageSettings", L"\u8bbe\u7f6e",
        WS_POPUP | WS_CLIPCHILDREN,
        x, y, kSettingsW, hgt,
        g.hwnd, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!hw) return;
    // Soft shadow / region rounded via paint; show
    ShowWindow(hw, SW_SHOW);
    UpdateWindow(hw);
}

'''

# Insert before OpenManageShortcuts — but SettingsProc calls OpenManageShortcuts,
# so OpenSettings must be AFTER OpenManageShortcuts declaration, or forward declare.
# Forward declare OpenManageShortcuts if needed.

if "static void OpenManageShortcuts();" not in c:
    # add forward declare near other static decls after kMaxShortcuts
    needle = "static const int IDM_LAUNCH_BASE = 200;"
    if needle not in c:
        raise SystemExit("missing IDM_LAUNCH_BASE")
    c = c.replace(needle, needle + "\nstatic void OpenManageShortcuts();\nstatic void OpenSettings();\nstatic void Repaint(HWND h);", 1)
    # Repaint may already exist - check duplicate
    if c.count("static void Repaint(HWND h);") > 1:
        # remove the one we added if Repaint already declared
        pass

# Check if Repaint already exists as definition
if "static void Repaint(" in c:
    # remove duplicate forward if we added and definition exists - keep one forward is ok in C++
    # but double definition of forward is fine; double `static void Repaint(HWND h);` twice is fine
    while c.count("static void Repaint(HWND h);") > 1:
        # keep first only - naive
        first = c.find("static void Repaint(HWND h);")
        second = c.find("static void Repaint(HWND h);", first + 1)
        if second < 0: break
        c = c[:second] + c[second + len("static void Repaint(HWND h);"):]

# Insert settings block AFTER OpenManageShortcuts function ends (after its closing brace before DrawQuickLaunch)
marker = "static void DrawQuickLaunch(Graphics& gph, Font& ui, float x, float y, float qw, float qh) {"
idx = c.find(marker)
if idx < 0:
    raise SystemExit("DrawQuickLaunch marker missing")
c = c[:idx] + SETTINGS_BLOCK + "\n" + c[idx:]
print("inserted settings block")

# Slim RC menu: replace from CreatePopupMenu through TrackPopupMenu handlers for dock/opacity etc.
# Find WM_RBUTTONUP case menu building
import re
# Replace AppendMenu section - find unique start
start = c.find("        HMENU menu = CreatePopupMenu();\n        HMENU scale = CreatePopupMenu();")
if start < 0:
    start = c.find("HMENU menu = CreatePopupMenu();")
    # find line start
    start = c.rfind("\n", 0, start) + 1

end = c.find("        if (cmd == IDM_MANAGE_APPS) OpenManageShortcuts();")
if end < 0:
    raise SystemExit("IDM_MANAGE_APPS handler missing")
# include through launch shortcut handlers, replace whole block from menu create to before return 0
# Find the return 0 after launch handlers
end2 = c.find("        return 0;\n    }\n    case WM_DESTROY:", end)
if end2 < 0:
    raise SystemExit("menu end missing")

new_menu = '''        HMENU menu = CreatePopupMenu();
        AppendMenuW(menu, MF_STRING, 10, L"\\u7acb\\u5373\\u5237\\u65b0");
        AppendMenuW(menu, MF_STRING, 11, L"\\u6253\\u5f00\\u7528\\u91cf\\u9875");
        AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(menu, MF_STRING, IDM_SETTINGS, L"\\u8bbe\\u7f6e");
        AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(menu, MF_STRING, 14, L"\\u9000\\u51fa");
        int cmd = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON, pt.x, pt.y, 0, h, nullptr);
        DestroyMenu(menu);
        if (cmd == 10) Refresh();
        if (cmd == 11) ShellExecuteW(nullptr, L"open", L"https://cursor.com/dashboard", nullptr, nullptr, SW_SHOWNORMAL);
        if (cmd == IDM_SETTINGS) OpenSettings();
        if (cmd == 14) DestroyWindow(h);
'''

c = c[:start] + new_menu + c[end2:]
print("slimmed RC menu")

# Also fix OpenManageShortcuts reopen: ensure IsWindow check
old_om = """static void OpenManageShortcuts() {
    if (g_manage.hwnd) { SetForegroundWindow(g_manage.hwnd); return; }"""
new_om = """static void OpenManageShortcuts() {
    if (g_manage.hwnd && IsWindow(g_manage.hwnd)) { ShowWindow(g_manage.hwnd, SW_SHOW); SetForegroundWindow(g_manage.hwnd); return; }
    g_manage.hwnd = nullptr;"""
if old_om in c:
    c = c.replace(old_om, new_om, 1)
    print("fixed manage reopen")
else:
    print("WARN manage open pattern changed")

# Ensure IDM_SETTINGS doesn't clash - IDM_LAUNCH_BASE is 200, IDM_MANAGE 199, 18 is fine
# But we may have added IDM_SETTINGS in block and also need it visible before menu - it's in SETTINGS_BLOCK

out = c.replace("\n", nl)
p.write_text(out, encoding="utf-8", newline="")
print("DONE lines", out.count("\n"))

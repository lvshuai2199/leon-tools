from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")

# 1) App struct: track dark
old = """    bool showBot = false;
    int scrollY = 0;
    bool scrolling = false;
} g;
"""
new = """    bool showBot = false;
    int scrollY = 0;
    bool scrolling = false;
    bool dark = false;
} g;
"""
if old not in text:
    raise SystemExit("app struct missing")
text = text.replace(old, new, 1)

# 2) Theme helpers after S()
old = "static int S(int v) { return (int)(v * g.scale / 100.0 + 0.5); }\n"
new = """static int S(int v) { return (int)(v * g.scale / 100.0 + 0.5); }

// Follow Windows app theme. Icons: light #202022 / dark #E8E8EA. Ring hues stay.
static bool ReadAppsDark() {
    HKEY k = nullptr;
    if (RegOpenKeyExW(HKEY_CURRENT_USER,
            L"Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Themes\\\\Personalize",
            0, KEY_READ, &k) != ERROR_SUCCESS)
        return false;
    DWORD light = 1, sz = sizeof(light), type = 0;
    LONG r = RegQueryValueExW(k, L"AppsUseLightTheme", nullptr, &type, (LPBYTE)&light, &sz);
    RegCloseKey(k);
    return r == ERROR_SUCCESS && type == REG_DWORD && light == 0;
}

static void RefreshTheme(HWND h) {
    bool dark = ReadAppsDark();
    if (dark == g.dark && h) return;
    g.dark = dark;
    if (h) InvalidateRect(h, nullptr, FALSE);
}

static Color IconInk() {
    return g.dark ? Color(255, 0xE8, 0xE8, 0xEA) : Color(255, 0x20, 0x20, 0x22);
}
"""
if old not in text:
    raise SystemExit("S() missing")
text = text.replace(old, new, 1)

# 3) DrawTitleMark: use IconInk()
old = """static void DrawTitleMark(Graphics& gph, float x, float y) {
    // usage-badge.svg: hex cursor #202022, viewBox 0 0 49 56, transparent cutout
    SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
"""
new = """static void DrawTitleMark(Graphics& gph, float x, float y) {
    // usage-badge.svg hex cursor; mono fill follows light/dark
    SolidBrush ink(IconInk());
"""
if old not in text:
    raise SystemExit("title mark missing")
text = text.replace(old, new, 1)

# 4) DrawRingGlyph: use IconInk()
old = """    // Four ring icons: black #202022 hex hollow, same 16x16 frame (ui-review/icon-*.svg).
    // Ring stroke colors stay blue / purple / teal / amber.
    SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
"""
new = """    // Four ring icons: hex hollow, same 16x16 frame (ui-review/icon-*.svg).
    // Mono fill follows theme; ring stroke colors stay blue / purple / teal / amber.
    SolidBrush ink(IconInk());
"""
if old not in text:
    raise SystemExit("ring glyph ink missing")
text = text.replace(old, new, 1)

# 5) Paint panel + text colors
old = """    Color fill(255, 248, 248, 250);
    gph.Clear(Color(255, 255, 0, 255));
    GraphicsPath body;
    float rad = (float)S(12);
    AddBodyPath(body, (float)w, (float)hh, rad, 2.0f);
    LinearGradientBrush wash(PointF(0.f, 0.f), PointF(0.f, (float)hh),
                             Color(255, 255, 255, 255), Color(255, 238, 239, 242));
    gph.FillPath(&wash, &body);
    gph.SetCompositingMode(Gdiplus::CompositingModeSourceOver);
    Pen rim(Color(255, 0xD8, 0xDC, 0xE1), 1.0f);
    rim.SetAlignment(PenAlignmentInset);
    rim.SetLineJoin(Gdiplus::LineJoinRound);
    gph.DrawPath(&rim, &body);

    FontFamily yahei(L"Microsoft YaHei UI");
    FontFamily segoe(L"Segoe UI");
    const FontFamily* uiFam = (yahei.GetLastStatus() == Gdiplus::Ok) ? &yahei : FontFamily::GenericSansSerif();
    const FontFamily* numFam = (segoe.GetLastStatus() == Gdiplus::Ok) ? &segoe : FontFamily::GenericSansSerif();
    Font title(uiFam, 11, Gdiplus::FontStyleBold, Gdiplus::UnitPoint);
    Font ui(uiFam, 8.5f, Gdiplus::FontStyleRegular, Gdiplus::UnitPoint);
    Font sm(uiFam, 7.5f, Gdiplus::FontStyleRegular, Gdiplus::UnitPoint);
    Font num(numFam, 8.0f, Gdiplus::FontStyleBold, Gdiplus::UnitPoint);
    SolidBrush white(Color(255, 32, 32, 34));
    SolidBrush muted(Color(200, 90, 96, 105));
"""
new = """    gph.Clear(Color(255, 255, 0, 255));
    GraphicsPath body;
    float rad = (float)S(12);
    AddBodyPath(body, (float)w, (float)hh, rad, 2.0f);
    Color washTop = g.dark ? Color(255, 0x2A, 0x2A, 0x2E) : Color(255, 255, 255, 255);
    Color washBot = g.dark ? Color(255, 0x1E, 0x1E, 0x22) : Color(255, 238, 239, 242);
    LinearGradientBrush wash(PointF(0.f, 0.f), PointF(0.f, (float)hh), washTop, washBot);
    gph.FillPath(&wash, &body);
    gph.SetCompositingMode(Gdiplus::CompositingModeSourceOver);
    Pen rim(g.dark ? Color(255, 0x3A, 0x3A, 0x40) : Color(255, 0xD8, 0xDC, 0xE1), 1.0f);
    rim.SetAlignment(PenAlignmentInset);
    rim.SetLineJoin(Gdiplus::LineJoinRound);
    gph.DrawPath(&rim, &body);

    FontFamily yahei(L"Microsoft YaHei UI");
    FontFamily segoe(L"Segoe UI");
    const FontFamily* uiFam = (yahei.GetLastStatus() == Gdiplus::Ok) ? &yahei : FontFamily::GenericSansSerif();
    const FontFamily* numFam = (segoe.GetLastStatus() == Gdiplus::Ok) ? &segoe : FontFamily::GenericSansSerif();
    Font title(uiFam, 11, Gdiplus::FontStyleBold, Gdiplus::UnitPoint);
    Font ui(uiFam, 8.5f, Gdiplus::FontStyleRegular, Gdiplus::UnitPoint);
    Font sm(uiFam, 7.5f, Gdiplus::FontStyleRegular, Gdiplus::UnitPoint);
    Font num(numFam, 8.0f, Gdiplus::FontStyleBold, Gdiplus::UnitPoint);
    SolidBrush white(g.dark ? Color(255, 0xE8, 0xE8, 0xEA) : Color(255, 32, 32, 34));
    SolidBrush muted(g.dark ? Color(200, 0xA0, 0xA4, 0xAE) : Color(200, 90, 96, 105));
"""
if old not in text:
    raise SystemExit("paint colors missing")
text = text.replace(old, new, 1)

# 6) Meter ink also follows theme for expanded labels
old = """static int Meter(Graphics& gph, Font& ui, Font& sm, int x, int y, int width, const wchar_t* title, double pct, const wchar_t* note, bool known = true) {
    SolidBrush ink(Color(255, 32, 32, 34));
    SolidBrush muted(Color(200, 90, 96, 105));
"""
new = """static int Meter(Graphics& gph, Font& ui, Font& sm, int x, int y, int width, const wchar_t* title, double pct, const wchar_t* note, bool known = true) {
    SolidBrush ink(g.dark ? Color(255, 0xE8, 0xE8, 0xEA) : Color(255, 32, 32, 34));
    SolidBrush muted(g.dark ? Color(200, 0xA0, 0xA4, 0xAE) : Color(200, 90, 96, 105));
"""
if old not in text:
    raise SystemExit("meter colors missing")
text = text.replace(old, new, 1)

# 7) DrawRingItem percent text ink
old = """    Color track(255, 220, 224, 228);
    Color ink(255, 32, 32, 34);
"""
new = """    Color track(g.dark ? Color(255, 0x3A, 0x3E, 0x44) : Color(255, 220, 224, 228));
    Color ink = IconInk();
"""
if old not in text:
    raise SystemExit("ring item colors missing")
text = text.replace(old, new, 1)

# 8) Meter bar track for dark
old = """    RoundBar(gph, (float)x, (float)y, (float)barW, (float)S(5), Color(255, 228, 230, 233));
"""
new = """    RoundBar(gph, (float)x, (float)y, (float)barW, (float)S(5),
             g.dark ? Color(255, 0x3A, 0x3E, 0x44) : Color(255, 228, 230, 233));
"""
if old not in text:
    raise SystemExit("bar track missing")
text = text.replace(old, new, 1)

# 9) WM_CREATE / timer / setting change
old = """    case WM_CREATE:
        Acrylic(h);
        Place(h);
        Refresh();
        SetTimer(h, 1, 45000, nullptr);
        return 0;
    case WM_TIMER:
        Refresh();
        return 0;
"""
new = """    case WM_CREATE:
        Acrylic(h);
        RefreshTheme(h);
        Place(h);
        Refresh();
        SetTimer(h, 1, 45000, nullptr);
        SetTimer(h, 2, 2000, nullptr); // theme poll
        return 0;
    case WM_TIMER:
        if (w == 2) { RefreshTheme(h); return 0; }
        Refresh();
        return 0;
    case WM_SETTINGCHANGE:
        if (l && (wcscmp((LPCWSTR)l, L"ImmersiveColorSet") == 0 || wcscmp((LPCWSTR)l, L"WindowsThemeElement") == 0))
            RefreshTheme(h);
        return 0;
"""
if old not in text:
    raise SystemExit("wndproc create missing")
text = text.replace(old, new, 1)

# 10) Also seed theme before first paint in wWinMain after LoadConfig
old = "    LoadConfig();\n"
# only the one in wWinMain
idx = text.find("int WINAPI wWinMain")
pos = text.find("    LoadConfig();\n", idx)
if pos < 0:
    raise SystemExit("winmain LoadConfig missing")
insert = pos + len("    LoadConfig();\n")
if "RefreshTheme(nullptr)" not in text[insert:insert+80]:
    text = text[:insert] + "    g.dark = ReadAppsDark();\n" + text[insert:]

p.write_text(text, encoding="utf-8")
print("theme patched")

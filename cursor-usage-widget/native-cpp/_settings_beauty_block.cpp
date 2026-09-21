// ---- Settings window (美工 locked: 320 / cards / #F8F8FA) ----
static const int IDM_SETTINGS = 18;
static const int kSettingsW = 320;

struct SettingsDlg {
    HWND hwnd = nullptr;
    RECT closeBtn{};
    RECT dockBtn[3]{};
    RECT alphaBtn[4]{};
    RECT botRow{};
    RECT botSwitch{};
    RECT addBtn{};
    RECT emptyHit{};
    RECT shortcutRow[8]{};
    int shortcutRows = 0;
} g_settings;

static void RoundRectPath(GraphicsPath& path, float x, float y, float w, float h, float r) {
    if (r < 0.5f) r = 0.5f;
    if (r * 2 > w) r = w / 2;
    if (r * 2 > h) r = h / 2;
    path.AddArc(x, y, r * 2, r * 2, 180, 90);
    path.AddArc(x + w - r * 2, y, r * 2, r * 2, 270, 90);
    path.AddArc(x + w - r * 2, y + h - r * 2, r * 2, r * 2, 0, 90);
    path.AddArc(x, y + h - r * 2, r * 2, r * 2, 90, 90);
    path.CloseFigure();
}

static int SettingsShortcutListH() {
    if (g_shortcuts.empty()) return 36;
    int n = (int)g_shortcuts.size();
    if (n > 8) n = 8;
    return n * 36;
}

static int SettingsContentH() {
    const int pad = 16;
    const int titleH = 28;
    const int cardGap = 12;
    const int cardPad = 12;
    const int titleToCtrl = 10;
    const int chipH = 28;
    const int botRowH = 28;
    const int addH = 32;
    int dockCard = cardPad + 14 + titleToCtrl + chipH + cardPad;
    int alphaCard = cardPad + 14 + titleToCtrl + chipH + cardPad;
    int botCard = cardPad + botRowH + cardPad;
    int listH = SettingsShortcutListH();
    int scCard = cardPad + 14 + titleToCtrl + listH + 10 + addH + cardPad;
    return pad + titleH + 12
        + dockCard + cardGap
        + alphaCard + cardGap
        + botCard + cardGap
        + scCard
        + pad;
}

static void SettingsLayout(int /*cw*/, int /*ch*/) {
    const int pad = 16;
    const int inner = kSettingsW - pad * 2;
    const int cardGap = 12;
    const int cardPad = 12;
    const int titleToCtrl = 10;
    const int chipH = 28;

    g_settings.closeBtn = { kSettingsW - pad - 28, pad - 2, kSettingsW - pad, pad + 26 };

    int y = pad + 28 + 12;

    // Card 1: 贴边
    int cardTop = y;
    int contentTop = cardTop + cardPad + 14 + titleToCtrl;
    int bw = (inner - cardPad * 2 - 8 * 2) / 3;
    int bx = pad + cardPad;
    for (int i = 0; i < 3; ++i) {
        int x0 = bx + i * (bw + 8);
        g_settings.dockBtn[i] = { x0, contentTop, x0 + bw, contentTop + chipH };
    }
    y = contentTop + chipH + cardPad + cardGap;

    // Card 2: 卡片透明度
    cardTop = y;
    contentTop = cardTop + cardPad + 14 + titleToCtrl;
    int aw = (inner - cardPad * 2 - 6 * 3) / 4;
    int ax = pad + cardPad;
    for (int i = 0; i < 4; ++i) {
        int x0 = ax + i * (aw + 6);
        g_settings.alphaBtn[i] = { x0, contentTop, x0 + aw, contentTop + chipH };
    }
    y = contentTop + chipH + cardPad + cardGap;

    // Card 3: Bot row (label left / switch right)
    cardTop = y;
    contentTop = cardTop + cardPad;
    g_settings.botRow = { pad + cardPad, contentTop, pad + inner - cardPad, contentTop + 28 };
    g_settings.botSwitch = { g_settings.botRow.right - 44, contentTop + 3, g_settings.botRow.right, contentTop + 3 + 22 };
    y = contentTop + 28 + cardPad + cardGap;

    // Card 4: shortcuts
    cardTop = y;
    contentTop = cardTop + cardPad + 14 + titleToCtrl;
    int listH = SettingsShortcutListH();
    g_settings.emptyHit = { pad + cardPad, contentTop, pad + inner - cardPad, contentTop + listH };
    g_settings.shortcutRows = 0;
    if (!g_shortcuts.empty()) {
        int n = (int)g_shortcuts.size();
        if (n > 8) n = 8;
        g_settings.shortcutRows = n;
        for (int i = 0; i < n; ++i) {
            int ry = contentTop + i * 36;
            g_settings.shortcutRow[i] = { pad + cardPad, ry, pad + inner - cardPad, ry + 36 };
        }
    }
    int addY = contentTop + listH + 10;
    g_settings.addBtn = { pad + cardPad, addY, pad + cardPad + 96, addY + 32 };
}

static void DrawSettingsChip(Graphics& gph, const RECT& rc, const wchar_t* label, bool on, bool blueSel, Font& f) {
    float x = (float)rc.left, y = (float)rc.top, w = (float)(rc.right - rc.left), h = (float)(rc.bottom - rc.top);
    GraphicsPath path;
    RoundRectPath(path, x + 0.5f, y + 0.5f, w - 1.f, h - 1.f, 6.f);
    Color fillC = Color(255, 255, 255, 255);
    Color borderC = Color(255, 0xD8, 0xDC, 0xE1);
    float borderW = 1.f;
    if (on) {
        if (blueSel) {
            fillC = Color(255, 0xE8, 0xF1, 0xFF);
            borderC = Color(255, 0x2F, 0x6F, 0xED);
            borderW = 1.6f;
        } else {
            fillC = Color(255, 0xEE, 0xF2, 0xF7);
            borderC = Color(255, 0xA8, 0xB2, 0xC0);
            borderW = 1.4f;
        }
    }
    SolidBrush fill(fillC);
    gph.FillPath(&fill, &path);
    Pen border(borderC, borderW);
    gph.DrawPath(&border, &path);
    SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
    StringFormat fmt;
    fmt.SetAlignment(StringAlignmentCenter);
    fmt.SetLineAlignment(StringAlignmentCenter);
    gph.DrawString(label, -1, &f, RectF(x, y, w, h), &fmt, &ink);
}

static void DrawSettingsCard(Graphics& gph, float x, float y, float w, float h) {
    GraphicsPath path;
    RoundRectPath(path, x + 0.5f, y + 0.5f, w - 1.f, h - 1.f, 8.f);
    SolidBrush fill(Color(255, 255, 255, 255));
    gph.FillPath(&fill, &path);
    Pen border(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
    gph.DrawPath(&border, &path);
}

static void PaintSettings(HWND h) {
    RECT crc; GetClientRect(h, &crc);
    int cw = crc.right, ch = crc.bottom;
    if (cw <= 0 || ch <= 0) return;
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
        gph.SetPixelOffsetMode(PixelOffsetModeHighQuality);

        SolidBrush bg(Color(255, 0xF8, 0xF8, 0xFA));
        gph.FillRectangle(&bg, 0, 0, cw, ch);
        GraphicsPath frame;
        RoundRectPath(frame, 0.5f, 0.5f, (float)cw - 1.f, (float)ch - 1.f, 12.f);
        Pen stroke(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
        gph.DrawPath(&stroke, &frame);

        FontFamily yahei(L"Microsoft YaHei UI");
        const FontFamily* fam = (yahei.GetLastStatus() == Gdiplus::Ok) ? &yahei : FontFamily::GenericSansSerif();
        Font title(fam, 15.f, Gdiplus::FontStyleBold, Gdiplus::UnitPixel);
        Font sec(fam, 10.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        Font ui(fam, 12.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        Font uiSm(fam, 11.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        SolidBrush titleBr(Color(255, 0x20, 0x20, 0x22));
        SolidBrush secBr(Color(255, 0x5A, 0x60, 0x69));
        SolidBrush muted(Color(255, 0x78, 0x7D, 0x85));

        const int pad = 16;
        const int inner = kSettingsW - pad * 2;
        const int cardGap = 12;
        const int cardPad = 12;
        const int titleToCtrl = 10;
        const int chipH = 28;

        gph.DrawString(L"\u8bbe\u7f6e", -1, &title, PointF((float)pad, (float)pad + 2.f), &titleBr);
        // close X
        {
            RECT cr = g_settings.closeBtn;
            float cx = (cr.left + cr.right) / 2.f;
            float cy = (cr.top + cr.bottom) / 2.f;
            Pen xp(Color(255, 0x5A, 0x60, 0x69), 1.6f);
            xp.SetStartCap(LineCapRound);
            xp.SetEndCap(LineCapRound);
            gph.DrawLine(&xp, cx - 5.f, cy - 5.f, cx + 5.f, cy + 5.f);
            gph.DrawLine(&xp, cx + 5.f, cy - 5.f, cx - 5.f, cy + 5.f);
        }

        int y = pad + 28 + 12;

        // --- card 贴边 ---
        {
            int cardH = cardPad + 14 + titleToCtrl + chipH + cardPad;
            DrawSettingsCard(gph, (float)pad, (float)y, (float)inner, (float)cardH);
            gph.DrawString(L"\u8d34\u8fb9", -1, &sec, PointF((float)(pad + cardPad), (float)(y + cardPad)), &secBr);
            const wchar_t* docks[] = { L"\u5de6", L"\u53f3", L"\u9876" };
            for (int i = 0; i < 3; ++i)
                DrawSettingsChip(gph, g_settings.dockBtn[i], docks[i], g.dockEdge == i, false, ui);
            y += cardH + cardGap;
        }

        // --- card 卡片透明度 ---
        {
            int cardH = cardPad + 14 + titleToCtrl + chipH + cardPad;
            DrawSettingsCard(gph, (float)pad, (float)y, (float)inner, (float)cardH);
            gph.DrawString(L"\u5361\u7247\u900f\u660e\u5ea6", -1, &sec, PointF((float)(pad + cardPad), (float)(y + cardPad)), &secBr);
            const int alphas[] = { 100, 85, 70, 55 };
            wchar_t alab[8];
            for (int i = 0; i < 4; ++i) {
                swprintf(alab, 8, L"%d", alphas[i]);
                DrawSettingsChip(gph, g_settings.alphaBtn[i], alab, g.bgAlpha == alphas[i], true, ui);
            }
            y += cardH + cardGap;
        }

        // --- card Bot ---
        {
            int cardH = cardPad + 28 + cardPad;
            DrawSettingsCard(gph, (float)pad, (float)y, (float)inner, (float)cardH);
            RECT rc = g_settings.botRow;
            gph.DrawString(L"\u663e\u793a Bot \u7528\u91cf", -1, &ui,
                PointF((float)rc.left, (float)rc.top + 4.f), &titleBr);
            float tx = (float)g_settings.botSwitch.left;
            float ty = (float)g_settings.botSwitch.top;
            float tw = 44.f, th = 22.f;
            GraphicsPath tpath;
            RoundRectPath(tpath, tx, ty, tw, th, th / 2.f);
            SolidBrush tfill(g.showBot ? Color(255, 0x2F, 0x6F, 0xED) : Color(255, 0xD0, 0xD4, 0xDA));
            gph.FillPath(&tfill, &tpath);
            float knob = th - 4.f;
            float kx = g.showBot ? (tx + tw - knob - 2.f) : (tx + 2.f);
            SolidBrush knobBr(Color(255, 255, 255, 255));
            gph.FillEllipse(&knobBr, kx, ty + 2.f, knob, knob);
            y += cardH + cardGap;
        }

        // --- card 快捷方式 ---
        {
            int listH = SettingsShortcutListH();
            int addH = 32;
            int cardH = cardPad + 14 + titleToCtrl + listH + 10 + addH + cardPad;
            DrawSettingsCard(gph, (float)pad, (float)y, (float)inner, (float)cardH);
            gph.DrawString(L"\u5feb\u6377\u65b9\u5f0f", -1, &sec, PointF((float)(pad + cardPad), (float)(y + cardPad)), &secBr);
            if (g_shortcuts.empty()) {
                StringFormat fmt;
                fmt.SetAlignment(StringAlignmentCenter);
                fmt.SetLineAlignment(StringAlignmentCenter);
                RECT er = g_settings.emptyHit;
                gph.DrawString(L"\u6dfb\u52a0\u5e38\u7528\u8f6f\u4ef6", -1, &uiSm,
                    RectF((float)er.left, (float)er.top, (float)(er.right - er.left), (float)(er.bottom - er.top)),
                    &fmt, &muted);
            } else {
                int n = g_settings.shortcutRows;
                {
                    HDC gdc = gph.GetHDC();
                    for (int i = 0; i < n; ++i) {
                        RECT rr = g_settings.shortcutRow[i];
                        int icon = 28;
                        int iy = rr.top + (36 - icon) / 2;
                        int ix = rr.left;
                        if (g_shortcuts[i].icon)
                            DrawIconEx(gdc, ix, iy, g_shortcuts[i].icon, icon, icon, 0, nullptr, DI_NORMAL);
                    }
                    gph.ReleaseHDC(gdc);
                }
                for (int i = 0; i < n; ++i) {
                    RECT rr = g_settings.shortcutRow[i];
                    int icon = 28;
                    int iy = rr.top + (36 - icon) / 2;
                    int ix = rr.left;
                    if (!g_shortcuts[i].icon) {
                        SolidBrush ph(Color(255, 0xE8, 0xEA, 0xEE));
                        gph.FillRectangle(&ph, (float)ix, (float)iy, (float)icon, (float)icon);
                    }
                    std::wstring name = g_shortcuts[i].name;
                    if (name.empty()) {
                        const wchar_t* nms = g_shortcuts[i].path.c_str();
                        for (const wchar_t* q = nms; *q; ++q)
                            if (*q == L'\' || *q == L'/') nms = q + 1;
                        name = nms;
                    }
                    gph.DrawString(name.c_str(), -1, &uiSm,
                        PointF((float)(ix + icon + 10), (float)(rr.top + 8)), &titleBr);
                }
            }
            // secondary add button
            {
                RECT ar = g_settings.addBtn;
                float x = (float)ar.left, yy = (float)ar.top, w = (float)(ar.right - ar.left), hh = (float)(ar.bottom - ar.top);
                GraphicsPath ap;
                RoundRectPath(ap, x + 0.5f, yy + 0.5f, w - 1.f, hh - 1.f, 6.f);
                SolidBrush af(Color(255, 0xF8, 0xF8, 0xFA));
                gph.FillPath(&af, &ap);
                Pen ab(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
                gph.DrawPath(&ab, &ap);
                StringFormat fmt;
                fmt.SetAlignment(StringAlignmentCenter);
                fmt.SetLineAlignment(StringAlignmentCenter);
                SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
                gph.DrawString(L"\u6dfb\u52a0", -1, &uiSm, RectF(x, yy, w, hh), &fmt, &ink);
            }
        }
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
        {
            int hgt = SettingsContentH();
            HRGN rgn = CreateRoundRectRgn(0, 0, kSettingsW + 1, hgt + 1, 24, 24);
            SetWindowRgn(h, rgn, TRUE);
        }
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT:
        PaintSettings(h);
        return 0;
    case WM_LBUTTONUP: {
        int x = GET_X_LPARAM(l), y = GET_Y_LPARAM(l);
        if (PtIn(g_settings.closeBtn, x, y)) { DestroyWindow(h); return 0; }
        for (int i = 0; i < 3; ++i) if (PtIn(g_settings.dockBtn[i], x, y)) {
            if (g.dockEdge != i) { g.dockEdge = i; g.y = -1; }
            SaveConfig(); SettingsApplyMain(); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        const int alphas[] = { 100, 85, 70, 55 };
        for (int i = 0; i < 4; ++i) if (PtIn(g_settings.alphaBtn[i], x, y)) {
            g.bgAlpha = alphas[i]; SaveConfig(); SettingsApplyMain(); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        if (PtIn(g_settings.botRow, x, y) || PtIn(g_settings.botSwitch, x, y)) {
            g.showBot = !g.showBot; SaveConfig(); SettingsApplyMain(); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        if (PtIn(g_settings.addBtn, x, y) || (g_shortcuts.empty() && PtIn(g_settings.emptyHit, x, y))) {
            OpenManageShortcuts();
            return 0;
        }
        return 0;
    }
    case WM_CLOSE:
        DestroyWindow(h);
        return 0;
    case WM_DESTROY:
        if (g_settings.hwnd == h) g_settings.hwnd = nullptr;
        return 0;
    }
    return DefWindowProcW(h, m, w, l);
}

static void OpenSettings() {
    if (g_settings.hwnd && !IsWindow(g_settings.hwnd))
        g_settings.hwnd = nullptr;
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
    ShowWindow(hw, SW_SHOW);
    UpdateWindow(hw);
}


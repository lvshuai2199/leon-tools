# -*- coding: utf-8 -*-
from pathlib import Path
import re
p = Path(r"D:\GitFiles\leon-tools\cursor-usage-widget\native-cpp\CursorUsage.cpp")
text = p.read_text(encoding="utf-8")
nl = "\r\n" if "\r\n" in text else "\n"
c = text.replace("\r\n", "\n")

def repl(old, new, label):
    global c
    if old not in c:
        raise SystemExit("FAIL " + label)
    c = c.replace(old, new, 1)
    print("ok", label)

repl("    bool dockRight = true;", "    int dockEdge = 1; // 0 left, 1 right, 2 top", "field")
repl('        if (k == "dock") g.dockEdge = v != "left";'.replace("dockEdge","dockRight"),
     '        if (k == "dock") {\n            if (v == "left") g.dockEdge = 0;\n            else if (v == "top") g.dockEdge = 2;\n            else g.dockEdge = 1;\n        }', "load")
repl('    out << "dock=" << (g.dockRight ? "right" : "left") << "\\n";',
     '    out << "dock=" << (g.dockEdge == 0 ? "left" : g.dockEdge == 2 ? "top" : "right") << "\\n";', "save")

# Place: replace dockRight usages inside Place by rewriting whole function via markers
a = c.find("static void Place(HWND h) {")
b = c.find("static void DragMove(HWND h) {")
if a < 0 or b < 0: raise SystemExit("FAIL Place markers")
place = '''static void Place(HWND h) {
    RECT wa = Work();
    int w = WinW();
    int hh = WinH();
    int x = 0, y = 0;
    if (g.dockEdge == 2) {
        if (g.y < 0) g.y = wa.left + (wa.right - wa.left - w) / 2;
        g.y = ClampI(g.y, (int)wa.left + 4, (int)wa.right - w - 4);
        x = g.y;
        y = wa.top;
    } else {
        if (g.y < 0) g.y = wa.top + (wa.bottom - wa.top - hh) / 2;
        g.y = ClampI(g.y, (int)wa.top + 4, (int)wa.bottom - hh - 4);
        x = g.dockEdge == 1 ? wa.right - w : wa.left;
        y = g.y;
    }
    SetWindowPos(h, HWND_TOPMOST, x, y, w, hh,
                 SWP_NOACTIVATE | SWP_NOCOPYBITS | SWP_SHOWWINDOW);
    ApplyRegion(h);
    RedrawWindow(h, nullptr, nullptr, RDW_INVALIDATE | RDW_UPDATENOW | RDW_NOERASE);
}

'''
c = c[:a] + place + c[b:]
print("ok Place")

a = c.find("static void DragMove(HWND h) {")
b = c.find("struct ACCENTPOLICY")
if a < 0 or b < 0: raise SystemExit("FAIL DragMove markers")
drag = '''static void DragMove(HWND h) {
    RECT wa = Work();
    int w = WinW();
    int hh = WinH();
    int x = 0, y = 0;
    if (g.dockEdge == 2) {
        g.y = ClampI(g.y, (int)wa.left + 4, (int)wa.right - w - 4);
        x = g.y;
        y = wa.top;
    } else {
        g.y = ClampI(g.y, (int)wa.top + 4, (int)wa.bottom - hh - 4);
        x = g.dockEdge == 1 ? wa.right - w : wa.left;
        y = g.y;
    }
    SetWindowPos(h, nullptr, x, y, 0, 0,
                 SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_NOREDRAW);
}

'''
c = c[:a] + drag + c[b:]
print("ok DragMove")

a = c.find("static void AddBodyPath(GraphicsPath& body, float w, float hh, float rad, float inset) {")
b = c.find("static HBITMAP MakeDib(int w, int hh, void** bits) {")
if a < 0 or b < 0: raise SystemExit("FAIL AddBodyPath markers")
body = '''static void AddBodyPath(GraphicsPath& body, float w, float hh, float rad, float inset) {
    float x = inset;
    float y = inset;
    float rw = w - inset * 2.f;
    float rh = hh - inset * 2.f;
    float r = rad;
    if (r < 8.f) r = 8.f;
    if (g.dockEdge == 2) {
        if (r * 2.f > rw * 0.4f) r = rw * 0.2f;
    } else {
        if (r * 2.f > rh * 0.4f) r = rh * 0.2f;
    }
    float right = x + rw;
    float bot = y + rh;
    if (g.dockEdge == 2) {
        body.AddLine(x, y, right, y);
        body.AddLine(right, y, right, bot - r);
        body.AddArc(right - r * 2.f, bot - r * 2.f, r * 2.f, r * 2.f, 0.f, 90.f);
        body.AddLine(right - r, bot, x + r, bot);
        body.AddArc(x, bot - r * 2.f, r * 2.f, r * 2.f, 90.f, 90.f);
        body.AddLine(x, bot - r, x, y);
        body.CloseFigure();
    } else if (g.dockEdge == 1) {
        body.AddArc(x, y, r * 2.f, r * 2.f, 180.f, 90.f);
        body.AddLine(x + r, y, right, y);
        body.AddLine(right, y, right, bot);
        body.AddLine(right, bot, x + r, bot);
        body.AddArc(x, bot - r * 2.f, r * 2.f, r * 2.f, 90.f, 90.f);
        body.CloseFigure();
    } else {
        body.AddArc(right - r * 2.f, y, r * 2.f, r * 2.f, 270.f, 90.f);
        body.AddLine(right, y + r, right, bot - r);
        body.AddArc(right - r * 2.f, bot - r * 2.f, r * 2.f, r * 2.f, 0.f, 90.f);
        body.AddLine(right - r, bot, x, bot);
        body.AddLine(x, bot, x, y);
        body.AddLine(x, y, right - r, y);
        body.CloseFigure();
    }
}

'''
c = c[:a] + body + c[b:]
print("ok AddBodyPath")

repl(
'''            if (abs(pt.y - g.press.y) > 4 || abs(pt.x - g.press.x) > 4) {
                g.dragging = true;
                g.y = g.pressY + (pt.y - g.press.y);
                DragMove(h);
            }''',
'''            if (abs(pt.y - g.press.y) > 4 || abs(pt.x - g.press.x) > 4) {
                g.dragging = true;
                if (g.dockEdge == 2)
                    g.y = g.pressY + (pt.x - g.press.x);
                else
                    g.y = g.pressY + (pt.y - g.press.y);
                DragMove(h);
            }''', "drag")

# handlers first
repl(
'''        if (cmd == 12) { g.dockRight = false; Place(h); SaveConfig(); }
        if (cmd == 13) { g.dockRight = true; Place(h); SaveConfig(); }''',
'''        if (cmd == 12 || cmd == 13 || cmd == 17) {
            int next = (cmd == 12) ? 0 : (cmd == 13) ? 1 : 2;
            if (next != g.dockEdge) {
                g.dockEdge = next;
                g.y = -1;
            }
            Place(h);
            SaveConfig();
        }''', "handlers")

m = re.search(r'AppendMenuW\(menu, MF_STRING, 12, L"[^"]*"\);\n\s*AppendMenuW\(menu, MF_STRING, 13, L"[^"]*"\);', c)
if not m: raise SystemExit("FAIL menu")
ins = (
    'AppendMenuW(menu, (UINT)(MF_STRING | (g.dockEdge == 0 ? MF_CHECKED : 0)), 12, L"\\u8d34\\u5230\\u5de6\\u8fb9");\n'
    '        AppendMenuW(menu, (UINT)(MF_STRING | (g.dockEdge == 1 ? MF_CHECKED : 0)), 13, L"\\u8d34\\u5230\\u53f3\\u8fb9");\n'
    '        AppendMenuW(menu, (UINT)(MF_STRING | (g.dockEdge == 2 ? MF_CHECKED : 0)), 17, L"\\u8d34\\u5230\\u9876\\u90e8");'
)
c = c[:m.start()] + ins + c[m.end():]
print("ok menu")

if "dockRight" in c:
    raise SystemExit("FAIL leftover dockRight: " + str(c.count("dockRight")))

out = c.replace("\n", nl)
p.write_text(out, encoding="utf-8", newline="")
print("DONE")

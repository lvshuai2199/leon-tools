# -*- coding: utf-8 -*-
from pathlib import Path
p = Path(r"D:\GitFiles\leon-tools\cursor-usage-widget\native-cpp\CursorUsage.cpp")
text = p.read_text(encoding="utf-8")
nl = "\r\n" if "\r\n" in text else "\n"
c = text.replace("\r\n", "\n")

def repl_block(start_marker, end_marker, new_block, label):
    global c
    a = c.find(start_marker)
    b = c.find(end_marker)
    if a < 0 or b < 0 or b <= a:
        raise SystemExit("FAIL markers " + label)
    c = c[:a] + new_block + c[b:]
    print("ok", label)

# Replace StripW through WinH
new_sizes = r'''// Collapsed rings ~1.13x; side dock = vertical strip, top dock = horizontal bar.
static const float kRingScale = 1.13f;
static float RingR() { return (float)S(14) * kRingScale; }
static int CollapsedRingCount() { return g.showBot ? 4 : 3; }

static int StripW() {
    if (g.dockEdge == 2) {
        // pad 12, ring gap 10, rings L->R
        float r = RingR();
        int n = CollapsedRingCount();
        int gap = S(10);
        return S(12) * 2 + (int)(n * (2.f * r) + (n - 1) * gap + 0.5f);
    }
    return S((int)(BASE_STRIP_W * kRingScale + 0.5));
}
static int StripH() {
    if (g.dockEdge == 2) {
        float r = RingR();
        // padTB 8 + ring + percent under ring
        return S(8) * 2 + (int)(2.f * r + (float)S(11) + (float)S(10) + 0.5f);
    }
    float r = RingR();
    float y0 = (float)S(30) * kRingScale;
    float step = (float)S(64) * kRingScale;
    int n = CollapsedRingCount();
    float lastY = y0 + step * (float)(n - 1);
    float topPad = y0 - r;
    if (topPad < (float)S(8)) topPad = (float)S(8);
    float contentBottom = lastY + r + (float)S(11) + (float)S(10);
    return (int)(contentBottom + topPad + 0.5f);
}
static int QuickColW() {
    // side pad 12, up to 4 icons / row before wrap feels natural; width fits 4
    return S(12) * 2 + S(28) * 4 + S(8) * 3;
}
static int PanelW() {
    if (g.dockEdge == 2)
        return S(BASE_PANEL_W) + S(16) + QuickColW();
    return S(BASE_PANEL_W);
}
static int MeterH() { return S(57); }
static int ExpandedChromeH() {
    if (!g.snap.ok) return S(46) + S(36);
    return S(46) + MeterH() * (g.showBot ? 4 : 3) + S(8);
}
static int TokenListH() {
    int n = (int)g.snap.models.size();
    if (n < 1 && !g.snap.topModel.empty()) n = 1;
    return S(26) + S(16) + n * S(16) + S(18);
}
static int ExpandedWantH() {
    if (!g.snap.ok) return ExpandedChromeH() + S(28);
    return ExpandedChromeH() + TokenListH() + S(14);
}
static int ExpandedH() {
    // Top dock: shortcuts column empty-state is short; height still follows usage.
    return ExpandedWantH();
}
static int TokenViewY() { return ExpandedChromeH(); }
static int TokenViewH() {
    int h = ExpandedH() - TokenViewY() - S(12);
    return h < S(36) ? S(36) : h;
}
static int MaxScroll() {
    return 0;
}
static int WinW() { return g.expanded ? PanelW() : StripW(); }
static int WinH() { return g.expanded ? ExpandedH() : StripH(); }

'''
repl_block("// Collapsed rings ~1.13x;", "static std::wstring ConfigPath()", new_sizes, "sizes")

# Replace collapsed paint block
old_collapsed = '''    if (!g.expanded) {
        float r = (float)S(14) * kRingScale;
        float cx = w * 0.5f;
        float y0 = (float)S(30) * kRingScale;
        float step = (float)S(64) * kRingScale;
        DrawRingItem(gph, num, cx, y0, r, g.snap.autoP, 0);
        DrawRingItem(gph, num, cx, y0 + step, r, g.snap.api, 1);
        DrawRingItem(gph, num, cx, y0 + step * 2, r, g.snap.total, 2);
        if (g.showBot)
            DrawRingItem(gph, num, cx, y0 + step * 3, r, g.snap.botP, 3, g.snap.botKnown);
    } else {'''

new_collapsed = '''    if (!g.expanded) {
        float r = RingR();
        if (g.dockEdge == 2) {
            // Horizontal bar: rings L->R, pad 12/8, gap 10, % under each.
            int n = CollapsedRingCount();
            float gap = (float)S(10);
            float padL = (float)S(12);
            float cy = (float)S(8) + r;
            float x0 = padL + r;
            float step = 2.f * r + gap;
            DrawRingItem(gph, num, x0, cy, r, g.snap.autoP, 0);
            DrawRingItem(gph, num, x0 + step, cy, r, g.snap.api, 1);
            DrawRingItem(gph, num, x0 + step * 2.f, cy, r, g.snap.total, 2);
            if (g.showBot)
                DrawRingItem(gph, num, x0 + step * 3.f, cy, r, g.snap.botP, 3, g.snap.botKnown);
            (void)n;
        } else {
            float cx = w * 0.5f;
            float y0 = (float)S(30) * kRingScale;
            float step = (float)S(64) * kRingScale;
            DrawRingItem(gph, num, cx, y0, r, g.snap.autoP, 0);
            DrawRingItem(gph, num, cx, y0 + step, r, g.snap.api, 1);
            DrawRingItem(gph, num, cx, y0 + step * 2, r, g.snap.total, 2);
            if (g.showBot)
                DrawRingItem(gph, num, cx, y0 + step * 3, r, g.snap.botP, 3, g.snap.botKnown);
        }
    } else {'''

if old_collapsed not in c:
    raise SystemExit("FAIL collapsed block")
c = c.replace(old_collapsed, new_collapsed, 1)
print("ok collapsed")

# Expanded: after title/meters setup, for top dock draw quick empty on the right.
# Insert helper before Paint and modify expanded branch to use usageW.

# Add DrawQuickEmpty after DrawRingItem's closing - find a spot before MakeDib / after AddBodyPath
helper = r'''
static void DrawQuickEmpty(Graphics& gph, Font& ui, float x, float y, float qw, float qh) {
    // Empty state: centered 「添加常用软件」 #787D85 — click opens manage (开发3).
    SolidBrush br(Color(255, 0x78, 0x7D, 0x85));
    const wchar_t* t = L"\u6dfb\u52a0\u5e38\u7528\u8f6f\u4ef6";
    RectF layout(x, y, qw, qh);
    StringFormat fmt;
    fmt.SetAlignment(StringAlignmentCenter);
    fmt.SetLineAlignment(StringAlignmentCenter);
    gph.DrawString(t, -1, &ui, layout, &fmt, &br);
}

'''
# Insert before static HBITMAP MakeDib
idx = c.find("static HBITMAP MakeDib(int w, int hh, void** bits) {")
if idx < 0: raise SystemExit("FAIL MakeDib")
c = c[:idx] + helper + c[idx:]
print("ok helper")

# Modify expanded paint: change pad/w usage for top dock
# Replace the start of else expanded block through meters
old_exp = '''    } else {


        int pad = S(20);
        DrawTitleMark(gph, (float)pad, (float)S(14));
        gph.DrawString(L"Cursor '''

# The Chinese may be garbled - find by structure
import re
m = re.search(
    r"    \} else \{\s*\n\s*\n\s*int pad = S\(20\);\s*\n\s*DrawTitleMark\(gph, \(float\)pad, \(float\)S\(14\)\);",
    c,
)
if not m:
    raise SystemExit("FAIL expanded start")

# Find where meters end (if showBot meter) and before token list
# We'll replace from "} else {" of expanded through the bot meter block with a version that supports top split.

# Simpler approach: after `int pad = S(20);` inject usage width and at end of meters draw quick empty.
# Find `int pad = S(20);` inside Paint expanded
pad_idx = c.find("        int pad = S(20);\n        DrawTitleMark")
if pad_idx < 0:
    pad_idx = c.find("        int pad = S(20);")
if pad_idx < 0: raise SystemExit("FAIL pad")

insert_after_pad = '''        int pad = S(20);
        int usageW = w;
        float quickX = 0.f, quickY = 0.f, quickW = 0.f, quickH = 0.f;
        if (g.dockEdge == 2) {
            usageW = S(BASE_PANEL_W);
            quickW = (float)QuickColW();
            quickX = (float)usageW + (float)S(16);
            quickY = (float)S(46);
            quickH = (float)(hh - S(46) - S(12));
            if (quickH < (float)S(40)) quickH = (float)S(40);
        }
'''
# replace only the pad line
if "int usageW = w;" in c:
    print("usageW already")
else:
    c = c.replace("        int pad = S(20);\n        DrawTitleMark", insert_after_pad + "        DrawTitleMark", 1)
    print("ok pad inject")

# Change Meter calls to use usageW instead of w for width - meters use `w` as window width
# Pattern: Meter(gph, ui, sm, pad, y, w, 
c2 = c.replace("y = Meter(gph, ui, sm, pad, y, w, L\"Auto\"", "y = Meter(gph, ui, sm, pad, y, usageW, L\"Auto\"")
c2 = c2.replace("y = Meter(gph, ui, sm, pad, y, w, L\"Models\"", "y = Meter(gph, ui, sm, pad, y, usageW, L\"Models\"")
c2 = c2.replace("y = Meter(gph, ui, sm, pad, y, w, L\"API\"", "y = Meter(gph, ui, sm, pad, y, usageW, L\"API\"")
c2 = c2.replace("y = Meter(gph, ui, sm, pad, y, w, L\"Bot\"", "y = Meter(gph, ui, sm, pad, y, usageW, L\"Bot\"")
# DrawRight for membership / tokens use w - pad -> for top should be usageW - pad when in usage column
# Only change membership DrawRight that uses w - pad at title
c = c2
print("ok meters usageW")

# After bot meter / before token list, draw quick empty for top
# Find: if (g.showBot)\n                y = Meter(...Bot...
marker = "y = Meter(gph, ui, sm, pad, y, usageW, L\"Bot\", g.snap.botP, L\"\", g.snap.botKnown);"
if marker not in c:
    # try without known
    marker = None
    for line in c.split("\n"):
        if "Meter(gph, ui, sm, pad, y, usageW, L\"Bot\"" in line:
            marker = line.strip()
            break
    if not marker:
        raise SystemExit("FAIL bot meter")

# Insert after the block that ends meters - after `if (g.showBot) y = Meter Bot`
needle = """            if (g.showBot)
                y = Meter(gph, ui, sm, pad, y, usageW, L\"Bot\", g.snap.botP, L\"\", g.snap.botKnown);
        }

        if (g.snap.ok) {"""
repl = """            if (g.showBot)
                y = Meter(gph, ui, sm, pad, y, usageW, L\"Bot\", g.snap.botP, L\"\", g.snap.botKnown);
        }

        if (g.dockEdge == 2 && quickW > 1.f)
            DrawQuickEmpty(gph, ui, quickX, quickY, quickW, quickH);

        if (g.snap.ok) {"""
if needle not in c:
    # show nearby
    i = c.find('L"Bot"')
    print("NEAR BOT", repr(c[i-80:i+120]))
    raise SystemExit("FAIL needle meters end")
c = c.replace(needle, repl, 1)
print("ok quick empty")

# Token DrawRight and strings should use usageW for right edge when top
c = c.replace(
    "DrawRight(gph, FormatTok(g.snap.today()), num, white, (float)(w - pad), (float)ty);",
    "DrawRight(gph, FormatTok(g.snap.today()), num, white, (float)(usageW - pad), (float)ty);",
    1,
)
c = c.replace(
    "DrawRight(gph, FormatTok(row.tokens), sm, muted, (float)(w - pad), (float)ty);",
    "DrawRight(gph, FormatTok(row.tokens), sm, muted, (float)(usageW - pad), (float)ty);",
    1,
)
# membership
c = c.replace(
    "DrawRight(gph, Utf8ToWide(g.snap.membership), ui, muted, (float)(w - pad), (float)S(16));",
    "DrawRight(gph, Utf8ToWide(g.snap.membership), ui, muted, (float)(usageW - pad), (float)S(16));",
    1,
)
print("ok token rights")

if "dockRight" in c:
    raise SystemExit("unexpected dockRight")

out = c.replace("\n", nl)
p.write_text(out, encoding="utf-8", newline="")
print("DONE")

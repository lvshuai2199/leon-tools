from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")
old = """static int ExpandedChromeH() { return S(46) + MeterH() * (g.showBot ? 4 : 3) + S(8); }
static int TokenListH() {
    int n = (int)g.snap.models.size();
    if (n < 1 && !g.snap.topModel.empty()) n = 1;
    return S(26) + S(16) + n * S(16) + S(18);
}
static int ExpandedWantH() { return ExpandedChromeH() + TokenListH() + S(14); }
"""
new = """static int ExpandedChromeH() {
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
"""
if old not in text:
    raise SystemExit("height block missing")
p.write_text(text.replace(old, new, 1), encoding="utf-8")
print("height ok")

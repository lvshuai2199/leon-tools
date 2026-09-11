from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")
old = """static void DrawRingGlyph(Graphics& gph, float cx, float cy, float size, int kind) {
    const float s = size / 32.f;
    const float ox = cx - size * 0.5f;
    const float oy = cy - size * 0.5f;
    if (kind == 3) {
"""
new = """static void DrawRingGlyph(Graphics& gph, float cx, float cy, float size, int kind) {
    const float s = size / 32.f;
    const float ox = cx - size * 0.5f;
    const float oy = cy - size * 0.5f;
    if (kind == 0) {
        // icon-auto.svg: hex cursor, fill #2F6FED, viewBox 0 0 49 56
        SolidBrush br(Color(255, 0x2F, 0x6F, 0xED));
        const float vw = 49.f, vh = 56.f;
        const float sc = size / vh * 0.92f;
        const float bx = cx - vw * sc * 0.5f;
        const float by = cy - vh * sc * 0.5f;
        auto X = [&](float x) { return bx + x * sc; };
        auto Y = [&](float y) { return by + y * sc; };
        GraphicsPath path(FillModeAlternate);
        PointF outer[12] = {
            {X(48.0226f), Y(13.2547f)}, {X(25.6601f), Y(0.3118f)}, {X(23.3378f), Y(0.3118f)},
            {X(0.9763f), Y(13.2547f)}, {X(0.f), Y(14.9502f)}, {X(0.f), Y(41.0498f)},
            {X(0.9763f), Y(42.7453f)}, {X(23.3389f), Y(55.6882f)}, {X(25.6611f), Y(55.6882f)},
            {X(48.0237f), Y(42.7453f)}, {X(49.f), Y(41.0498f)}, {X(49.f), Y(14.9502f)},
        };
        path.AddPolygon(outer, 12);
        PointF inner[9] = {
            {X(46.6179f), Y(15.9964f)}, {X(25.0302f), Y(53.4802f)}, {X(24.4989f), Y(53.337f)},
            {X(24.4989f), Y(28.793f)}, {X(23.8134f), Y(27.6027f)}, {X(2.6109f), Y(15.3312f)},
            {X(2.7537f), Y(14.7987f)}, {X(45.9292f), Y(14.7987f)}, {X(46.619f), Y(15.9974f)},
        };
        path.AddPolygon(inner, 9);
        gph.FillPath(&br, &path);
        return;
    }
    if (kind == 3) {
"""
if old not in text:
    raise SystemExit("DrawRingGlyph head missing")
text = text.replace(old, new, 1)
# remove old default tip polygon (kind 0 fallback at end)
old2 = """    PointF pts[7] = {
        {ox + 10.5f * s, oy + 6.5f * s},
        {ox + 10.5f * s, oy + 23.5f * s},
        {ox + 14.6f * s, oy + 18.8f * s},
        {ox + 18.8f * s, oy + 25.5f * s},
        {ox + 21.6f * s, oy + 23.8f * s},
        {ox + 17.6f * s, oy + 16.8f * s},
        {ox + 23.2f * s, oy + 16.2f * s},
    };
    GraphicsPath tip;
    tip.AddPolygon(pts, 7);
    SolidBrush br(Color(255, 0x2F, 0x6F, 0xED));
    gph.FillPath(&br, &tip);
}
"""
new2 = """}
"""
if old2 not in text:
    raise SystemExit("old tip missing")
text = text.replace(old2, new2, 1)
p.write_text(text, encoding="utf-8")
print("patched auto ring")

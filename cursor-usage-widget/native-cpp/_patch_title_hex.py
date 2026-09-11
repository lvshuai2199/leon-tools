from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")

old_title = """static void DrawTitleMark(Graphics& gph, float x, float y) {
    // usage-badge.svg gauge: viewBox 0 0 16 16, stroke/fill #202022
    const float s = 1.f;
    Color ink(255, 0x20, 0x20, 0x22);
    Pen pen(ink, 1.5f * s);
    pen.SetStartCap(Gdiplus::LineCapRound);
    pen.SetEndCap(Gdiplus::LineCapRound);
    pen.SetLineJoin(Gdiplus::LineJoinRound);
    // Arc M2.75 11.25 a5.75 5.75 0 1 1 10.5 0 (large arc through top)
    const float cx = x + 8.f * s;
    const float cy = y + 11.25f * s + 2.345f * s;
    const float r = 5.75f * s;
    gph.DrawArc(&pen, cx - r, cy - r, r * 2.f, r * 2.f, 204.1f, 228.f);
    // Needle M8 10.1 L10.85 6.55
    gph.DrawLine(&pen, x + 8.f * s, y + 10.1f * s, x + 10.85f * s, y + 6.55f * s);
    // Hub circle cx=8 cy=10.1 r=1.15
    SolidBrush hub(ink);
    const float hr = 1.15f * s;
    gph.FillEllipse(&hub, x + 8.f * s - hr, y + 10.1f * s - hr, hr * 2.f, hr * 2.f);
}
"""
new_title = """static void DrawTitleMark(Graphics& gph, float x, float y) {
    // usage-badge.svg: hex cursor #202022, viewBox 0 0 49 56, transparent cutout
    SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
    const float vw = 49.f, vh = 56.f;
    const float sc = 16.f / vh;
    const float bx = x;
    const float by = y;
    auto X = [&](float px) { return bx + px * sc; };
    auto Y = [&](float py) { return by + py * sc; };
    GraphicsPath path(Gdiplus::FillModeAlternate);
    path.StartFigure();
    path.AddLine(X(48.0226f), Y(13.2547f), X(25.6601f), Y(0.3118f));
    path.AddBezier(X(25.6601f), Y(0.3118f), X(24.942f), Y(-0.1039f), X(24.0559f), Y(-0.1039f), X(23.3378f), Y(0.3118f));
    path.AddLine(X(23.3378f), Y(0.3118f), X(0.9763f), Y(13.2547f));
    path.AddBezier(X(0.9763f), Y(13.2547f), X(0.3727f), Y(13.6041f), X(0.f), Y(14.2503f), X(0.f), Y(14.9502f));
    path.AddLine(X(0.f), Y(14.9502f), X(0.f), Y(41.0498f));
    path.AddBezier(X(0.f), Y(41.0498f), X(0.f), Y(41.7496f), X(0.3727f), Y(42.3958f), X(0.9763f), Y(42.7453f));
    path.AddLine(X(0.9763f), Y(42.7453f), X(23.3389f), Y(55.6882f));
    path.AddBezier(X(23.3389f), Y(55.6882f), X(24.057f), Y(56.1039f), X(24.943f), Y(56.1039f), X(25.6611f), Y(55.6882f));
    path.AddLine(X(25.6611f), Y(55.6882f), X(48.0237f), Y(42.7453f));
    path.AddBezier(X(48.0237f), Y(42.7453f), X(48.6273f), Y(42.3958f), X(49.f), Y(41.7496f), X(49.f), Y(41.0498f));
    path.AddLine(X(49.f), Y(41.0498f), X(49.f), Y(14.9502f));
    path.AddBezier(X(49.f), Y(14.9502f), X(49.f), Y(14.2503f), X(48.6273f), Y(13.6041f), X(48.0226f), Y(13.2547f));
    path.CloseFigure();
    path.StartFigure();
    path.AddLine(X(46.6179f), Y(15.9964f), X(25.0302f), Y(53.4802f));
    path.AddBezier(X(25.0302f), Y(53.4802f), X(24.8842f), Y(53.7328f), X(24.4989f), Y(53.6296f), X(24.4989f), Y(53.337f));
    path.AddLine(X(24.4989f), Y(53.337f), X(24.4989f), Y(28.793f));
    path.AddBezier(X(24.4989f), Y(28.793f), X(24.4989f), Y(28.3026f), X(24.2375f), Y(27.849f), X(23.8134f), Y(27.6027f));
    path.AddLine(X(23.8134f), Y(27.6027f), X(2.6109f), Y(15.3312f));
    path.AddBezier(X(2.6109f), Y(15.3312f), X(2.359f), Y(15.1849f), X(2.4619f), Y(14.7987f), X(2.7537f), Y(14.7987f));
    path.AddLine(X(2.7537f), Y(14.7987f), X(45.9292f), Y(14.7987f));
    path.AddBezier(X(45.9292f), Y(14.7987f), X(46.5423f), Y(14.7987f), X(46.9255f), Y(15.4649f), X(46.619f), Y(15.9974f));
    path.CloseFigure();
    gph.FillPath(&ink, &path);
}
"""
if old_title not in text:
    raise SystemExit("title block missing")
text = text.replace(old_title, new_title, 1)

old_size = "    DrawRingGlyph(gph, cx, y, (r - 3.f) * 1.55f, kind);"
new_size = "    // Same pixel size for all four ring icons (美工: e.g. 22), tucked inside the ring.\n    DrawRingGlyph(gph, cx, y, (float)S(22), kind);"
if old_size not in text:
    raise SystemExit("glyph size call missing")
text = text.replace(old_size, new_size, 1)

p.write_text(text, encoding="utf-8")
print("patched title + ring size")

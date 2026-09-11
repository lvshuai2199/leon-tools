from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")
start = text.find("static void DrawRingGlyph(Graphics& gph, float cx, float cy, float size, int kind) {")
end = text.find("static void DrawRingItem(Graphics& gph, Font& num, float cx, float y, float r, double pct, int kind, bool known = true) {")
if start < 0 or end < 0:
    raise SystemExit(f"markers {start} {end}")
new = r'''static float RingGlyphX(float cx, float size, float x) {
    return cx - size * 0.5f + (3.7f + x * 0.17f) * (size / 16.f);
}
static float RingGlyphY(float cy, float size, float y) {
    return cy - size * 0.5f + (3.25f + y * 0.17f) * (size / 16.f);
}

static void AddRingHex(GraphicsPath& path, float cx, float cy, float size) {
    auto X = [&](float x) { return RingGlyphX(cx, size, x); };
    auto Y = [&](float y) { return RingGlyphY(cy, size, y); };
    PointF hex[6] = {
        {X(24.5f), Y(0.3f)}, {X(48.02f), Y(13.25f)}, {X(48.02f), Y(42.75f)},
        {X(24.5f), Y(55.69f)}, {X(0.98f), Y(42.75f)}, {X(0.98f), Y(13.25f)},
    };
    path.AddPolygon(hex, 6);
}

static void DrawRingGlyph(Graphics& gph, float cx, float cy, float size, int kind) {
    // Four ring icons: black #202022 hex hollow, same 16x16 frame (ui-review/icon-*.svg).
    // Ring stroke colors stay blue / purple / teal / amber.
    SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
    auto X = [&](float x) { return RingGlyphX(cx, size, x); };
    auto Y = [&](float y) { return RingGlyphY(cy, size, y); };
    GraphicsPath path(Gdiplus::FillModeAlternate);

    if (kind == 0) {
        // icon-auto.svg — cursor cutout in hex
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
        return;
    }

    AddRingHex(path, cx, cy, size);

    if (kind == 1) {
        // icon-models.svg — three bar cutouts
        PointF a[4] = {{X(11.f), Y(15.f)}, {X(38.f), Y(15.f)}, {X(35.f), Y(21.f)}, {X(14.f), Y(21.f)}};
        PointF b[4] = {{X(11.f), Y(25.f)}, {X(38.f), Y(25.f)}, {X(35.f), Y(31.f)}, {X(14.f), Y(31.f)}};
        PointF c[4] = {{X(11.f), Y(35.f)}, {X(38.f), Y(35.f)}, {X(35.f), Y(41.f)}, {X(14.f), Y(41.f)}};
        path.AddPolygon(a, 4);
        path.AddPolygon(b, 4);
        path.AddPolygon(c, 4);
    } else if (kind == 2) {
        // icon-api.svg — two nodes + bridge cutouts
        PointF n1[6] = {
            {X(8.f), Y(20.f)}, {X(17.f), Y(14.5f)}, {X(26.f), Y(20.f)},
            {X(26.f), Y(34.f)}, {X(17.f), Y(39.5f)}, {X(8.f), Y(34.f)},
        };
        PointF n2[6] = {
            {X(23.f), Y(20.f)}, {X(32.f), Y(14.5f)}, {X(41.f), Y(20.f)},
            {X(41.f), Y(34.f)}, {X(32.f), Y(39.5f)}, {X(23.f), Y(34.f)},
        };
        PointF bridge[4] = {
            {X(22.5f), Y(24.5f)}, {X(32.5f), Y(24.5f)}, {X(32.5f), Y(29.5f)}, {X(22.5f), Y(29.5f)},
        };
        path.AddPolygon(n1, 6);
        path.AddPolygon(n2, 6);
        path.AddPolygon(bridge, 4);
    } else if (kind == 3) {
        // icon-bot.svg — antenna, face, eyes, smile cutouts
        PointF ant[4] = {{X(22.f), Y(6.f)}, {X(27.f), Y(6.f)}, {X(27.f), Y(14.f)}, {X(22.f), Y(14.f)}};
        PointF body[4] = {{X(14.f), Y(16.f)}, {X(35.f), Y(16.f)}, {X(35.f), Y(43.f)}, {X(14.f), Y(43.f)}};
        path.AddPolygon(ant, 4);
        path.AddPolygon(body, 4);
        float er = 3.6f * 0.17f * (size / 16.f);
        path.AddEllipse(X(18.5f) - er, Y(26.6f) - er, er * 2.f, er * 2.f);
        path.AddEllipse(X(30.5f) - er, Y(26.6f) - er, er * 2.f, er * 2.f);
        GraphicsPath smile;
        smile.AddBezier(X(20.f), Y(35.5f), X(22.25f), Y(37.75f), X(26.75f), Y(37.75f), X(29.f), Y(35.5f));
        smile.AddLine(X(29.f), Y(35.5f), X(29.f), Y(38.f));
        smile.AddBezier(X(29.f), Y(38.f), X(26.75f), Y(40.25f), X(22.25f), Y(40.25f), X(20.f), Y(38.f));
        smile.CloseFigure();
        path.AddPath(&smile, FALSE);
    }

    gph.FillPath(&ink, &path);
}

'''
p.write_text(text[:start] + new + text[end:], encoding="utf-8")
print("DrawRingGlyph replaced")

from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")
old = """    if (kind == 0) {
        // icon-auto.svg: hex cursor, fill #2F6FED, viewBox 0 0 49 56
        SolidBrush br(Color(255, 0x2F, 0x6F, 0xED));
        const float vw = 49.f, vh = 56.f;
        const float sc = size / vh * 0.92f;
        const float bx = cx - vw * sc * 0.5f;
        const float by = cy - vh * sc * 0.5f;
        auto X = [&](float x) { return bx + x * sc; };
        auto Y = [&](float y) { return by + y * sc; };
        GraphicsPath path(Gdiplus::FillModeAlternate);
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
"""
new = """    if (kind == 0) {
        // icon-auto.svg: hex cursor #2F6FED, transparent cutout, tucked inside the ring
        SolidBrush br(Color(255, 0x2F, 0x6F, 0xED));
        const float vw = 49.f, vh = 56.f;
        // Keep clear padding between hex and the progress ring.
        const float sc = size / vh * 0.58f;
        const float bx = cx - vw * sc * 0.5f;
        const float by = cy - vh * sc * 0.5f;
        auto X = [&](float x) { return bx + x * sc; };
        auto Y = [&](float y) { return by + y * sc; };
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
        // Inner cutout (cursor) — reverse order for a clean even-odd hole
        path.StartFigure();
        path.AddLine(X(46.619f), Y(15.9974f), X(45.9292f), Y(14.7987f));
        path.AddLine(X(45.9292f), Y(14.7987f), X(2.7537f), Y(14.7987f));
        path.AddBezier(X(2.7537f), Y(14.7987f), X(2.4619f), Y(14.7987f), X(2.359f), Y(15.1849f), X(2.6109f), Y(15.3312f));
        path.AddLine(X(2.6109f), Y(15.3312f), X(23.8134f), Y(27.6027f));
        path.AddBezier(X(23.8134f), Y(27.6027f), X(24.2375f), Y(27.849f), X(24.4989f), Y(28.3026f), X(24.4989f), Y(28.793f));
        path.AddLine(X(24.4989f), Y(28.793f), X(24.4989f), Y(53.337f));
        path.AddBezier(X(24.4989f), Y(53.337f), X(24.4989f), Y(53.6296f), X(24.8842f), Y(53.7328f), X(25.0302f), Y(53.4802f));
        path.AddLine(X(25.0302f), Y(53.4802f), X(46.6179f), Y(15.9964f));
        path.CloseFigure();
        gph.FillPath(&br, &path);
        return;
    }
"""
if old not in text:
    raise SystemExit("block missing")
p.write_text(text.replace(old, new, 1), encoding="utf-8")
print("ok")

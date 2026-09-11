from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")
start = text.find("    if (kind == 0) {")
end = text.find("    if (kind == 3) {", start)
if start < 0 or end < 0:
    raise SystemExit(f"markers {start} {end}")
new = """    if (kind == 0) {
        // icon-auto.svg: blue hex #2F6FED + white cursor cutout, tucked in ring
        SolidBrush blue(Color(255, 0x2F, 0x6F, 0xED));
        SolidBrush snow(Color(255, 0xF8, 0xF8, 0xFA));
        const float vw = 49.f, vh = 56.f;
        const float sc = size / vh * 0.62f;
        const float bx = cx - vw * sc * 0.5f;
        const float by = cy - vh * sc * 0.5f;
        auto X = [&](float x) { return bx + x * sc; };
        auto Y = [&](float y) { return by + y * sc; };
        GraphicsPath outer;
        outer.StartFigure();
        outer.AddLine(X(48.0226f), Y(13.2547f), X(25.6601f), Y(0.3118f));
        outer.AddBezier(X(25.6601f), Y(0.3118f), X(24.942f), Y(-0.1039f), X(24.0559f), Y(-0.1039f), X(23.3378f), Y(0.3118f));
        outer.AddLine(X(23.3378f), Y(0.3118f), X(0.9763f), Y(13.2547f));
        outer.AddBezier(X(0.9763f), Y(13.2547f), X(0.3727f), Y(13.6041f), X(0.f), Y(14.2503f), X(0.f), Y(14.9502f));
        outer.AddLine(X(0.f), Y(14.9502f), X(0.f), Y(41.0498f));
        outer.AddBezier(X(0.f), Y(41.0498f), X(0.f), Y(41.7496f), X(0.3727f), Y(42.3958f), X(0.9763f), Y(42.7453f));
        outer.AddLine(X(0.9763f), Y(42.7453f), X(23.3389f), Y(55.6882f));
        outer.AddBezier(X(23.3389f), Y(55.6882f), X(24.057f), Y(56.1039f), X(24.943f), Y(56.1039f), X(25.6611f), Y(55.6882f));
        outer.AddLine(X(25.6611f), Y(55.6882f), X(48.0237f), Y(42.7453f));
        outer.AddBezier(X(48.0237f), Y(42.7453f), X(48.6273f), Y(42.3958f), X(49.f), Y(41.7496f), X(49.f), Y(41.0498f));
        outer.AddLine(X(49.f), Y(41.0498f), X(49.f), Y(14.9502f));
        outer.AddBezier(X(49.f), Y(14.9502f), X(49.f), Y(14.2503f), X(48.6273f), Y(13.6041f), X(48.0226f), Y(13.2547f));
        outer.CloseFigure();
        gph.FillPath(&blue, &outer);
        GraphicsPath cut;
        cut.StartFigure();
        cut.AddLine(X(46.6179f), Y(15.9964f), X(25.0302f), Y(53.4802f));
        cut.AddBezier(X(25.0302f), Y(53.4802f), X(24.8842f), Y(53.7328f), X(24.4989f), Y(53.6296f), X(24.4989f), Y(53.337f));
        cut.AddLine(X(24.4989f), Y(53.337f), X(24.4989f), Y(28.793f));
        cut.AddBezier(X(24.4989f), Y(28.793f), X(24.4989f), Y(28.3026f), X(24.2375f), Y(27.849f), X(23.8134f), Y(27.6027f));
        cut.AddLine(X(23.8134f), Y(27.6027f), X(2.6109f), Y(15.3312f));
        cut.AddBezier(X(2.6109f), Y(15.3312f), X(2.359f), Y(15.1849f), X(2.4619f), Y(14.7987f), X(2.7537f), Y(14.7987f));
        cut.AddLine(X(2.7537f), Y(14.7987f), X(45.9292f), Y(14.7987f));
        cut.AddBezier(X(45.9292f), Y(14.7987f), X(46.5423f), Y(14.7987f), X(46.9255f), Y(15.4649f), X(46.619f), Y(15.9974f));
        cut.CloseFigure();
        gph.FillPath(&snow, &cut);
        return;
    }
"""
p.write_text(text[:start] + new + text[end:], encoding="utf-8")
print("patched")

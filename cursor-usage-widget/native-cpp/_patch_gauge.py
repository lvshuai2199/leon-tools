from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")
old = """static void DrawTitleMark(Graphics& gph, float x, float y) {
    const float s = 16.f / 32.f;
    SolidBrush br(Color(255, 0x20, 0x20, 0x22));
    PointF a1[4] = {
        {x + 16.f * s, y + 4.6f * s},
        {x + 23.4f * s, y + 8.8f * s},
        {x + 16.2f * s, y + 12.8f * s},
        {x + 8.6f * s, y + 8.4f * s},
    };
    PointF a2[4] = {
        {x + 8.6f * s, y + 8.4f * s},
        {x + 16.2f * s, y + 12.8f * s},
        {x + 14.6f * s, y + 25.2f * s},
        {x + 6.6f * s, y + 19.6f * s},
    };
    PointF a3[4] = {
        {x + 23.4f * s, y + 8.8f * s},
        {x + 16.2f * s, y + 12.8f * s},
        {x + 14.6f * s, y + 25.2f * s},
        {x + 26.8f * s, y + 18.2f * s},
    };
    GraphicsPath path;
    path.AddPolygon(a1, 4);
    path.AddPolygon(a2, 4);
    path.AddPolygon(a3, 4);
    gph.FillPath(&br, &path);
}
"""
new = """static void DrawTitleMark(Graphics& gph, float x, float y) {
    // usage-badge.svg gauge: viewBox 0 0 16 16, stroke/fill #202022
    const float s = 1.f;
    Color ink(255, 0x20, 0x20, 0x22);
    Pen pen(ink, 1.5f * s);
    pen.SetStartCap(LineCapRound);
    pen.SetEndCap(LineCapRound);
    pen.SetLineJoin(LineJoinRound);
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
if old not in text:
    raise SystemExit("DrawTitleMark block not found")
p.write_text(text.replace(old, new, 1), encoding="utf-8")
print("ok")

from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")
text2 = text.replace("pen.SetStartCap(LineCapRound);", "pen.SetStartCap(Gdiplus::LineCapRound);")
text2 = text2.replace("pen.SetEndCap(LineCapRound);", "pen.SetEndCap(Gdiplus::LineCapRound);")
text2 = text2.replace("pen.SetLineJoin(LineJoinRound);", "pen.SetLineJoin(Gdiplus::LineJoinRound);")
if text2 == text:
    raise SystemExit("no replace")
p.write_text(text2, encoding="utf-8")
print("fixed")

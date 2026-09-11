from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")
old = "    LoadConfig();\n"
# find in wWinMain - need unique context
idx = text.find("int WINAPI wWinMain")
chunk = text[idx:]
if "LoadConfig();" not in chunk:
    raise SystemExit("no LoadConfig in WinMain")
# only first LoadConfig after wWinMain
pos = text.find("    LoadConfig();", idx)
# insert expand after it
insert_at = pos + len("    LoadConfig();\n")
addon = "    g.expanded = true; // TEMP for title screenshot\n"
if "TEMP for title screenshot" in text:
    print("already forced")
else:
    text = text[:insert_at] + addon + text[insert_at:]
    p.write_text(text, encoding="utf-8")
    print("forced expand")

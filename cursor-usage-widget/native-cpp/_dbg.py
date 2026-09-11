from pathlib import Path
import re
c=Path(r"D:\GitFiles\leon-tools\cursor-usage-widget\native-cpp\CursorUsage.cpp").read_text(encoding="utf-8").replace("\r\n","\n")
oldh="        if (cmd == 12) { g.dockRight = false; Place(h); SaveConfig(); }\n        if (cmd == 13) { g.dockRight = true; Place(h); SaveConfig(); }"
print("handlers", oldh in c)
m=re.search(r"AppendMenuW\(menu, MF_STRING, 12, L\"[^\"]*\"\);\n\s*AppendMenuW\(menu, MF_STRING, 13, L\"[^\"]*\"\);", c)
print("menu match", bool(m))
# simulate all prior replaces from patch file by exec-ing until handlers
# show save line escape
i=c.find('out << "dock="')
print("save", repr(c[i:i+90]))
# Try replace save with exact from file
old_save='    out << "dock=" << (g.dockRight ? "right" : "left") << "\\n";'
print("old_save in c", old_save in c)
# maybe the issue is double escaping in patch file for save line
print([hex(ord(ch)) for ch in c[i:i+90] if ord(ch)>127 or ch=='\\'])
# find backslash-n in save
j=c.find('dockRight ? "right"')
print(repr(c[j-30:j+60]))

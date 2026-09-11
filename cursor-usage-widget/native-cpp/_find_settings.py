from pathlib import Path
c = Path(r"D:\GitFiles\leon-tools\cursor-usage-widget\native-cpp\CursorUsage.cpp").read_text(encoding="utf-8")
keys = ["OpenSettings", "SettingsProc", "g_settings", "\u8bbe\u7f6e", "OpenManage", "ManageProc", "cmd == 18", "cmd == 19", "cmd == 20", "AppendMenuW(menu"]
for k in keys:
    start = 0
    hits = 0
    while hits < 8:
        j = c.find(k, start)
        if j < 0:
            break
        line = c.count("\n", 0, j) + 1
        snip = c[j:j+70].replace("\n", " ")
        print("%d:%s: %s" % (line, k[:20], snip))
        start = j + len(k)
        hits += 1

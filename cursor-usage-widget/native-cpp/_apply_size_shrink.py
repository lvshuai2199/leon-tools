# -*- coding: utf-8 -*-
"""Shrink CursorUsage strip: rings x0.88, gap=8, tighter collapsed pads; settings 300/12.
Does NOT modify close/destroy/WM_ACTIVATE/owner/SettingsApplyMain crash-fix paths.
"""
from pathlib import Path
import re
import sys

path = Path(sys.argv[1] if len(sys.argv) > 1 else
            r"D:\GitFiles\leon-tools\cursor-usage-widget\native-cpp\CursorUsage.cpp")
raw = path.read_text(encoding="utf-8")
nl = "\r\n" if "\r\n" in raw else "\n"
c = raw.replace("\r\n", "\n")
orig = c

def repl(old, new, label):
    global c
    if old not in c:
        raise SystemExit("FAIL missing: " + label + "\n" + old[:200])
    c = c.replace(old, new, 1)
    print("ok", label)

# --- 1) ring scale ---
if "1.13f * 0.88f" in c:
    print("skip kRingScale")
else:
    repl(
        "static const float kRingScale = 1.13f;",
        "static const float kRingScale = 1.13f * 0.88f; // product: rings x0.88",
        "kRingScale",
    )

# --- 2) constants after RingR ---
if "static const int kRingGap = 8;" not in c:
    m = re.search(r"static float RingR\(\) \{ return [^;]+; \}", c)
    if not m:
        raise SystemExit("FAIL RingR")
    repl(
        m.group(0),
        m.group(0)
        + "\nstatic const int kRingGap = 8; // top-dock H + side-dock V"
        + "\nstatic const int kCollapsedPad = 10; // ~1/2-2/3 prior extra air",
        "kRingGap/kCollapsedPad",
    )

# --- 3) StripW ---
# Top dock: gap S(10) -> kRingGap; pad S(12) -> kCollapsedPad
idx = c.find("static int StripW()")
idx2 = c.find("static int StripH()")
if idx < 0 or idx2 < 0:
    raise SystemExit("FAIL StripW/H")
head, mid, tail = c[:idx], c[idx:idx2], c[idx2:]
mid, n = re.subn(r"int gap = S\(10\);", "int gap = kRingGap;", mid, count=1)
print("StripW gap", n)
mid, n = re.subn(r"return S\(12\) \* 2 \+", "return kCollapsedPad * 2 +", mid, count=1)
print("StripW pad", n)
# side return
old_side_ret = "return S((int)(BASE_STRIP_W * kRingScale + 0.5));"
new_side_ret = (
    "{\n"
    "        float rSide = RingR();\n"
    "        int side = kCollapsedPad;\n"
    "        int ww = (int)(2.f * rSide + (float)(side * 2) + 0.5f);\n"
    "        int minW = S(44);\n"
    "        return ww < minW ? minW : ww;\n"
    "    }"
)
if old_side_ret in mid:
    mid = mid.replace(old_side_ret, new_side_ret, 1)
    print("ok side StripW")
else:
    # alternate formatting
    alt = "return S((int)(BASE_STRIP_W * kRingScale + 0.5));"
    if alt in mid:
        mid = mid.replace(alt, new_side_ret, 1)
        print("ok side StripW alt")
    else:
        print("WARN side StripW ret; mid=\n", mid)
c = head + mid + tail

# --- 4) StripH ---
idx = c.find("static int StripH()")
# end at next static int function
m = re.search(r"\nstatic int \w+\(\)", c[idx + 10 :])
idx2 = idx + 10 + m.start() if m else -1
head, mid, tail = c[:idx], c[idx:idx2], c[idx2:]
mid2 = mid.replace(
    "return S(8) * 2 + (int)(2.f * r + (float)S(11) + (float)S(10) + 0.5f);",
    "return S(6) * 2 + (int)(2.f * r + (float)S(11) + (float)S(9) + 0.5f);",
    1,
)
old_h = """    float r = RingR();
    float y0 = (float)S(30) * kRingScale;
    float step = (float)S(64) * kRingScale;
    int n = CollapsedRingCount();
    float lastY = y0 + step * (float)(n - 1);
    float topPad = y0 - r;
    if (topPad < (float)S(8)) topPad = (float)S(8);
    float contentBottom = lastY + r + (float)S(11) + (float)S(10);
    return (int)(contentBottom + topPad + 0.5f);
"""
new_h = """    float r = RingR();
    float topPad = (float)kCollapsedPad;
    float y0 = r + topPad;
    float step = 2.f * r + (float)kRingGap;
    int n = CollapsedRingCount();
    float lastY = y0 + step * (float)(n - 1);
    float contentBottom = lastY + r + (float)S(11) + (float)S(9);
    return (int)(contentBottom + topPad + 0.5f);
"""
# tolerate return (int) vs (int)
if old_h not in mid2:
    old_h = old_h.replace("return (int)(contentBottom + topPad + 0.5f);",
                          "return (int)(contentBottom + topPad + 0.5f);")
if old_h in mid2:
    mid2 = mid2.replace(old_h, new_h, 1)
    print("ok StripH side")
else:
    # try with return (int)(contentBottom + topPad + 0.5f);
    variants = [
        old_h,
        old_h.replace("return (int)(contentBottom + topPad + 0.5f);",
                      "return (int)(contentBottom + topPad + 0.5f);"),
        """    float r = RingR();
    float y0 = (float)S(30) * kRingScale;
    float step = (float)S(64) * kRingScale;
    int n = CollapsedRingCount();
    float lastY = y0 + step * (float)(n - 1);
    float topPad = y0 - r;
    if (topPad < (float)S(8)) topPad = (float)S(8);
    float contentBottom = lastY + r + (float)S(11) + (float)S(10);
    return (int)(contentBottom + topPad + 0.5f);
""",
    ]
    # from dump: return (int)(contentBottom + topPad + 0.5f);  -- actually dump had:
    # return (int)(contentBottom + topPad + 0.5f);
    dump_h = """    float r = RingR();
    float y0 = (float)S(30) * kRingScale;
    float step = (float)S(64) * kRingScale;
    int n = CollapsedRingCount();
    float lastY = y0 + step * (float)(n - 1);
    float topPad = y0 - r;
    if (topPad < (float)S(8)) topPad = (float)S(8);
    float contentBottom = lastY + r + (float)S(11) + (float)S(10);
    return (int)(contentBottom + topPad + 0.5f);
"""
    # dump file had: return (int)(contentBottom + topPad + 0.5f); with float cast style
    # From earlier read: `return (int)(contentBottom + topPad + 0.5f);` NO
    # Actual dump lines 689-697:
    dump_h = """    float r = RingR();
    float y0 = (float)S(30) * kRingScale;
    float step = (float)S(64) * kRingScale;
    int n = CollapsedRingCount();
    float lastY = y0 + step * (float)(n - 1);
    float topPad = y0 - r;
    if (topPad < (float)S(8)) topPad = (float)S(8);
    float contentBottom = lastY + r + (float)S(11) + (float)S(10);
    return (int)(contentBottom + topPad + 0.5f);
"""
    # Looking at dump again: `return (int)(contentBottom + topPad + 0.5f);` 
    # Wait the dump said: `return (int)(contentBottom + topPad + 0.5f);` 
    # Actual: `return (int)(contentBottom + topPad + 0.5f);`
    # From size_dump: `return (int)(contentBottom + topPad + 0.5f);`
    # 371 dump: `return (int)(contentBottom + topPad + 0.5f);` — let's use fuzzy regex
    mid3, n = re.subn(
        r"float r = RingR\(\);\n"
        r"    float y0 = \(float\)S\(30\) \* kRingScale;\n"
        r"    float step = \(float\)S\(64\) \* kRingScale;\n"
        r"    int n = CollapsedRingCount\(\);\n"
        r"    float lastY = y0 \+ step \* \(float\)\(n - 1\);\n"
        r"    float topPad = y0 - r;\n"
        r"    if \(topPad < \(float\)S\(8\)\) topPad = \(float\)S\(8\);\n"
        r"    float contentBottom = lastY \+ r \+ \(float\)S\(11\) \+ \(float\)S\(10\);\n"
        r"    return \(int\)\(contentBottom \+ topPad \+ 0\.5f\);",
        new_h.strip("\n"),
        mid2,
        count=1,
    )
    if n:
        mid2 = mid3
        print("ok StripH side regex")
    else:
        print("WARN StripH side; showing mid:\n", mid2[-800:])
c = head + mid2 + tail

# --- 5) collapsed paint ---
old_p = """            float cx = w * 0.5f;
            float y0 = (float)S(30) * kRingScale;
            float step = (float)S(64) * kRingScale;
"""
new_p = """            float cx = w * 0.5f;
            float topPad = (float)kCollapsedPad;
            float y0 = r + topPad;
            float step = 2.f * r + (float)kRingGap;
"""
if old_p in c:
    repl(old_p, new_p, "collapsed side paint")
else:
    print("WARN collapsed side paint")

for old, new, lab in [
    ("float gap = (float)S(10);", "float gap = (float)kRingGap;", "paint gap"),
    ("float padL = (float)S(12);", "float padL = (float)kCollapsedPad;", "paint padL"),
    ("float cy = (float)S(8) + r;", "float cy = (float)S(6) + r;", "paint cy"),
]:
    if old in c:
        c = c.replace(old, new, 1)
        print("ok", lab)
    else:
        print("skip", lab)

# --- 6) settings width + pad ---
n = 0
c, n1 = re.subn(r"\bkSettingsW\s*=\s*320\b", "kSettingsW = 300", c, count=1)
c, n2 = re.subn(r"\bkSettingsW\s*=\s*320\b", "kSettingsW = 300", c, count=1)
print("settings width", n1, n2)
if n1 + n2 == 0 and not re.search(r"kSettingsW?\s*=\s*300", c):
    print("WARN settings width")

for fn in ("SettingsContentH", "SettingsLayout", "PaintSettings", "OpenSettings"):
    m = re.search(rf"static (?:int|void) {fn}\(", c)
    if not m:
        continue
    # find opening brace of function
    brace = c.find("{", m.end())
    depth = 0
    j = brace
    while j < len(c):
        if c[j] == "{":
            depth += 1
        elif c[j] == "}":
            depth -= 1
            if depth == 0:
                j += 1
                break
        j += 1
    body = c[m.start():j]
    body2, n = re.subn(r"const int pad = 16;", "const int pad = 12;", body)
    if n:
        c = c[: m.start()] + body2 + c[j:]
        print("ok pad", fn, n)

# Icon ratio untouched (DrawRingItem keeps ~0.62-0.65 inner).
print("skip icon ratio (keep)")

# Title/bot scale: if DrawTitleMark uses fixed S(14)/S(16), multiply by kRingScale once.
m = re.search(r"static void DrawTitleMark\(Graphics& gph, float x, float y\) \{.*?\n\}", c, re.S)
if m:
    body = m.group(0)
    body2, n = re.subn(
        r"float (sz|size|s) = \(float\)S\((\d+)\);",
        r"float \1 = (float)S(\2) * kRingScale;",
        body,
        count=2,
    )
    if n:
        c = c[: m.start()] + body2 + c[m.end() :]
        print("ok title mark scale", n)
    else:
        print("skip title mark")

if c == orig:
    raise SystemExit("FAIL no changes")

# Confirm we did not edit crash-fix function bodies by checking key markers still present
for marker in ("WM_ACTIVATE", "SettingsApplyMain", "WM_CLOSE"):
    # presence optional; if present counts should stay same
    if marker in orig and orig.count(marker) != c.count(marker):
        print("NOTE marker count changed", marker, orig.count(marker), c.count(marker))

path.write_text(c.replace("\n", nl), encoding="utf-8")
print("WROTE", path)
print("DONE delta", len(c) - len(orig))

from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8-sig")

old = """    ses.sub = jp.str("sub");
    s.email = ses.email;
    s.membership = ses.membership;
    if (JwtExpired(ses.access)) RefreshToken(ses);
"""
new = """    ses.sub = jp.str("sub");
    s.email = ses.email;
    // Cached stripe membership is only applied after a real usage payload lands.
    if (JwtExpired(ses.access)) RefreshToken(ses);
"""
if old not in text:
    raise SystemExit("anchor membership missing")
text = text.replace(old, new, 1)

old2 = """    J sum = call(L"cursor.com", L"/api/usage-summary", "GET", "", true);
    ApplyPlan(s, sum);
    J period = call(L"cursor.com", L"/api/dashboard/get-current-period-usage", "POST", "{}", true);
    ApplyPlan(s, period);
    J rpc = call(L"api2.cursor.sh", L"/aiserver.v1.DashboardService/GetCurrentPeriodUsage", "POST", "{}", false);
    ApplyPlan(s, rpc);
    J sand = call(L"api2.cursor.sh", L"/aiserver.v1.DashboardService/GetSandUsageStatus", "POST", "{}", false);
    ApplySandUsage(s, sand);
    J agg = call(L"cursor.com", L"/api/dashboard/get-aggregated-usage-events", "POST", dayBody, true);
    SumAgg(s, agg);
    s.ok = true;
    return s;
}
"""
new2 = """    bool gotUsage = false;
    auto takePlan = [&](const J& j) {
        if (j.t != J::OBJ || j.o.empty()) return;
        ApplyPlan(s, j);
        if (WalkPlan(j) || j.get("membershipType") || j.get("displayMessage")
            || j.get("namedModelSelectedDisplayMessage") || j.get("autoModelSelectedDisplayMessage"))
            gotUsage = true;
    };
    J sum = call(L"cursor.com", L"/api/usage-summary", "GET", "", true);
    takePlan(sum);
    J period = call(L"cursor.com", L"/api/dashboard/get-current-period-usage", "POST", "{}", true);
    takePlan(period);
    J rpc = call(L"api2.cursor.sh", L"/aiserver.v1.DashboardService/GetCurrentPeriodUsage", "POST", "{}", false);
    takePlan(rpc);
    J sand = call(L"api2.cursor.sh", L"/aiserver.v1.DashboardService/GetSandUsageStatus", "POST", "{}", false);
    ApplySandUsage(s, sand);
    J agg = call(L"cursor.com", L"/api/dashboard/get-aggregated-usage-events", "POST", dayBody, true);
    if (agg.t == J::OBJ && !agg.o.empty()) SumAgg(s, agg);

    if (!gotUsage) {
        s.ok = false;
        s.membership.clear();
        s.autoP = s.api = s.total = s.botP = 0;
        s.botKnown = false;
        s.tin = s.tout = s.tcache = s.twrite = 0;
        s.models.clear();
        s.topModel.clear();
        if (s.error.empty()) s.error = "未读到用量";
        return s;
    }
    if (s.membership.empty() && !ses.membership.empty())
        s.membership = ses.membership;
    s.ok = true;
    return s;
}
"""
if old2 not in text:
    raise SystemExit("anchor fetch missing")
text = text.replace(old2, new2, 1)

# Gate token block on snap.ok — find the unique start after meters
marker = "        int viewY = y + S(8);\n        int ty = viewY;\n"
idx = text.find(marker)
if idx < 0:
    raise SystemExit("viewY marker missing")
# Find end of token/footer block: the foot DrawString then closing braces
end_marker = "        gph.DrawString(foot, -1, &sm, PointF((float)pad, (float)ty), &muted);\n\n    }\n    }"
end = text.find(end_marker, idx)
if end < 0:
    raise SystemExit("foot end missing")
end_block = end + len("        gph.DrawString(foot, -1, &sm, PointF((float)pad, (float)ty), &muted);\n")
token_block = text[idx:end_block]
wrapped = "        if (g.snap.ok) {\n" + token_block + "        } else {\n            std::wstring err = g.snap.error.empty() ? L\"未读到用量\" : Utf8ToWide(g.snap.error);\n            gph.DrawString(err.c_str(), -1, &sm, PointF((float)pad, (float)(y + S(28))), &muted);\n        }\n"
text = text[:idx] + wrapped + text[end_block:]

p.write_text(text, encoding="utf-8")
print("patched empty state")

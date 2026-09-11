from pathlib import Path
p = Path(r"D:/GitFiles/leon-tools/cursor-usage-widget/native-cpp/CursorUsage.cpp")
text = p.read_text(encoding="utf-8")
old = """    std::string mem = payload.str(\"membershipType\");
    if (!mem.empty()) s.membership = mem;
}
"""
new = r'''    std::string mem = payload.str("membershipType");
    if (!mem.empty()) s.membership = mem;
}

static const J* FindKeyed(const J& node, const char* a, const char* b, const J** holder) {
    if (node.t != J::OBJ) return nullptr;
    if (const J* v = node.get(a)) { *holder = &node; return v; }
    if (const J* v = node.get(b)) { *holder = &node; return v; }
    for (auto& kv : node.o) {
        if (const J* f = FindKeyed(kv.second, a, b, holder)) return f;
    }
    return nullptr;
}

static bool FlagOn(const J& node, const char* k) {
    const J* v = node.get(k);
    if (!v) return false;
    if (v->t == J::BOOL) return v->b;
    if (v->t == J::NUM) return v->n != 0;
    return false;
}

// Grok Bot weekly usage: DashboardService.GetSandUsageStatus.usagePercent
static void ApplySandUsage(Snapshot& s, const J& payload) {
    const J* holder = nullptr;
    const J* field = FindKeyed(payload, "usagePercent", "usage_percent", &holder);
    if (!field || !holder) return;
    if (FlagOn(*holder, "usesPooledEnterpriseAllowance") || FlagOn(*holder, "uses_pooled_enterprise_allowance"))
        return;
    if (field->t != J::NUM && field->t != J::STR) return;
    double v = 0;
    if (field->t == J::NUM) v = field->n;
    else {
        try { v = std::stod(field->s); } catch (...) { return; }
    }
    if (!std::isfinite(v)) return;
    s.botP = (std::max)(0.0, v);
    s.botKnown = true;
}
'''
if old not in text:
    raise SystemExit("anchor1 missing")
text = text.replace(old, new, 1)
old2 = """    J rpc = call(L\"api2.cursor.sh\", L\"/aiserver.v1.DashboardService/GetCurrentPeriodUsage\", \"POST\", \"{}\", false);
    ApplyPlan(s, rpc);
"""
new2 = """    J rpc = call(L\"api2.cursor.sh\", L\"/aiserver.v1.DashboardService/GetCurrentPeriodUsage\", \"POST\", \"{}\", false);
    ApplyPlan(s, rpc);
    J sand = call(L\"api2.cursor.sh\", L\"/aiserver.v1.DashboardService/GetSandUsageStatus\", \"POST\", \"{}\", false);
    ApplySandUsage(s, sand);
"""
if old2 not in text:
    raise SystemExit("anchor2 missing")
text = text.replace(old2, new2, 1)
p.write_text(text, encoding="utf-8")
print("patched")

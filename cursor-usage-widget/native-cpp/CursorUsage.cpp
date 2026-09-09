#define _CRT_SECURE_NO_WARNINGS
#define NOMINMAX
#include <windows.h>
#include <windowsx.h>
#include <objidl.h>
#include <shellapi.h>
#include <winhttp.h>
#include <gdiplus.h>
#include <dwmapi.h>
#include <string>
#include <vector>
#include <map>
#include <cmath>
#include <cctype>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <sstream>
#include <algorithm>
#include <fstream>
#include <regex>

#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "winhttp.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "dwmapi.lib")
#pragma comment(lib, "advapi32.lib")

using Gdiplus::Graphics;
using Gdiplus::Pen;
using Gdiplus::SolidBrush;
using Gdiplus::Color;
using Gdiplus::Font;
using Gdiplus::FontFamily;
using Gdiplus::RectF;
using Gdiplus::PointF;
using Gdiplus::StringFormat;
using Gdiplus::StringAlignmentCenter;
using Gdiplus::StringAlignmentNear;
using Gdiplus::StringAlignmentFar;
using Gdiplus::SmoothingModeAntiAlias;
using Gdiplus::TextRenderingHintClearTypeGridFit;
using Gdiplus::GraphicsPath;
using Gdiplus::FillModeWinding;
using Gdiplus::REAL;
using Gdiplus::LinearGradientBrush;
using Gdiplus::PenAlignmentInset;
using Gdiplus::PixelOffsetModeHighQuality;
using Gdiplus::CompositingQualityHighQuality;

static const int BASE_STRIP_W = 64;
static const int BASE_STRIP_H = 220;
static const int BASE_PANEL_W = 276;
static const int BASE_PANEL_H = 380;
static const UINT WM_USAGE = WM_APP + 1;

static std::wstring Utf8ToWide(const std::string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), nullptr, 0);
    std::wstring w(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), &w[0], n);
    return w;
}
static std::string WideToUtf8(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), nullptr, 0, nullptr, nullptr);
    std::string s(n, 0);
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), &s[0], n, nullptr, nullptr);
    return s;
}

struct J {
    enum Type { NIL, NUM, STR, BOOL, OBJ, ARR } t = NIL;
    double n = 0;
    bool b = false;
    std::string s;
    std::map<std::string, J> o;
    std::vector<J> a;
    const J* get(const char* k) const {
        auto it = o.find(k);
        return it == o.end() ? nullptr : &it->second;
    }
    double num(const char* k, double d = 0) const {
        const J* v = get(k);
        if (!v) return d;
        if (v->t == NUM) return v->n;
        if (v->t == STR) { try { return std::stod(v->s); } catch (...) { return d; } }
        return d;
    }
    std::string str(const char* k) const {
        const J* v = get(k);
        return v && v->t == STR ? v->s : std::string();
    }
};

struct JsonParser {
    const char* p;
    const char* e;
    JsonParser(const std::string& t) : p(t.c_str()), e(t.c_str() + t.size()) {}
    void sp() { while (p < e && (*p == ' ' || *p == '\n' || *p == '\r' || *p == '\t')) ++p; }
    bool eat(char c) { sp(); if (p < e && *p == c) { ++p; return true; } return false; }
    J parse() { sp(); return val(); }
    J val() {
        sp();
        if (p >= e) return J();
        if (*p == '{') return obj();
        if (*p == '[') return arr();
        if (*p == '"') { J j; j.t = J::STR; j.s = str(); return j; }
        if (*p == 't' || *p == 'f') return boolean();
        if (*p == 'n') { p += 4; return J(); }
        return number();
    }
    std::string str() {
        ++p;
        std::string o;
        while (p < e && *p != '"') {
            if (*p == '\\' && p + 1 < e) {
                ++p;
                char c = *p++;
                if (c == 'n') o += '\n';
                else if (c == 'u' && p + 4 <= e) {
                    unsigned v = 0;
                    sscanf(p, "%4x", &v);
                    p += 4;
                    if (v < 0x80) o += (char)v;
                    else if (v < 0x800) { o += (char)(0xC0 | (v >> 6)); o += (char)(0x80 | (v & 0x3F)); }
                    else { o += (char)(0xE0 | (v >> 12)); o += (char)(0x80 | ((v >> 6) & 0x3F)); o += (char)(0x80 | (v & 0x3F)); }
                } else o += c;
            } else o += *p++;
        }
        if (p < e) ++p;
        return o;
    }
    J number() {
        char* end = nullptr;
        J j; j.t = J::NUM; j.n = strtod(p, &end);
        if (end) p = end;
        return j;
    }
    J boolean() {
        J j; j.t = J::BOOL;
        if (*p == 't') { p += 4; j.b = true; } else { p += 5; j.b = false; }
        return j;
    }
    J obj() {
        J j; j.t = J::OBJ; ++p;
        sp();
        if (eat('}')) return j;
        for (;;) {
            sp();
            if (*p != '"') break;
            std::string k = str();
            eat(':');
            j.o[k] = val();
            if (eat('}')) break;
            eat(',');
        }
        return j;
    }
    J arr() {
        J j; j.t = J::ARR; ++p;
        sp();
        if (eat(']')) return j;
        for (;;) {
            j.a.push_back(val());
            if (eat(']')) break;
            eat(',');
        }
        return j;
    }
};

static std::string B64Url(const std::string& in) {
    std::string s = in;
    std::replace(s.begin(), s.end(), '-', '+');
    std::replace(s.begin(), s.end(), '_', '/');
    while (s.size() % 4) s += '=';
    static const char* tbl = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    int val = 0, valb = -8;
    for (unsigned char c : s) {
        if (c == '=') break;
        const char* pos = strchr(tbl, c);
        if (!pos) continue;
        val = (val << 6) + (int)(pos - tbl);
        valb += 6;
        if (valb >= 0) {
            out.push_back(char((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return out;
}

// ---- sqlite via winsqlite3.dll ----
typedef struct sqlite3 sqlite3;
typedef struct sqlite3_stmt sqlite3_stmt;
typedef int (*sqlite3_open_v2_t)(const char*, sqlite3**, int, const char*);
typedef int (*sqlite3_close_t)(sqlite3*);
typedef int (*sqlite3_prepare_v2_t)(sqlite3*, const char*, int, sqlite3_stmt**, const char**);
typedef int (*sqlite3_step_t)(sqlite3_stmt*);
typedef const unsigned char* (*sqlite3_column_text_t)(sqlite3_stmt*, int);
typedef int (*sqlite3_finalize_t)(sqlite3_stmt*);

static std::map<std::string, std::string> ReadAuth() {
    std::map<std::string, std::string> out;
    wchar_t appdata[MAX_PATH];
    if (!GetEnvironmentVariableW(L"APPDATA", appdata, MAX_PATH)) return out;
    std::wstring path = std::wstring(appdata) + L"\\Cursor\\User\\globalStorage\\state.vscdb";
    if (GetFileAttributesW(path.c_str()) == INVALID_FILE_ATTRIBUTES) return out;
    HMODULE m = LoadLibraryW(L"winsqlite3.dll");
    if (!m) return out;
    auto open = (sqlite3_open_v2_t)GetProcAddress(m, "sqlite3_open_v2");
    auto close = (sqlite3_close_t)GetProcAddress(m, "sqlite3_close");
    auto prep = (sqlite3_prepare_v2_t)GetProcAddress(m, "sqlite3_prepare_v2");
    auto step = (sqlite3_step_t)GetProcAddress(m, "sqlite3_step");
    auto text = (sqlite3_column_text_t)GetProcAddress(m, "sqlite3_column_text");
    auto fin = (sqlite3_finalize_t)GetProcAddress(m, "sqlite3_finalize");
    if (!open || !close || !prep || !step || !text || !fin) { FreeLibrary(m); return out; }
    std::string uri = "file:///" + WideToUtf8(path);
    for (auto& c : uri) if (c == '\\') c = '/';
    uri += "?mode=ro";
    sqlite3* db = nullptr;
    if (open(uri.c_str(), &db, 0x00000001 | 0x00000040, nullptr) != 0 || !db) { FreeLibrary(m); return out; }
    sqlite3_stmt* st = nullptr;
    const char* sql = "SELECT key, value FROM ItemTable WHERE key LIKE 'cursorAuth/%'";
    if (prep(db, sql, -1, &st, nullptr) == 0) {
        while (step(st) == 100) {
            const unsigned char* k = text(st, 0);
            const unsigned char* v = text(st, 1);
            if (k) out[(const char*)k] = v ? (const char*)v : "";
        }
        fin(st);
    }
    close(db);
    FreeLibrary(m);
    return out;
}

struct Session {
    std::string access, refresh, sub, email, membership;
};

static J JwtPayload(const std::string& jwt) {
    auto a = jwt.find('.');
    auto b = jwt.find('.', a + 1);
    if (a == std::string::npos || b == std::string::npos) return J();
    std::string json = B64Url(jwt.substr(a + 1, b - a - 1));
    return JsonParser(json).parse();
}

static bool JwtExpired(const std::string& jwt) {
    J p = JwtPayload(jwt);
    long long exp = (long long)p.num("exp");
    return exp <= (long long)time(nullptr) + 60;
}

static std::string Http(const std::wstring& host, const std::wstring& path, const std::string& method,
                        const std::string& body, const std::map<std::wstring, std::wstring>& headers) {
    HINTERNET ses = WinHttpOpen(L"CursorUsage/1.0", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
                                WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!ses) return "";
    WinHttpSetTimeouts(ses, 8000, 8000, 15000, 15000);
    HINTERNET con = WinHttpConnect(ses, host.c_str(), INTERNET_DEFAULT_HTTPS_PORT, 0);
    if (!con) { WinHttpCloseHandle(ses); return ""; }
    std::wstring wmethod = Utf8ToWide(method);
    DWORD flags = WINHTTP_FLAG_SECURE;
    HINTERNET req = WinHttpOpenRequest(con, wmethod.c_str(), path.c_str(), nullptr,
                                       WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, flags);
    if (!req) { WinHttpCloseHandle(con); WinHttpCloseHandle(ses); return ""; }
    std::wstring hdr;
    for (auto& kv : headers) hdr += kv.first + L": " + kv.second + L"\r\n";
    BOOL ok = WinHttpSendRequest(req, hdr.empty() ? WINHTTP_NO_ADDITIONAL_HEADERS : hdr.c_str(),
                                 hdr.empty() ? 0 : -1,
                                 body.empty() ? WINHTTP_NO_REQUEST_DATA : (LPVOID)body.data(),
                                 (DWORD)body.size(), (DWORD)body.size(), 0);
    std::string resp;
    if (ok && WinHttpReceiveResponse(req, nullptr)) {
        DWORD avail = 0;
        while (WinHttpQueryDataAvailable(req, &avail) && avail) {
            std::string chunk(avail, 0);
            DWORD read = 0;
            WinHttpReadData(req, &chunk[0], avail, &read);
            chunk.resize(read);
            resp += chunk;
        }
    }
    WinHttpCloseHandle(req);
    WinHttpCloseHandle(con);
    WinHttpCloseHandle(ses);
    return resp;
}

static J CallJson(Session& ses, const std::wstring& host, const std::wstring& path,
                  const std::string& method, const std::string& body, bool cookie) {
    std::map<std::wstring, std::wstring> h;
    h[L"Accept"] = L"application/json";
    h[L"Content-Type"] = L"application/json";
    if (cookie) {
        h[L"Cookie"] = L"WorkosCursorSessionToken=" + Utf8ToWide(ses.sub + "::" + ses.access);
        h[L"Origin"] = L"https://cursor.com";
        h[L"Referer"] = L"https://cursor.com/dashboard";
    } else {
        h[L"Authorization"] = L"Bearer " + Utf8ToWide(ses.access);
        h[L"Connect-Protocol-Version"] = L"1";
    }
    std::string raw = Http(host, path, method, body, h);
    if (raw.empty() || raw[0] == '<') return J();
    return JsonParser(raw).parse();
}

static bool RefreshToken(Session& ses) {
    if (ses.refresh.empty()) return false;
    std::string body = std::string("{\"grant_type\":\"refresh_token\",\"client_id\":\"") +
                       "KbZUR41cY7W6zRSdpSUJ7I7mLYBKOCmB\",\"refresh_token\":\"" + ses.refresh + "\"}";
    J j = CallJson(ses, L"api2.cursor.sh", L"/oauth/token", "POST", body, false);
    std::string tok = j.str("access_token");
    if (tok.empty()) return false;
    ses.access = tok;
    J p = JwtPayload(tok);
    std::string sub = p.str("sub");
    if (!sub.empty()) ses.sub = sub;
    return true;
}

static const J* WalkPlan(const J& d) {
    if (d.t != J::OBJ) return nullptr;
    if (auto* p = d.get("planUsage")) if (p->t == J::OBJ) return p;
    if (auto* ind = d.get("individualUsage")) {
        if (auto* p = ind->get("plan")) if (p->t == J::OBJ) return p;
    }
    if (d.get("autoPercentUsed") || d.get("apiPercentUsed") || d.get("totalPercentUsed") || d.get("includedSpend"))
        return &d;
    for (auto& kv : d.o) {
        const J* f = WalkPlan(kv.second);
        if (f) return f;
    }
    return nullptr;
}

struct ModelUse {
    std::string name;
    long long tokens = 0;
};

struct Snapshot {
    bool ok = false;
    std::string error;
    std::string email, membership;
    double total = 0, autoP = 0, api = 0, botP = 0;
    bool botKnown = false;
    long long tin = 0, tout = 0, tcache = 0, twrite = 0;
    std::string topModel;
    std::vector<ModelUse> models;
    SYSTEMTIME fetched{};
    long long today() const { return tin + tout + tcache + twrite; }
};

static void MergePct(double& dest, double v) {
    if (v >= 0.05) dest = v;
    else if (dest < 0.05) dest = v;
}

static double PctInText(const std::string& msg) {
    std::smatch m;
    if (std::regex_search(msg, m, std::regex("([0-9]+(?:\\.[0-9]+)?)\\s*%")))
        return std::stod(m[1]);
    return 0;
}

static void TakeBotPct(Snapshot& s, const J& node) {
    if (node.t != J::OBJ) return;
    const char* keys[] = {
        "botPercentUsed", "composerPercentUsed", "agentPercentUsed",
        "backgroundAgentPercentUsed", "cloudAgentPercentUsed"
    };
    for (auto* k : keys) {
        if (node.get(k)) {
            MergePct(s.botP, node.num(k));
            s.botKnown = true;
        }
    }
    if (const J* bot = node.get("bot")) {
        if (bot->t == J::OBJ) {
            if (bot->get("percentUsed")) {
                MergePct(s.botP, bot->num("percentUsed"));
                s.botKnown = true;
            } else if (bot->get("percent")) {
                MergePct(s.botP, bot->num("percent"));
                s.botKnown = true;
            } else if (bot->num("limit") > 0) {
                MergePct(s.botP, (std::min)(100.0, bot->num("used") * 100.0 / bot->num("limit")));
                s.botKnown = true;
            }
        }
    }
    for (auto& kv : node.o) {
        const std::string& k = kv.first;
        bool botish = k.find("bot") != std::string::npos || k.find("Bot") != std::string::npos
            || k.find("agent") != std::string::npos || k.find("Agent") != std::string::npos
            || k.find("composer") != std::string::npos || k.find("Composer") != std::string::npos;
        if (!botish || k.find("Percent") == std::string::npos) continue;
        if (kv.second.t == J::NUM) {
            MergePct(s.botP, kv.second.n);
            s.botKnown = true;
        }
    }
}

static void ApplyPlan(Snapshot& s, const J& payload) {
    const J* plan = WalkPlan(payload);
    if (plan) {
        if (plan->get("autoPercentUsed")) MergePct(s.autoP, plan->num("autoPercentUsed"));
        if (plan->get("apiPercentUsed")) MergePct(s.api, plan->num("apiPercentUsed"));
        if (plan->get("totalPercentUsed")) MergePct(s.total, plan->num("totalPercentUsed"));
        TakeBotPct(s, *plan);
        double included = plan->num("includedSpend");
        if (included <= 0) included = plan->num("used");
        double limit = plan->num("limit");
        if (limit > 0 && included >= 0 && s.total < 0.05)
            s.total = (std::min)(100.0, included * 100.0 / limit);
    }
    std::string msg = payload.str("displayMessage");
    if (msg.empty()) msg = payload.str("autoModelSelectedDisplayMessage");
    double parsed = PctInText(msg);
    if (parsed > 0 && s.autoP < 0.05) s.autoP = parsed;
    double named = PctInText(payload.str("namedModelSelectedDisplayMessage"));
    if (named > 0) MergePct(s.api, named);
    TakeBotPct(s, payload);
    const char* botMsgs[] = {
        "botModelSelectedDisplayMessage", "agentModelSelectedDisplayMessage",
        "composerModelSelectedDisplayMessage"
    };
    for (auto* k : botMsgs) {
        double parsedBot = PctInText(payload.str(k));
        if (parsedBot > 0) {
            MergePct(s.botP, parsedBot);
            s.botKnown = true;
        }
    }
    std::string mem = payload.str("membershipType");
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

static long long RowTokens(const J& row) {
    double tot = row.num("totalTokens");
    if (tot > 0) return (long long)(tot + 0.5);
    return (long long)(row.num("inputTokens") + row.num("outputTokens")
        + row.num("cacheReadTokens") + row.num("cacheWriteTokens") + 0.5);
}

static void SumAgg(Snapshot& s, const J& d) {
    if (d.t != J::OBJ) return;
    s.models.clear();
    const J* ag = d.get("aggregations");
    long long tin = 0, tout = 0, tcache = 0, twrite = 0, all = 0;
    if (ag && ag->t == J::ARR) {
        for (auto& row : ag->a) {
            tin += (long long)row.num("inputTokens");
            tout += (long long)row.num("outputTokens");
            tcache += (long long)row.num("cacheReadTokens");
            twrite += (long long)row.num("cacheWriteTokens");
            long long tok = RowTokens(row);
            all += tok;
            ModelUse m;
            m.name = row.str("modelIntent");
            if (m.name.empty()) m.name = row.str("model");
            if (m.name.empty()) m.name = "unknown";
            m.tokens = tok;
            s.models.push_back(m);
        }
        std::sort(s.models.begin(), s.models.end(),
                  [](const ModelUse& a, const ModelUse& b) { return a.tokens > b.tokens; });
        if (s.models.size() > 12) s.models.resize(12);
        if (!s.models.empty()) s.topModel = s.models[0].name;
    }
    double topAll = d.num("totalTokens");
    if (topAll > 0) all = (long long)(topAll + 0.5);
    s.tin = tin ? tin : (long long)d.num("totalInputTokens");
    s.tout = tout ? tout : (long long)d.num("totalOutputTokens");
    s.tcache = tcache ? tcache : (long long)d.num("totalCacheReadTokens");
    s.twrite = twrite ? twrite : (long long)d.num("totalCacheWriteTokens");
    long long parts = s.tin + s.tout + s.tcache + s.twrite;
    if (all > parts) {
        // Usage 页 Daily total 含全部 token；缺口记入缓存，避免少计。
        s.tcache += (all - parts);
    }
}

static Snapshot FetchUsage() {
    Snapshot s;
    GetLocalTime(&s.fetched);
    auto items = ReadAuth();
    Session ses;
    ses.access = items["cursorAuth/accessToken"];
    ses.refresh = items["cursorAuth/refreshToken"];
    ses.email = items["cursorAuth/cachedEmail"];
    ses.membership = items["cursorAuth/stripeMembershipType"];
    if (ses.access.size() < 20) { s.error = "Cursor 未登录"; return s; }
    J jp = JwtPayload(ses.access);
    ses.sub = jp.str("sub");
    s.email = ses.email;
    s.membership = ses.membership;
    if (JwtExpired(ses.access)) RefreshToken(ses);

    auto call = [&](const std::wstring& host, const std::wstring& path, const std::string& method,
                    const std::string& body, bool cookie) -> J {
        J r = CallJson(ses, host, path, method, body, cookie);
        if (r.t == J::NIL && cookie) { RefreshToken(ses); r = CallJson(ses, host, path, method, body, cookie); }
        return r;
    };

    SYSTEMTIME st; GetLocalTime(&st);
    std::tm lt {};
    lt.tm_year = st.wYear - 1900; lt.tm_mon = st.wMonth - 1; lt.tm_mday = st.wDay;
    lt.tm_hour = 0; lt.tm_min = 0; lt.tm_sec = 0; lt.tm_isdst = -1;
    time_t localStart = mktime(&lt);
    long long startMs = (long long)localStart * 1000;
    long long endMs = startMs + 86400000LL;

    J me = call(L"cursor.com", L"/api/auth/me", "GET", "", true);
    if (!me.str("email").empty()) s.email = me.str("email");
    std::string uid;
    if (me.get("id")) {
        if (me.get("id")->t == J::NUM) { char b[64]; sprintf(b, "%.0f", me.get("id")->n); uid = b; }
        else uid = me.str("id");
    }

    char dayBody[384];
    if (!uid.empty() && uid.find_first_not_of("0123456789") == std::string::npos)
        sprintf(dayBody, "{\"startDate\":%lld,\"endDate\":%lld,\"userId\":%s}", startMs, endMs, uid.c_str());
    else if (!uid.empty())
        sprintf(dayBody, "{\"startDate\":%lld,\"endDate\":%lld,\"userId\":\"%s\"}", startMs, endMs, uid.c_str());
    else
        sprintf(dayBody, "{\"startDate\":%lld,\"endDate\":%lld}", startMs, endMs);

    J sum = call(L"cursor.com", L"/api/usage-summary", "GET", "", true);
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

static std::wstring FormatTok(long long n) {
    wchar_t b[32];
    if (n >= 1000000) swprintf(b, 32, L"%.1fM", n / 1000000.0);
    else if (n >= 1000) swprintf(b, 32, L"%.1fK", n / 1000.0);
    else swprintf(b, 32, L"%lld", n);
    std::wstring w = b;
    if (w.size() > 2 && w[w.size() - 3] == L'.' && w[w.size() - 2] == L'0')
        w.erase(w.size() - 3, 2);
    return w;
}

// ---- window ----
struct App {
    HWND hwnd = nullptr;
    bool expanded = false;
    bool dockRight = true;
    int y = -1;
    bool dragging = false;
    POINT press{};
    int pressY = 0;
    Snapshot snap;
    ULONG_PTR gdip = 0;
    bool tracking = false;
    DWORD holdUntil = 0;
    int scale = 80;
    bool showBot = false;
    int scrollY = 0;
    bool scrolling = false;
} g;

static int S(int v) { return (int)(v * g.scale / 100.0 + 0.5); }
static int StripW() { return S(BASE_STRIP_W); }
static int StripH() { return S(g.showBot ? BASE_STRIP_H + 64 : BASE_STRIP_H); }
static int PanelW() { return S(BASE_PANEL_W); }
static int MeterH() { return S(57); }
static int ExpandedChromeH() { return S(46) + MeterH() * (g.showBot ? 4 : 3) + S(8); }
static int TokenListH() {
    int n = (int)g.snap.models.size();
    if (n < 1 && !g.snap.topModel.empty()) n = 1;
    return S(26) + S(16) + n * S(16) + S(18);
}
static int ExpandedWantH() { return ExpandedChromeH() + TokenListH() + S(14); }
static int ExpandedH() {
    return ExpandedWantH();
}
static int TokenViewY() { return ExpandedChromeH(); }
static int TokenViewH() {
    int h = ExpandedH() - TokenViewY() - S(12);
    return h < S(36) ? S(36) : h;
}
static int MaxScroll() {
    return 0;
}
static int WinW() { return g.expanded ? PanelW() : StripW(); }
static int WinH() { return g.expanded ? ExpandedH() : StripH(); }

static std::wstring ConfigPath() {
    wchar_t ad[MAX_PATH];
    GetEnvironmentVariableW(L"APPDATA", ad, MAX_PATH);
    std::wstring dir = std::wstring(ad) + L"\\cursor-usage-widget";
    CreateDirectoryW(dir.c_str(), nullptr);
    return dir + L"\\ui.ini";
}

static void LoadConfig() {
    std::ifstream in(ConfigPath());
    if (!in) return;
    std::string line;
    while (std::getline(in, line)) {
        auto eq = line.find('=');
        if (eq == std::string::npos) continue;
        std::string k = line.substr(0, eq), v = line.substr(eq + 1);
        if (k == "scale") {
            int sc = atoi(v.c_str());
            if (sc < 60) sc = 60;
            if (sc > 160) sc = 160;
            g.scale = sc;
        }
        if (k == "dock") g.dockRight = v != "left";
        if (k == "y") g.y = atoi(v.c_str());
        if (k == "showBot") g.showBot = v == "1";
    }
}

static void SaveConfig() {
    std::ofstream out(ConfigPath());
    if (!out) return;
    out << "scale=" << g.scale << "\n";
    out << "dock=" << (g.dockRight ? "right" : "left") << "\n";
    out << "y=" << g.y << "\n";
    out << "showBot=" << (g.showBot ? "1" : "0") << "\n";
}

static const wchar_t* kRunKey = L"Software\\Microsoft\\Windows\\CurrentVersion\\Run";
static const wchar_t* kRunName = L"CursorUsageWidget";
static const wchar_t* kApprovedKey = L"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run";

static void WriteStartupApproved(bool on) {
    HKEY k = nullptr;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, kApprovedKey, 0, nullptr, 0, KEY_SET_VALUE, nullptr, &k, nullptr) != ERROR_SUCCESS)
        return;
    BYTE data[12] = {};
    data[0] = on ? 2 : 3;
    if (!on) {
        FILETIME ft;
        GetSystemTimeAsFileTime(&ft);
        memcpy(data + 4, &ft, sizeof(ft));
    }
    RegSetValueExW(k, kRunName, 0, REG_BINARY, data, sizeof(data));
    RegCloseKey(k);
}

static bool StartupApprovedOff() {
    HKEY k = nullptr;
    if (RegOpenKeyExW(HKEY_CURRENT_USER, kApprovedKey, 0, KEY_READ, &k) != ERROR_SUCCESS) return false;
    BYTE data[12] = {};
    DWORD sz = sizeof(data), type = 0;
    LONG r = RegQueryValueExW(k, kRunName, nullptr, &type, data, &sz);
    RegCloseKey(k);
    return r == ERROR_SUCCESS && type == REG_BINARY && sz >= 1 && data[0] == 3;
}


static std::wstring ExePath() {
    wchar_t buf[MAX_PATH];
    GetModuleFileNameW(nullptr, buf, MAX_PATH);
    return buf;
}

static bool AutoStartOn() {
    if (StartupApprovedOff()) return false;
    HKEY k = nullptr;
    if (RegOpenKeyExW(HKEY_CURRENT_USER, kRunKey, 0, KEY_READ, &k) != ERROR_SUCCESS) return false;
    wchar_t val[MAX_PATH * 2]{};
    DWORD sz = sizeof(val), type = 0;
    LONG r = RegQueryValueExW(k, kRunName, nullptr, &type, (LPBYTE)val, &sz);
    RegCloseKey(k);
    return r == ERROR_SUCCESS && type == REG_SZ && val[0] != 0;
}

static void SetAutoStart(bool on) {
    HKEY k = nullptr;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, kRunKey, 0, nullptr, 0, KEY_SET_VALUE, nullptr, &k, nullptr) != ERROR_SUCCESS)
        return;
    if (on) {
        std::wstring p = L"\"" + ExePath() + L"\"";
        RegSetValueExW(k, kRunName, 0, REG_SZ, (const BYTE*)p.c_str(), (DWORD)((p.size() + 1) * sizeof(wchar_t)));
    } else {
        RegDeleteValueW(k, kRunName);
    }
    RegCloseKey(k);
    WriteStartupApproved(on);
}

static bool CursorInWindow(HWND h) {
    POINT pt;
    GetCursorPos(&pt);
    RECT rc;
    GetWindowRect(h, &rc);
    InflateRect(&rc, 4, 4);
    return PtInRect(&rc, pt) != FALSE;
}

static int ClampI(int v, int lo, int hi) {
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}

static RECT Work() {
    RECT r; SystemParametersInfo(SPI_GETWORKAREA, 0, &r, 0); return r;
}

static void ApplyRegion(HWND h) {
    int w = WinW();
    int hh = WinH();
    SetWindowRgn(h, nullptr, FALSE);
}

static void Place(HWND h) {
    RECT wa = Work();
    int w = WinW();
    int hh = WinH();
    if (g.y < 0) g.y = wa.top + (wa.bottom - wa.top - hh) / 2;
    g.y = ClampI(g.y, (int)wa.top + 4, (int)wa.bottom - hh - 4);
    int x = g.dockRight ? wa.right - w : wa.left;
    SetWindowPos(h, HWND_TOPMOST, x, g.y, w, hh,
                 SWP_NOACTIVATE | SWP_NOCOPYBITS | SWP_SHOWWINDOW);
    ApplyRegion(h);
    RedrawWindow(h, nullptr, nullptr, RDW_INVALIDATE | RDW_UPDATENOW | RDW_NOERASE);
}

static void DragMove(HWND h) {
    RECT wa = Work();
    int w = WinW();
    int hh = WinH();
    g.y = ClampI(g.y, (int)wa.top + 4, (int)wa.bottom - hh - 4);
    int x = g.dockRight ? wa.right - w : wa.left;
    SetWindowPos(h, nullptr, x, g.y, 0, 0,
                 SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_NOREDRAW);
}

struct ACCENTPOLICY { int s, f; DWORD c; int a; };
struct WINCOMPDATA { int attr; PVOID data; ULONG size; };
typedef BOOL(WINAPI* SetWindowCompositionAttributeFn)(HWND, WINCOMPDATA*);

static void Acrylic(HWND h) {
    LONG ex = GetWindowLong(h, GWL_EXSTYLE);
    SetWindowLong(h, GWL_EXSTYLE, (ex | WS_EX_LAYERED | WS_EX_TOOLWINDOW) & ~WS_EX_APPWINDOW);
    SetLayeredWindowAttributes(h, RGB(255, 0, 255), 0, LWA_COLORKEY);
    // Accent blur is always a rectangle and shows as the left white frame outside the pill.
    DWM_BLURBEHIND bb{};
    bb.dwFlags = DWM_BB_ENABLE;
    bb.fEnable = FALSE;
    DwmEnableBlurBehindWindow(h, &bb);
    int ncrp = 1; // DWMNCRP_DISABLED — skips DWM’s dark frame
    DwmSetWindowAttribute(h, 2, &ncrp, sizeof(ncrp));
    HMODULE u = GetModuleHandleW(L"user32.dll");
    auto fn = (SetWindowCompositionAttributeFn)GetProcAddress(u, "SetWindowCompositionAttribute");
    if (fn) {
        ACCENTPOLICY ap{};
        ap.s = 0;
        WINCOMPDATA d{ 19, &ap, sizeof(ap) };
        fn(h, &d);
    }
}

static void DrawCenter(Graphics& gph, const std::wstring& t, Font& f, SolidBrush& br, float cx, float cy) {
    StringFormat fmt;
    fmt.SetAlignment(StringAlignmentCenter);
    fmt.SetLineAlignment(StringAlignmentCenter);
    gph.DrawString(t.c_str(), -1, &f, RectF(cx - 40, cy - 12, 80, 24), &fmt, &br);
}

static void DrawRight(Graphics& gph, const std::wstring& t, Font& f, SolidBrush& br, float right, float y) {
    StringFormat fmt;
    fmt.SetAlignment(StringAlignmentFar);
    gph.DrawString(t.c_str(), -1, &f, RectF(right - 180, y, 180, 20), &fmt, &br);
}

static void RoundBar(Graphics& gph, float x, float y, float w, float h, Color fill) {
    GraphicsPath p(FillModeWinding);
    float r = h / 2;
    p.AddArc(x, y, h, h, 90, 180);
    p.AddArc(x + w - h, y, h, h, 270, 180);
    p.CloseFigure();
    SolidBrush br(fill);
    gph.FillPath(&br, &p);
}

static int Meter(Graphics& gph, Font& ui, Font& sm, int x, int y, int width, const wchar_t* title, double pct, const wchar_t* note, bool known = true) {
    SolidBrush ink(Color(255, 32, 32, 34));
    SolidBrush muted(Color(200, 90, 96, 105));
    SolidBrush noteBr(Color(170, 120, 125, 133));
    gph.DrawString(title, -1, &ui, PointF((float)x, (float)y), &ink);
    wchar_t right[32];
    if (!g.snap.ok || !known) wcscpy(right, L"--");
    else if (pct > 0 && pct < 0.5) wcscpy(right, L"<1%");
    else swprintf(right, 32, L"%.0f%%", pct);
    DrawRight(gph, right, sm, muted, (float)(width - S(22)), (float)y);
    y += S(32);
    int barW = width - S(44);
    RoundBar(gph, (float)x, (float)y, (float)barW, (float)S(5), Color(255, 228, 230, 233));
    float fill = (float)(std::max(0.0, std::min(100.0, pct)) / 100.0 * barW);
        Color bar = Color(255, 0x34, 0xC7, 0x59);
    if (pct >= 90.0) bar = Color(255, 0xE5, 0x48, 0x4D);
    else if (pct >= 70.0) bar = Color(255, 0xE6, 0xA2, 0x3C);
    if (g.snap.ok && known && fill > 3) RoundBar(gph, (float)x, (float)y, fill, (float)S(5), bar);
    y += S(9);
    gph.DrawString(note, -1, &sm, PointF((float)x, (float)y), &noteBr);
    return y + S(16);
}

static void DrawCursorIcon(Graphics& gph, float cx, float cy, float s, Color col) {
    PointF pts[4] = {
        {cx + s * 0.78f, cy - s * 0.78f},
        {cx - s * 0.82f, cy - s * 0.18f},
        {cx - s * 0.06f, cy + s * 0.04f},
        {cx + s * 0.16f, cy + s * 0.82f},
    };
    GraphicsPath path;
    path.AddPolygon(pts, 4);
    SolidBrush br(col);
    gph.FillPath(&br, &path);
}

static void DrawTitleMark(Graphics& gph, float x, float y) {
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

static void DrawLoops(Graphics& gph, float cx, float cy, float s, Color col) {
    Pen p(col, 1.6f);
    gph.DrawEllipse(&p, cx - s, cy - s * 0.35f, s * 1.15f, s * 0.9f);
    gph.DrawEllipse(&p, cx - s * 0.2f, cy - s * 0.55f, s * 1.15f, s * 0.9f);
}

static void DrawBars(Graphics& gph, float cx, float cy, float s, Color col) {
    SolidBrush br(col);
    float w = s * 0.38f, gap = s * 0.22f;
    gph.FillRectangle(&br, cx - w * 1.5f - gap, cy + s * 0.1f, w, s * 0.7f);
    gph.FillRectangle(&br, cx - w * 0.5f, cy - s * 0.35f, w, s * 1.15f);
    gph.FillRectangle(&br, cx + w * 0.5f + gap, cy - s * 0.05f, w, s * 0.85f);
}

static Color RingColor(int kind, float p) {
    if (p >= 90.f) return Color(255, 0xE5, 0x48, 0x4D);
    if (kind == 1) return Color(255, 0x7C, 0x5C, 0xBF);
    if (kind == 2) return Color(255, 0x3A, 0xA0, 0xA0);
    if (kind == 3) return Color(255, 0xC4, 0x6B, 0x3A);
    return Color(255, 0x2F, 0x6F, 0xED);
}

static void FillRoundRect(Graphics& gph, float x, float y, float w, float h, float rad, SolidBrush& br) {
    if (rad * 2.f > h) rad = h * 0.5f;
    if (rad * 2.f > w) rad = w * 0.5f;
    GraphicsPath p(FillModeWinding);
    p.AddArc(x, y, rad * 2.f, rad * 2.f, 180.f, 90.f);
    p.AddArc(x + w - rad * 2.f, y, rad * 2.f, rad * 2.f, 270.f, 90.f);
    p.AddArc(x + w - rad * 2.f, y + h - rad * 2.f, rad * 2.f, rad * 2.f, 0.f, 90.f);
    p.AddArc(x, y + h - rad * 2.f, rad * 2.f, rad * 2.f, 90.f, 90.f);
    p.CloseFigure();
    gph.FillPath(&br, &p);
}

static void DrawRingGlyph(Graphics& gph, float cx, float cy, float size, int kind) {
    const float s = size / 32.f;
    const float ox = cx - size * 0.5f;
    const float oy = cy - size * 0.5f;
    if (kind == 3) {
        SolidBrush body(Color(255, 0x20, 0x20, 0x22));
        FillRoundRect(gph, ox + 9.f * s, oy + 11.f * s, 14.f * s, 12.f * s, 3.f * s, body);
        FillRoundRect(gph, ox + 14.2f * s, oy + 7.2f * s, 3.6f * s, 4.f * s, 1.2f * s, body);
        SolidBrush eye(Color(255, 0xF8, 0xF8, 0xFA));
        gph.FillEllipse(&eye, ox + 11.9f * s, oy + 14.9f * s, 2.6f * s, 2.6f * s);
        gph.FillEllipse(&eye, ox + 17.5f * s, oy + 14.9f * s, 2.6f * s, 2.6f * s);
        return;
    }
    if (kind == 1) {
        SolidBrush br(Color(255, 0x7C, 0x5C, 0xBF));
        FillRoundRect(gph, ox + 8.f * s, oy + 8.f * s, 16.f * s, 16.f * s, 3.5f * s, br);
        return;
    }
    if (kind == 2) {
        SolidBrush br(Color(255, 0x3A, 0xA0, 0xA0));
        gph.FillEllipse(&br, ox + 6.9f * s, oy + 12.4f * s, 7.2f * s, 7.2f * s);
        gph.FillEllipse(&br, ox + 17.9f * s, oy + 12.4f * s, 7.2f * s, 7.2f * s);
        float lw = 3.2f * s; if (lw < 2.2f) lw = 2.2f;
        Pen link(Color(255, 0x3A, 0xA0, 0xA0), lw);
        link.SetStartCap(Gdiplus::LineCapRound);
        link.SetEndCap(Gdiplus::LineCapRound);
        gph.DrawLine(&link, ox + 14.1f * s, oy + 16.f * s, ox + 17.9f * s, oy + 16.f * s);
        return;
    }
    PointF pts[7] = {
        {ox + 10.5f * s, oy + 6.5f * s},
        {ox + 10.5f * s, oy + 23.5f * s},
        {ox + 14.6f * s, oy + 18.8f * s},
        {ox + 18.8f * s, oy + 25.5f * s},
        {ox + 21.6f * s, oy + 23.8f * s},
        {ox + 17.6f * s, oy + 16.8f * s},
        {ox + 23.2f * s, oy + 16.2f * s},
    };
    GraphicsPath tip;
    tip.AddPolygon(pts, 7);
    SolidBrush br(Color(255, 0x2F, 0x6F, 0xED));
    gph.FillPath(&br, &tip);
}


static void DrawRingItem(Graphics& gph, Font& num, float cx, float y, float r, double pct, int kind, bool known = true) {
    Color track(255, 220, 224, 228);
    Color ink(255, 32, 32, 34);
    bool live = g.snap.ok && known;
    float p = live ? (float)std::max(0.0, std::min(100.0, pct)) : 0.f;
    Color go = RingColor(kind, p);
    Pen ring(track, 2.0f);
    gph.DrawEllipse(&ring, cx - r, y - r, r * 2, r * 2);
    if (live && p > 0.3f) {
        Pen arc(go, 2.2f);
        arc.SetStartCap(Gdiplus::LineCapRound);
        arc.SetEndCap(Gdiplus::LineCapRound);
        gph.DrawArc(&arc, cx - r, y - r, r * 2, r * 2, -90.f, 360.f * p / 100.f);
    }
    FontFamily yahei(L"Microsoft YaHei UI");
    const FontFamily* fam = (yahei.GetLastStatus() == Gdiplus::Ok) ? &yahei : FontFamily::GenericSansSerif();
    Font mark(fam, 7.0f, Gdiplus::FontStyleBold, Gdiplus::UnitPoint);
    SolidBrush markBr(go);
    DrawRingGlyph(gph, cx, y, (r - 3.f) * 1.55f, kind);
    wchar_t buf[16];
    if (!live) wcscpy(buf, L"--");
    else swprintf(buf, 16, (p > 0 && p < 0.5) ? L"<1%%" : L"%.0f%%", p);
    SolidBrush text(ink);
    DrawCenter(gph, buf, num, text, cx, y + r + (float)S(11));
}

static void AddBodyPath(GraphicsPath& body, float w, float hh, float rad, float inset) {
    float x = inset;
    float y = inset;
    float rw = w - inset * 2.f;
    float rh = hh - inset * 2.f;
    float r = rad;
    if (r < 8.f) r = 8.f;
    if (r * 2.f > rh * 0.4f) r = rh * 0.2f;
    float right = x + rw;
    float bot = y + rh;
    if (g.dockRight) {
        body.AddArc(x, y, r * 2.f, r * 2.f, 180.f, 90.f);
        body.AddLine(x + r, y, right, y);
        body.AddLine(right, y, right, bot);
        body.AddLine(right, bot, x + r, bot);
        body.AddArc(x, bot - r * 2.f, r * 2.f, r * 2.f, 90.f, 90.f);
        body.CloseFigure();
    } else {
        body.AddArc(right - r * 2.f, y, r * 2.f, r * 2.f, 270.f, 90.f);
        body.AddLine(right, y + r, right, bot - r);
        body.AddArc(right - r * 2.f, bot - r * 2.f, r * 2.f, r * 2.f, 0.f, 90.f);
        body.AddLine(right - r, bot, x, bot);
        body.AddLine(x, bot, x, y);
        body.AddLine(x, y, right - r, y);
        body.CloseFigure();
    }
}

static HBITMAP MakeDib(int w, int hh, void** bits) {
    BITMAPINFO bmi{};
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = w;
    bmi.bmiHeader.biHeight = -hh;
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 32;
    bmi.bmiHeader.biCompression = BI_RGB;
    return CreateDIBSection(nullptr, &bmi, DIB_RGB_COLORS, bits, nullptr, 0);
}

static void Paint(HWND h, HDC hdc) {
    RECT rc; GetClientRect(h, &rc);
    int w = rc.right, hh = rc.bottom;
    if (w <= 0 || hh <= 0) return;

    void* bits = nullptr;
    HDC mem = CreateCompatibleDC(hdc);
    HBITMAP bmp = MakeDib(w, hh, &bits);
    HGDIOBJ old = bmp ? SelectObject(mem, bmp) : nullptr;
    HDC target = bmp ? mem : hdc;

    {
    Graphics gph(target);
    gph.SetSmoothingMode(SmoothingModeAntiAlias);
    gph.SetPixelOffsetMode(PixelOffsetModeHighQuality);
    gph.SetCompositingQuality(CompositingQualityHighQuality);
    gph.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
    gph.SetCompositingMode(Gdiplus::CompositingModeSourceCopy);
    Color fill(255, 248, 248, 250);
    gph.Clear(Color(255, 255, 0, 255));
    GraphicsPath body;
    float rad = (float)S(12);
    AddBodyPath(body, (float)w, (float)hh, rad, 2.0f);
    LinearGradientBrush wash(PointF(0.f, 0.f), PointF(0.f, (float)hh),
                             Color(255, 255, 255, 255), Color(255, 238, 239, 242));
    gph.FillPath(&wash, &body);
    gph.SetCompositingMode(Gdiplus::CompositingModeSourceOver);
    Pen rim(Color(255, 0xD8, 0xDC, 0xE1), 1.0f);
    rim.SetAlignment(PenAlignmentInset);
    rim.SetLineJoin(Gdiplus::LineJoinRound);
    gph.DrawPath(&rim, &body);

    FontFamily yahei(L"Microsoft YaHei UI");
    FontFamily segoe(L"Segoe UI");
    const FontFamily* uiFam = (yahei.GetLastStatus() == Gdiplus::Ok) ? &yahei : FontFamily::GenericSansSerif();
    const FontFamily* numFam = (segoe.GetLastStatus() == Gdiplus::Ok) ? &segoe : FontFamily::GenericSansSerif();
    Font title(uiFam, 11, Gdiplus::FontStyleBold, Gdiplus::UnitPoint);
    Font ui(uiFam, 8.5f, Gdiplus::FontStyleRegular, Gdiplus::UnitPoint);
    Font sm(uiFam, 7.5f, Gdiplus::FontStyleRegular, Gdiplus::UnitPoint);
    Font num(numFam, 8.0f, Gdiplus::FontStyleBold, Gdiplus::UnitPoint);
    SolidBrush white(Color(255, 32, 32, 34));
    SolidBrush muted(Color(200, 90, 96, 105));

    if (!g.expanded) {
        float r = (float)S(14);
        float cx = w * 0.5f;
        float y0 = (float)S(30);
        float step = (float)S(64);
        DrawRingItem(gph, num, cx, y0, r, g.snap.autoP, 0);
        DrawRingItem(gph, num, cx, y0 + step, r, g.snap.api, 1);
        DrawRingItem(gph, num, cx, y0 + step * 2, r, g.snap.total, 2);
        if (g.showBot)
            DrawRingItem(gph, num, cx, y0 + step * 3, r, g.snap.botP, 3, g.snap.botKnown);
    } else {


        int pad = S(20);
        DrawTitleMark(gph, (float)pad, (float)S(14));
        gph.DrawString(L"Cursor 用量", -1, &title, PointF((float)pad + 20.f, (float)S(14)), &white);
        int y = S(46);
        if (!g.snap.ok) {
            DrawRight(gph, L"\u672a\u8bfb\u5230", ui, muted, (float)(w - pad), (float)S(16));
            gph.DrawString(L"\u672a\u8bfb\u5230\u7528\u91cf", -1, &ui, PointF((float)pad, (float)S(46)), &muted);
        } else {
            if (!g.snap.membership.empty())
                DrawRight(gph, Utf8ToWide(g.snap.membership), ui, muted, (float)(w - pad), (float)S(16));
            y = S(46);
            y = Meter(gph, ui, sm, pad, y, w, L"Auto", g.snap.autoP, L"");
            y = Meter(gph, ui, sm, pad, y, w, L"Models", g.snap.api, L"");
            y = Meter(gph, ui, sm, pad, y, w, L"API", g.snap.total, L"");
            if (g.showBot)
                y = Meter(gph, ui, sm, pad, y, w, L"Bot", g.snap.botP, L"", g.snap.botKnown);
        }

        int viewY = y + S(8);
        int ty = viewY;
        gph.DrawString(L"今日 Token", -1, &ui, PointF((float)pad, (float)ty), &white);
        DrawRight(gph, FormatTok(g.snap.today()), num, white, (float)(w - pad), (float)ty);
        ty += S(24);
        std::wstring detail = L"入 " + FormatTok(g.snap.tin) + L"  ·  出 " + FormatTok(g.snap.tout);
        if (g.snap.tcache) detail += L"  ·  缓存 " + FormatTok(g.snap.tcache);
        gph.DrawString(detail.c_str(), -1, &sm, PointF((float)pad, (float)ty), &muted);
        ty += S(16);
        for (auto& row : g.snap.models) {
            std::wstring name = Utf8ToWide(row.name);
            if (name.size() > 20) name = name.substr(0, 19) + L"…";
            gph.DrawString(name.c_str(), -1, &sm, PointF((float)pad, (float)ty), &muted);
            DrawRight(gph, FormatTok(row.tokens), sm, muted, (float)(w - pad), (float)ty);
            ty += S(16);
        }
        ty += S(4);
        wchar_t foot[64];
        if (g.snap.ok) swprintf(foot, 64, L"%02d:%02d:%02d", g.snap.fetched.wHour, g.snap.fetched.wMinute, g.snap.fetched.wSecond);
        else {
            std::wstring err = Utf8ToWide(g.snap.error);
            wcsncpy(foot, err.c_str(), 63); foot[63] = 0;
        }
        gph.DrawString(foot, -1, &sm, PointF((float)pad, (float)ty), &muted);

    }
    }

    if (bmp) {
        BitBlt(hdc, 0, 0, w, hh, mem, 0, 0, SRCCOPY);
        SelectObject(mem, old);
        DeleteObject(bmp);
    }
    DeleteDC(mem);
}

static DWORD WINAPI FetchThread(LPVOID) {
    Snapshot s = FetchUsage();
    Snapshot* heap = new Snapshot(s);
    PostMessage(g.hwnd, WM_USAGE, 0, (LPARAM)heap);
    return 0;
}

static void Refresh() { CreateThread(nullptr, 0, FetchThread, nullptr, 0, nullptr); }

static void TrackLeave(HWND h) {
    TRACKMOUSEEVENT tme{ sizeof(tme), TME_LEAVE, h, 0 };
    TrackMouseEvent(&tme);
    g.tracking = true;
}

static LRESULT CALLBACK WndProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
    case WM_CREATE:
        Acrylic(h);
        Place(h);
        Refresh();
        SetTimer(h, 1, 45000, nullptr);
        return 0;
    case WM_TIMER:
        Refresh();
        return 0;
    case WM_USAGE: {
        Snapshot* s = (Snapshot*)l;
        if (s) { g.snap = *s; delete s; }
        g.scrollY = ClampI(g.scrollY, 0, MaxScroll());
        if (g.expanded) Place(h);
        InvalidateRect(h, nullptr, FALSE);
        return 0;
    }
    case WM_PAINT: {
        PAINTSTRUCT ps; HDC hdc = BeginPaint(h, &ps);
        Paint(h, hdc);
        EndPaint(h, &ps);
        return 0;
    }
    case WM_ERASEBKGND: return 1;
    case WM_NCPAINT: return 0;
    case WM_NCCALCSIZE:
        if (w) return 0;
        break;
    case WM_LBUTTONDOWN: {
        POINT pt{ GET_X_LPARAM(l), GET_Y_LPARAM(l) };
        if (g.expanded && MaxScroll() > 0 && pt.y >= TokenViewY()) {
            g.scrolling = true;
            g.press = pt;
            SetCapture(h);
            return 0;
        }
        ClientToScreen(h, &pt);
        g.press = pt;
        g.pressY = g.y;
        g.dragging = false;
        g.scrolling = false;
        SetCapture(h);
        return 0;
    }
    case WM_MOUSEMOVE:
        if (g.expanded && !g.tracking) TrackLeave(h);
        if (GetCapture() == h && g.scrolling) {
            int dy = GET_Y_LPARAM(l) - g.press.y;
            g.press.y = GET_Y_LPARAM(l);
            g.scrollY = ClampI(g.scrollY + dy * MaxScroll() / (std::max)(1, TokenViewH() - 16), 0, MaxScroll());
            InvalidateRect(h, nullptr, FALSE);
            return 0;
        }
        if (GetCapture() == h && !g.scrolling) {
            POINT pt;
            GetCursorPos(&pt);
            if (abs(pt.y - g.press.y) > 4 || abs(pt.x - g.press.x) > 4) {
                g.dragging = true;
                g.y = g.pressY + (pt.y - g.press.y);
                DragMove(h);
            }
        }
        return 0;
    case WM_MOUSEWHEEL:
        if (g.expanded && MaxScroll() > 0) {
            int delta = GET_WHEEL_DELTA_WPARAM(w);
            g.scrollY = ClampI(g.scrollY - delta / WHEEL_DELTA * S(28), 0, MaxScroll());
            InvalidateRect(h, nullptr, FALSE);
            return 0;
        }
        break;
    case WM_LBUTTONUP:
        ReleaseCapture();
        if (g.scrolling) {
            g.scrolling = false;
            return 0;
        }
        if (g.dragging) {
            DragMove(h);
            ApplyRegion(h);
            SaveConfig();
        } else if (!g.expanded) {
            g.expanded = true;
            g.scrollY = 0;
            g.holdUntil = GetTickCount() + 800;
            Place(h);
            g.tracking = false;
            TrackLeave(h);
        }
        return 0;
    case WM_MOUSELEAVE:
        g.tracking = false;
        if (!g.expanded) return 0;
        if (GetTickCount() < g.holdUntil || CursorInWindow(h)) {
            TrackLeave(h);
            return 0;
        }
        g.expanded = false;
        g.scrollY = 0;
        g.scrolling = false;
        Place(h);
        return 0;
    case WM_RBUTTONUP: {
        POINT pt{ GET_X_LPARAM(l), GET_Y_LPARAM(l) };
        ClientToScreen(h, &pt);
        HMENU menu = CreatePopupMenu();
        HMENU scale = CreatePopupMenu();
        auto chk = [](int v) { return (UINT)(MF_STRING | (g.scale == v ? MF_CHECKED : 0)); };
        AppendMenuW(scale, chk(70), 21, L"70%");
        AppendMenuW(scale, chk(80), 22, L"80%");
        AppendMenuW(scale, chk(100), 23, L"100%");
        AppendMenuW(scale, chk(125), 24, L"125%");
        AppendMenuW(menu, MF_STRING, 10, L"立即刷新");
        AppendMenuW(menu, MF_STRING, 11, L"打开用量页");
        AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(menu, MF_POPUP, (UINT_PTR)scale, L"显示大小");
        AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(menu, MF_STRING, 12, L"贴到左边");
        AppendMenuW(menu, MF_STRING, 13, L"贴到右边");
        AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(menu, (UINT)(MF_STRING | (AutoStartOn() ? MF_CHECKED : 0)), 15, L"开机启动");
        AppendMenuW(menu, (UINT)(MF_STRING | (g.showBot ? MF_CHECKED : 0)), 16, L"显示 Bot 用量");
        AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(menu, MF_STRING, 14, L"退出");
        int cmd = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON, pt.x, pt.y, 0, h, nullptr);
        DestroyMenu(menu);
        if (cmd == 10) Refresh();
        if (cmd == 11) ShellExecuteW(nullptr, L"open", L"https://cursor.com/dashboard", nullptr, nullptr, SW_SHOWNORMAL);
        if (cmd == 12) { g.dockRight = false; Place(h); SaveConfig(); }
        if (cmd == 13) { g.dockRight = true; Place(h); SaveConfig(); }
        if (cmd == 21 || cmd == 22 || cmd == 23 || cmd == 24) {
            int map[] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 70, 80, 100, 125 };
            g.scale = map[cmd];
            Place(h);
            SaveConfig();
        }
        if (cmd == 15) SetAutoStart(!AutoStartOn());
        if (cmd == 16) { g.showBot = !g.showBot; Place(h); SaveConfig(); }
        if (cmd == 14) DestroyWindow(h);
        return 0;
    }
    case WM_DESTROY:
        SaveConfig();
        KillTimer(h, 1);
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProc(h, m, w, l);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE, LPWSTR, int) {
    HANDLE mu = CreateMutexW(nullptr, TRUE, L"CursorUsageWidgetCpp");
    if (GetLastError() == ERROR_ALREADY_EXISTS) return 0;
    LoadConfig();
    SetProcessDPIAware();
    Gdiplus::GdiplusStartupInput in;
    Gdiplus::GdiplusStartup(&g.gdip, &in, nullptr);

    WNDCLASSEXW wc{ sizeof(wc) };
    wc.lpfnWndProc = WndProc;
    wc.hInstance = inst;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hIcon = (HICON)LoadImageW(inst, MAKEINTRESOURCEW(1), IMAGE_ICON, 32, 32, LR_DEFAULTCOLOR);
    wc.hIconSm = (HICON)LoadImageW(inst, MAKEINTRESOURCEW(1), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
    wc.lpszClassName = L"CursorUsageWidget";
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.hbrBackground = CreateSolidBrush(RGB(248, 248, 250));
    RegisterClassExW(&wc);

    g.hwnd = CreateWindowExW(WS_EX_TOPMOST | WS_EX_TOOLWINDOW | WS_EX_LAYERED,
                             wc.lpszClassName, L"Cursor 用量",
                             WS_POPUP,
                             0, 0, StripW(), StripH(),
                             nullptr, nullptr, inst, nullptr);
    Acrylic(g.hwnd);
    if (wc.hIcon) SendMessageW(g.hwnd, WM_SETICON, ICON_BIG, (LPARAM)wc.hIcon);
    if (wc.hIconSm) SendMessageW(g.hwnd, WM_SETICON, ICON_SMALL, (LPARAM)wc.hIconSm);
    Place(g.hwnd);
    ShowWindow(g.hwnd, SW_SHOW);
    UpdateWindow(g.hwnd);

    MSG msg;
    while (GetMessage(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    Gdiplus::GdiplusShutdown(g.gdip);
    CloseHandle(mu);
    return 0;
}

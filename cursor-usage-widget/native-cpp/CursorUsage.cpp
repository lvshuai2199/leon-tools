#define _CRT_SECURE_NO_WARNINGS
#define NOMINMAX
#include <windows.h>
#include <windowsx.h>
#include <objidl.h>
#include <shellapi.h>
#include <commdlg.h>
#include <winhttp.h>
#include <gdiplus.h>
#include <dwmapi.h>
#include <string>
#include <vector>
#include <map>
#include <cmath>
#include <cctype>
#include <cstring>
#include <ctime>
#include <sstream>
#include <algorithm>
#include <fstream>
#include <regex>
#include <set>
#include <shlobj.h>
#include <shlwapi.h>
#include <commctrl.h>
#include <commoncontrols.h>

#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "winhttp.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "comdlg32.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "dwmapi.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "shlwapi.lib")

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
using Gdiplus::Bitmap;
using Gdiplus::InterpolationModeHighQualityBicubic;

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
    // Cached stripe membership is only applied after a real usage payload lands.
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

    bool gotUsage = false;
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
    int dockEdge = 1; // 0 left, 1 right, 2 top
    int y = -1;
    bool dragging = false;
    POINT press{};
    int pressY = 0;
    int pressLaunch = -1;
    Snapshot snap;
    ULONG_PTR gdip = 0;
    bool tracking = false;
    DWORD holdUntil = 0;
    int scale = 80;
    bool showBot = false;
    bool showApi = true; // dual-ring lower outer; default on
    int ringMode = 0; // 0=quad four rings, 1=dual concentric pairs
    int bgAlpha = 100; // legacy unused
    int scrollY = 0;
    bool scrolling = false;
    bool dark = false;
} g;

static int S(int v) { return (int)(v * g.scale / 100.0 + 0.5); }
static int QuickIconPx() { int n = S(40); return n < 36 ? 36 : n; }
static int QuickTilePad() { int n = S(8); return n < 7 ? 7 : n; }
static int QuickTilePx() { return QuickIconPx() + QuickTilePad() * 2; }
static int QuickGap() { int n = S(12); return n < 10 ? 10 : n; }
static int QuickPad() { int n = S(14); return n < 12 ? 12 : n; }
static int QuickRowGap() { return QuickGap(); }

// Follow Windows app theme. Icons: light #202022 / dark #E8E8EA. Ring hues stay.
static bool ReadAppsDark() {
    HKEY k = nullptr;
    if (RegOpenKeyExW(HKEY_CURRENT_USER,
            L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            0, KEY_READ, &k) != ERROR_SUCCESS)
        return false;
    DWORD light = 1, sz = sizeof(light), type = 0;
    LONG r = RegQueryValueExW(k, L"AppsUseLightTheme", nullptr, &type, (LPBYTE)&light, &sz);
    RegCloseKey(k);
    return r == ERROR_SUCCESS && type == REG_DWORD && light == 0;
}

static void Repaint(HWND h);

static void RefreshTheme(HWND h) {
    bool dark = ReadAppsDark();
    if (dark == g.dark && h) return;
    g.dark = dark;
    if (h) Repaint(h);
}

static Color IconInk() {
    return g.dark ? Color(255, 0xE8, 0xE8, 0xEA) : Color(255, 0x20, 0x20, 0x22);
}
// Collapsed rings ~1.13x; side dock = vertical strip, top dock = horizontal bar.
static const float kRingScale = 1.13f * 0.88f; // product: rings x0.88
static float RingR() { return (float)S(14) * kRingScale; }
static const int kRingGap = 8; // top-dock H + side-dock V
static const int kCollapsedPad = 10; // ~1/2-2/3 prior extra air
static const int kPctBelow = 16; // % center below ring edge
static float RingStepV() {
    return 2.f * RingR() + (float)kPctBelow + (float)S(12) + (float)kRingGap;
}


static int DualPairCount();
static float DualOuterR();
static float DualInnerR();
static float DualCenterGap();




static int CollapsedRingCount() {
    int n = 2; // Auto + Models
    if (g.showApi) n++;
    if (g.showBot) n++;
    return n;
}
static void DrawCollapsedRingsHV(Graphics& gph, Font& num, float x0, float y0, float r,
    float stepX, float stepY, bool horizontal);

static int StripW() {
    if (g.dockEdge == 2) {
        if (g.ringMode == 1) {
            float Ro = DualOuterR();
            int n = DualPairCount();
            int gap = 12;
            return 12 * 2 + (int)(n * (2.f * Ro) + (n - 1) * gap + 0.5f);
        }
        float r = RingR();
        int n = CollapsedRingCount();
        int gap = kRingGap;
        return kCollapsedPad * 2 + (int)(n * (2.f * r) + (n - 1) * gap + 0.5f);
    }
    {
        float rSide = (g.ringMode == 1) ? DualOuterR() : RingR();
        int side = kCollapsedPad;
        int ww = (int)(2.f * rSide + (float)(side * 2) + 0.5f);
        int minW = S(44);
        return ww < minW ? minW : ww;
    }
}
static int StripH() {
    if (g.dockEdge == 2) {
        if (g.ringMode == 1) {
            float Ro = DualOuterR();
            int hh = (int)(2.f * Ro + 12.f + 0.5f);
            return hh < 40 ? 40 : hh;
        }
        float r = RingR();
        return S(6) * 2 + (int)(2.f * r + (float)kPctBelow + (float)S(10) + 0.5f);
    }
    if (g.ringMode == 1) {
        float Ro = DualOuterR();
        float topPad = (float)kCollapsedPad;
        float y0 = Ro + topPad;
        int n = DualPairCount();
        float step = DualCenterGap();
        float lastY = y0 + step * (float)(n - 1);
        float contentBottom = lastY + Ro + (float)S(10);
        return (int)(contentBottom + topPad + 0.5f);
    }
    float r = RingR();
    float topPad = (float)kCollapsedPad;
    float y0 = r + topPad;
    float step = RingStepV();
    int n = CollapsedRingCount();
    float lastY = y0 + step * (float)(n - 1);
    float contentBottom = lastY + r + (float)kPctBelow + (float)S(10);
    return (int)(contentBottom + topPad + 0.5f);
}
static int QuickColW();
static int PanelW() {
    int qw = QuickColW();
    if (qw <= 0) return S(BASE_PANEL_W);
    return S(BASE_PANEL_W) + S(16) + qw;
}
static int MeterH() { return S(57); }
static int ExpandedChromeH() {
    if (!g.snap.ok) return S(46) + S(36);
    return S(46) + MeterH() * CollapsedRingCount() + S(8);
}
static int TokenListH() {
    int n = (int)g.snap.models.size();
    if (n < 1 && !g.snap.topModel.empty()) n = 1;
    return S(26) + S(16) + n * S(16) + S(18);
}
static int QuickContentH();
static int ExpandedWantH() {
    int h;
    if (!g.snap.ok) h = ExpandedChromeH() + S(28);
    else h = ExpandedChromeH() + TokenListH() + S(14);
    int qh = QuickContentH();
    if (qh > 0) {
        int need = S(46) + qh + S(12);
        if (need > h) h = need;
    }
    return h;
}
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
        if (k == "dock") {
            if (v == "left") g.dockEdge = 0;
            else if (v == "top") g.dockEdge = 2;
            else g.dockEdge = 1;
        }
        if (k == "y") g.y = atoi(v.c_str());
        if (k == "showBot") g.showBot = v == "1";
        if (k == "showApi") g.showApi = v == "1";
        if (k == "ringMode") g.ringMode = (atoi(v.c_str()) == 1) ? 1 : 0;
        // bgAlpha removed — ignore legacy keys
    }
}

static void SaveConfig() {
    std::ofstream out(ConfigPath());
    if (!out) return;
    out << "scale=" << g.scale << "\n";
    out << "dock=" << (g.dockEdge == 0 ? "left" : g.dockEdge == 2 ? "top" : "right") << "\n";
    out << "y=" << g.y << "\n";
    out << "showBot=" << (g.showBot ? "1" : "0") << "\n";
    out << "showApi=" << (g.showApi ? "1" : "0") << "\n";
    out << "ringMode=" << g.ringMode << "\n";

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

static void SyncHotspots(HWND h);

static void KeepTopMost(HWND h) {
    if (!h) return;
    LONG_PTR ex = GetWindowLongPtrW(h, GWL_EXSTYLE);
    if (!(ex & WS_EX_TOPMOST))
        SetWindowLongPtrW(h, GWL_EXSTYLE, ex | WS_EX_TOPMOST);
    SetWindowPos(h, HWND_TOPMOST, 0, 0, 0, 0,
                 SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_NOOWNERZORDER | SWP_NOSENDCHANGING);
}

static void ApplyRegion(HWND h) {
    // Soft AA corners come from UpdateLayeredWindow per-pixel alpha.
    // Hard SetWindowRgn staircases the far-side r12 — keep region cleared.
    if (h) SetWindowRgn(h, nullptr, TRUE);
}

static void Place(HWND h) {
    RECT wa = Work();
    int w = WinW();
    int hh = WinH();
    int x = 0, y = 0;
    if (g.dockEdge == 2) {
        if (g.y < 0) g.y = wa.left + (wa.right - wa.left - w) / 2;
        g.y = ClampI(g.y, (int)wa.left + 4, (int)wa.right - w - 4);
        x = g.y;
        y = wa.top;
    } else {
        if (g.y < 0) g.y = wa.top + (wa.bottom - wa.top - hh) / 2;
        g.y = ClampI(g.y, (int)wa.top + 4, (int)wa.bottom - hh - 4);
        x = g.dockEdge == 1 ? wa.right - w : wa.left;
        y = g.y;
    }
    SetWindowPos(h, HWND_TOPMOST, x, y, w, hh,
                 SWP_NOACTIVATE | SWP_NOCOPYBITS | SWP_SHOWWINDOW);
    KeepTopMost(h);
    ApplyRegion(h);
    RedrawWindow(h, nullptr, nullptr, RDW_INVALIDATE | RDW_UPDATENOW | RDW_NOERASE);
    SyncHotspots(h);
}

static void DragMove(HWND h) {
    RECT wa = Work();
    int w = WinW();
    int hh = WinH();
    int x = 0, y = 0;
    if (g.dockEdge == 2) {
        g.y = ClampI(g.y, (int)wa.left + 4, (int)wa.right - w - 4);
        x = g.y;
        y = wa.top;
    } else {
        g.y = ClampI(g.y, (int)wa.top + 4, (int)wa.bottom - hh - 4);
        x = g.dockEdge == 1 ? wa.right - w : wa.left;
        y = g.y;
    }
    SetWindowPos(h, nullptr, x, y, 0, 0,
                 SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_NOREDRAW);
}

struct ACCENTPOLICY { int s, f; DWORD c; int a; };
struct WINCOMPDATA { int attr; PVOID data; ULONG size; };
typedef BOOL(WINAPI* SetWindowCompositionAttributeFn)(HWND, WINCOMPDATA*);

static void Acrylic(HWND h) {
    LONG ex = GetWindowLong(h, GWL_EXSTYLE);
    SetWindowLong(h, GWL_EXSTYLE, (ex | WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_TOPMOST) & ~WS_EX_APPWINDOW);
    // Per-pixel alpha via UpdateLayeredWindow in Paint (soft AA corners).
    DWM_BLURBEHIND bb{};
    bb.dwFlags = DWM_BB_ENABLE;
    bb.fEnable = FALSE;
    DwmEnableBlurBehindWindow(h, &bb);
    int ncrp = 1;
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
    SolidBrush ink(g.dark ? Color(255, 0xE8, 0xE8, 0xEA) : Color(255, 32, 32, 34));
    SolidBrush muted(g.dark ? Color(200, 0xA0, 0xA4, 0xAE) : Color(200, 90, 96, 105));
    SolidBrush noteBr(Color(170, 120, 125, 133));
    gph.DrawString(title, -1, &ui, PointF((float)x, (float)y), &ink);
    wchar_t right[32];
    if (!g.snap.ok || !known) wcscpy(right, L"--");
    else if (pct > 0 && pct < 0.5) wcscpy(right, L"<1%");
    else swprintf(right, 32, L"%.0f%%", pct);
    DrawRight(gph, right, sm, muted, (float)(width - S(22)), (float)y);
    y += S(32);
    int barW = (width - x) - S(24);
    if (barW < 8) barW = 8;
    RoundBar(gph, (float)x, (float)y, (float)barW, (float)S(5),
             g.dark ? Color(255, 0x3A, 0x3E, 0x44) : Color(255, 228, 230, 233));
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
    // hex cursor; mono fill follows light/dark
    SolidBrush ink(IconInk());
    const float vw = 49.f, vh = 56.f;
    const float sc = 16.f / vh;
    const float bx = x;
    const float by = y;
    auto X = [&](float px) { return bx + px * sc; };
    auto Y = [&](float py) { return by + py * sc; };
    GraphicsPath path(Gdiplus::FillModeAlternate);
    path.StartFigure();
    path.AddLine(X(48.0226f), Y(13.2547f), X(25.6601f), Y(0.3118f));
    path.AddBezier(X(25.6601f), Y(0.3118f), X(24.942f), Y(-0.1039f), X(24.0559f), Y(-0.1039f), X(23.3378f), Y(0.3118f));
    path.AddLine(X(23.3378f), Y(0.3118f), X(0.9763f), Y(13.2547f));
    path.AddBezier(X(0.9763f), Y(13.2547f), X(0.3727f), Y(13.6041f), X(0.f), Y(14.2503f), X(0.f), Y(14.9502f));
    path.AddLine(X(0.f), Y(14.9502f), X(0.f), Y(41.0498f));
    path.AddBezier(X(0.f), Y(41.0498f), X(0.f), Y(41.7496f), X(0.3727f), Y(42.3958f), X(0.9763f), Y(42.7453f));
    path.AddLine(X(0.9763f), Y(42.7453f), X(23.3389f), Y(55.6882f));
    path.AddBezier(X(23.3389f), Y(55.6882f), X(24.057f), Y(56.1039f), X(24.943f), Y(56.1039f), X(25.6611f), Y(55.6882f));
    path.AddLine(X(25.6611f), Y(55.6882f), X(48.0237f), Y(42.7453f));
    path.AddBezier(X(48.0237f), Y(42.7453f), X(48.6273f), Y(42.3958f), X(49.f), Y(41.7496f), X(49.f), Y(41.0498f));
    path.AddLine(X(49.f), Y(41.0498f), X(49.f), Y(14.9502f));
    path.AddBezier(X(49.f), Y(14.9502f), X(49.f), Y(14.2503f), X(48.6273f), Y(13.6041f), X(48.0226f), Y(13.2547f));
    path.CloseFigure();
    path.StartFigure();
    path.AddLine(X(46.6179f), Y(15.9964f), X(25.0302f), Y(53.4802f));
    path.AddBezier(X(25.0302f), Y(53.4802f), X(24.8842f), Y(53.7328f), X(24.4989f), Y(53.6296f), X(24.4989f), Y(53.337f));
    path.AddLine(X(24.4989f), Y(53.337f), X(24.4989f), Y(28.793f));
    path.AddBezier(X(24.4989f), Y(28.793f), X(24.4989f), Y(28.3026f), X(24.2375f), Y(27.849f), X(23.8134f), Y(27.6027f));
    path.AddLine(X(23.8134f), Y(27.6027f), X(2.6109f), Y(15.3312f));
    path.AddBezier(X(2.6109f), Y(15.3312f), X(2.359f), Y(15.1849f), X(2.4619f), Y(14.7987f), X(2.7537f), Y(14.7987f));
    path.AddLine(X(2.7537f), Y(14.7987f), X(45.9292f), Y(14.7987f));
    path.AddBezier(X(45.9292f), Y(14.7987f), X(46.5423f), Y(14.7987f), X(46.9255f), Y(15.4649f), X(46.619f), Y(15.9974f));
    path.CloseFigure();
    gph.FillPath(&ink, &path);
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

static void StrokeRoundRect(Graphics& gph, float x, float y, float w, float h, float rad, Pen& pen) {
    if (rad * 2.f > h) rad = h * 0.5f;
    if (rad * 2.f > w) rad = w * 0.5f;
    GraphicsPath p(FillModeWinding);
    p.AddArc(x, y, rad * 2.f, rad * 2.f, 180.f, 90.f);
    p.AddArc(x + w - rad * 2.f, y, rad * 2.f, rad * 2.f, 270.f, 90.f);
    p.AddArc(x + w - rad * 2.f, y + h - rad * 2.f, rad * 2.f, rad * 2.f, 0.f, 90.f);
    p.AddArc(x, y + h - rad * 2.f, rad * 2.f, rad * 2.f, 90.f, 90.f);
    p.CloseFigure();
    gph.DrawPath(&pen, &p);
}

static float RingGlyphX(float cx, float size, float x) {
    return cx - size * 0.5f + (3.7f + x * 0.17f) * (size / 16.f);
}
static float RingGlyphY(float cy, float size, float y) {
    return cy - size * 0.5f + (3.25f + y * 0.17f) * (size / 16.f);
}

static void AddRingHex(GraphicsPath& path, float cx, float cy, float size) {
    auto X = [&](float x) { return RingGlyphX(cx, size, x); };
    auto Y = [&](float y) { return RingGlyphY(cy, size, y); };
    PointF hex[6] = {
        {X(24.5f), Y(0.3f)}, {X(48.02f), Y(13.25f)}, {X(48.02f), Y(42.75f)},
        {X(24.5f), Y(55.69f)}, {X(0.98f), Y(42.75f)}, {X(0.98f), Y(13.25f)},
    };
    path.AddPolygon(hex, 6);
}

static void DrawRingGlyph(Graphics& gph, float cx, float cy, float size, int kind) {
    // Four ring icons: hex hollow, same 16x16 frame (ui-review/icon-*.svg).
    // Mono fill follows theme; ring stroke colors stay blue / purple / teal / amber.
    SolidBrush ink(IconInk());
    auto X = [&](float x) { return RingGlyphX(cx, size, x); };
    auto Y = [&](float y) { return RingGlyphY(cy, size, y); };
    GraphicsPath path(Gdiplus::FillModeAlternate);

    if (kind == 0) {
        // icon-auto.svg — cursor cutout in hex
        path.StartFigure();
        path.AddLine(X(48.0226f), Y(13.2547f), X(25.6601f), Y(0.3118f));
        path.AddBezier(X(25.6601f), Y(0.3118f), X(24.942f), Y(-0.1039f), X(24.0559f), Y(-0.1039f), X(23.3378f), Y(0.3118f));
        path.AddLine(X(23.3378f), Y(0.3118f), X(0.9763f), Y(13.2547f));
        path.AddBezier(X(0.9763f), Y(13.2547f), X(0.3727f), Y(13.6041f), X(0.f), Y(14.2503f), X(0.f), Y(14.9502f));
        path.AddLine(X(0.f), Y(14.9502f), X(0.f), Y(41.0498f));
        path.AddBezier(X(0.f), Y(41.0498f), X(0.f), Y(41.7496f), X(0.3727f), Y(42.3958f), X(0.9763f), Y(42.7453f));
        path.AddLine(X(0.9763f), Y(42.7453f), X(23.3389f), Y(55.6882f));
        path.AddBezier(X(23.3389f), Y(55.6882f), X(24.057f), Y(56.1039f), X(24.943f), Y(56.1039f), X(25.6611f), Y(55.6882f));
        path.AddLine(X(25.6611f), Y(55.6882f), X(48.0237f), Y(42.7453f));
        path.AddBezier(X(48.0237f), Y(42.7453f), X(48.6273f), Y(42.3958f), X(49.f), Y(41.7496f), X(49.f), Y(41.0498f));
        path.AddLine(X(49.f), Y(41.0498f), X(49.f), Y(14.9502f));
        path.AddBezier(X(49.f), Y(14.9502f), X(49.f), Y(14.2503f), X(48.6273f), Y(13.6041f), X(48.0226f), Y(13.2547f));
        path.CloseFigure();
        path.StartFigure();
        path.AddLine(X(46.6179f), Y(15.9964f), X(25.0302f), Y(53.4802f));
        path.AddBezier(X(25.0302f), Y(53.4802f), X(24.8842f), Y(53.7328f), X(24.4989f), Y(53.6296f), X(24.4989f), Y(53.337f));
        path.AddLine(X(24.4989f), Y(53.337f), X(24.4989f), Y(28.793f));
        path.AddBezier(X(24.4989f), Y(28.793f), X(24.4989f), Y(28.3026f), X(24.2375f), Y(27.849f), X(23.8134f), Y(27.6027f));
        path.AddLine(X(23.8134f), Y(27.6027f), X(2.6109f), Y(15.3312f));
        path.AddBezier(X(2.6109f), Y(15.3312f), X(2.359f), Y(15.1849f), X(2.4619f), Y(14.7987f), X(2.7537f), Y(14.7987f));
        path.AddLine(X(2.7537f), Y(14.7987f), X(45.9292f), Y(14.7987f));
        path.AddBezier(X(45.9292f), Y(14.7987f), X(46.5423f), Y(14.7987f), X(46.9255f), Y(15.4649f), X(46.619f), Y(15.9974f));
        path.CloseFigure();
        gph.FillPath(&ink, &path);
        return;
    }

    AddRingHex(path, cx, cy, size);

    if (kind == 1) {
        // icon-models.svg — three bar cutouts
        PointF a[4] = {{X(11.f), Y(15.f)}, {X(38.f), Y(15.f)}, {X(35.f), Y(21.f)}, {X(14.f), Y(21.f)}};
        PointF b[4] = {{X(11.f), Y(25.f)}, {X(38.f), Y(25.f)}, {X(35.f), Y(31.f)}, {X(14.f), Y(31.f)}};
        PointF c[4] = {{X(11.f), Y(35.f)}, {X(38.f), Y(35.f)}, {X(35.f), Y(41.f)}, {X(14.f), Y(41.f)}};
        path.AddPolygon(a, 4);
        path.AddPolygon(b, 4);
        path.AddPolygon(c, 4);
    } else if (kind == 2) {
        // icon-api.svg — two nodes + bridge cutouts
        PointF n1[6] = {
            {X(8.f), Y(20.f)}, {X(17.f), Y(14.5f)}, {X(26.f), Y(20.f)},
            {X(26.f), Y(34.f)}, {X(17.f), Y(39.5f)}, {X(8.f), Y(34.f)},
        };
        PointF n2[6] = {
            {X(23.f), Y(20.f)}, {X(32.f), Y(14.5f)}, {X(41.f), Y(20.f)},
            {X(41.f), Y(34.f)}, {X(32.f), Y(39.5f)}, {X(23.f), Y(34.f)},
        };
        PointF bridge[4] = {
            {X(22.5f), Y(24.5f)}, {X(32.5f), Y(24.5f)}, {X(32.5f), Y(29.5f)}, {X(22.5f), Y(29.5f)},
        };
        path.AddPolygon(n1, 6);
        path.AddPolygon(n2, 6);
        path.AddPolygon(bridge, 4);
    } else if (kind == 3) {
        // icon-bot.svg — antenna, face, eyes, smile cutouts
        PointF ant[4] = {{X(22.f), Y(6.f)}, {X(27.f), Y(6.f)}, {X(27.f), Y(14.f)}, {X(22.f), Y(14.f)}};
        PointF body[4] = {{X(14.f), Y(16.f)}, {X(35.f), Y(16.f)}, {X(35.f), Y(43.f)}, {X(14.f), Y(43.f)}};
        path.AddPolygon(ant, 4);
        path.AddPolygon(body, 4);
        float er = 3.6f * 0.17f * (size / 16.f);
        path.AddEllipse(X(18.5f) - er, Y(26.6f) - er, er * 2.f, er * 2.f);
        path.AddEllipse(X(30.5f) - er, Y(26.6f) - er, er * 2.f, er * 2.f);
        GraphicsPath smile;
        smile.AddBezier(X(20.f), Y(35.5f), X(22.25f), Y(37.75f), X(26.75f), Y(37.75f), X(29.f), Y(35.5f));
        smile.AddLine(X(29.f), Y(35.5f), X(29.f), Y(38.f));
        smile.AddBezier(X(29.f), Y(38.f), X(26.75f), Y(40.25f), X(22.25f), Y(40.25f), X(20.f), Y(38.f));
        smile.CloseFigure();
        path.AddPath(&smile, FALSE);
    }

    gph.FillPath(&ink, &path);
}

static void DrawRingItem(Graphics& gph, Font& num, float cx, float y, float r, double pct, int kind, bool known = true) {
    Color track(g.dark ? Color(255, 0x3A, 0x3E, 0x44) : Color(255, 0xC5, 0xCA, 0xD3));
    Color ink = IconInk();
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
    // Same size for all four: visual hex ~0.62-0.65 of ring inner diameter; keep padding.
    float innerD = 2.f * r - 4.4f;
    if (innerD < 8.f) innerD = 2.f * r * 0.85f;
    // SVG hex fills ~0.595 of the 16px box; scale box so content hits ~0.64 of innerD.
    float glyphSize = innerD * 0.635f / 0.595f; // ~0.62-0.65 of inner diameter
    DrawRingGlyph(gph, cx, y, glyphSize, kind);
    if (live) {
        wchar_t buf[16];
        swprintf(buf, 16, (p > 0 && p < 0.5) ? L"<1%%" : L"%.0f%%", p);
        SolidBrush text(ink);
        DrawCenter(gph, buf, num, text, cx, y + r + (float)kPctBelow);
    }
}

static void DrawCollapsedRingsHV(Graphics& gph, Font& num, float x0, float y0, float r,
    float stepX, float stepY, bool horizontal) {
    float x = x0, y = y0;
    auto advance = [&]() {
        if (horizontal) x += stepX;
        else y += stepY;
    };
    DrawRingItem(gph, num, x, y, r, g.snap.autoP, 0); advance();
    DrawRingItem(gph, num, x, y, r, g.snap.api, 1); advance();
    if (g.showApi) { DrawRingItem(gph, num, x, y, r, g.snap.total, 2); advance(); }
    if (g.showBot) { DrawRingItem(gph, num, x, y, r, g.snap.botP, 3, g.snap.botKnown); }
}

static int DualPairCount() {
    // upper Auto/Model always; lower if Api or Bot visible
    int n = 1;
    if (g.showApi || g.showBot) n = 2;
    return n;
}
static float DualOuterR() { return (float)S(14); } // diam ~28
static float DualInnerR() { return (float)S(9); }  // diam ~18
static float DualCenterGap() { float gap = (float)S(52); float need = 2.f * DualOuterR() + (float)kRingGap; return gap > need ? gap : need; }

static void DrawConcentricPair(Graphics& gph, float cx, float cy,
    double outerPct, int outerKind, bool outerKnown,
    double innerPct, int innerKind, bool innerKnown,
    bool drawOuter, bool drawInner) {
    float Ro = DualOuterR(), Ri = DualInnerR();
    if (drawOuter) {
        // track + arc only (no center glyph on outer)
        Color track(g.dark ? Color(255, 0x3A, 0x3E, 0x44) : Color(255, 0xC5, 0xCA, 0xD3));
        Pen ring(track, 3.0f);
        gph.DrawEllipse(&ring, cx - Ro, cy - Ro, Ro * 2, Ro * 2);
        bool live = g.snap.ok && outerKnown;
        float p = live ? (float)std::max(0.0, std::min(100.0, outerPct)) : 0.f;
        if (live && p > 0.3f) {
            Color go = RingColor(outerKind, p);
            Pen arc(go, 3.0f);
            arc.SetStartCap(Gdiplus::LineCapRound);
            arc.SetEndCap(Gdiplus::LineCapRound);
            gph.DrawArc(&arc, cx - Ro, cy - Ro, Ro * 2, Ro * 2, -90.f, 360.f * p / 100.f);
        }
    }
    if (drawInner) {
        Color track(g.dark ? Color(255, 0x3A, 0x3E, 0x44) : Color(255, 0xC5, 0xCA, 0xD3));
        Pen ring(track, 2.5f);
        gph.DrawEllipse(&ring, cx - Ri, cy - Ri, Ri * 2, Ri * 2);
        bool live = g.snap.ok && innerKnown;
        float p = live ? (float)std::max(0.0, std::min(100.0, innerPct)) : 0.f;
        if (live && p > 0.3f) {
            Color go = RingColor(innerKind, p);
            Pen arc(go, 2.5f);
            arc.SetStartCap(Gdiplus::LineCapRound);
            arc.SetEndCap(Gdiplus::LineCapRound);
            gph.DrawArc(&arc, cx - Ri, cy - Ri, Ri * 2, Ri * 2, -90.f, 360.f * p / 100.f);
        }
        // glyph in center (inner ring's kind)
        float innerD = 2.f * Ri - 3.f;
        if (innerD < 6.f) innerD = Ri;
        float glyphSize = innerD * 0.635f / 0.595f;
        DrawRingGlyph(gph, cx, cy, glyphSize, innerKind);
    } else if (drawOuter) {
        // only outer: put glyph of outer kind
        float innerD = 2.f * Ro * 0.55f;
        float glyphSize = innerD * 0.635f / 0.595f;
        DrawRingGlyph(gph, cx, cy, glyphSize, outerKind);
    }
}
static void AddBodyPath(GraphicsPath& body, float w, float hh, float rad, float inset) {
    // inset only on free edges; dock flush side stays at 0 so no white seam on screen.
    float r = rad;
    if (r < 8.f) r = 8.f;
    float L = inset, T = inset, R = w - inset, B = hh - inset;
    if (g.dockEdge == 0) L = 0.f;          // left flush
    else if (g.dockEdge == 1) R = w;       // right flush
    else if (g.dockEdge == 2) T = 0.f;     // top flush
    float rw = R - L, rh = B - T;
    if (g.dockEdge == 2) {
        if (r * 2.f > rw * 0.45f) r = rw * 0.22f;
    } else {
        if (r * 2.f > rh * 0.45f) r = rh * 0.22f;
    }
    if (g.dockEdge == 2) {
        // top flush straight; bottom-left & bottom-right r12
        body.AddLine(L, T, R, T);
        body.AddLine(R, T, R, B - r);
        body.AddArc(R - r * 2.f, B - r * 2.f, r * 2.f, r * 2.f, 0.f, 90.f);
        body.AddLine(R - r, B, L + r, B);
        body.AddArc(L, B - r * 2.f, r * 2.f, r * 2.f, 90.f, 90.f);
        body.AddLine(L, B - r, L, T);
        body.CloseFigure();
    } else if (g.dockEdge == 1) {
        // right flush; far left top+bottom r12
        body.AddArc(L, T, r * 2.f, r * 2.f, 180.f, 90.f);
        body.AddLine(L + r, T, R, T);
        body.AddLine(R, T, R, B);
        body.AddLine(R, B, L + r, B);
        body.AddArc(L, B - r * 2.f, r * 2.f, r * 2.f, 90.f, 90.f);
        body.CloseFigure();
    } else {
        // left flush; far right top+bottom r12
        body.AddLine(L, T, R - r, T);
        body.AddArc(R - r * 2.f, T, r * 2.f, r * 2.f, 270.f, 90.f);
        body.AddLine(R, T + r, R, B - r);
        body.AddArc(R - r * 2.f, B - r * 2.f, r * 2.f, r * 2.f, 0.f, 90.f);
        body.AddLine(R - r, B, L, B);
        body.AddLine(L, B, L, T);
        body.CloseFigure();
    }
}



// ---- quick launch / installed apps ----
static void Place(HWND h);
static void Paint(HWND h, HDC hdc);
static void Repaint(HWND h) {
    if (!h || !IsWindow(h)) return;
    InvalidateRect(h, nullptr, FALSE);
    UpdateWindow(h);
}

static const int kMaxShortcuts = 9;
static const int IDM_MANAGE_APPS = 199;
static const int IDM_LAUNCH_BASE = 200;
static void OpenManageShortcuts();
static void OpenSettings();


struct ShortcutItem {
    std::wstring path;
    std::wstring name;
    std::wstring target;
    HICON icon = nullptr;
    Bitmap* bmp = nullptr;
};

struct InstalledApp {
    std::wstring name;
    std::wstring launchPath;
    std::wstring target;
};

static std::vector<ShortcutItem> g_shortcuts;
static RECT g_quickRect{};
static bool g_quickHit = false;
static int g_launchAnim = -1;
static DWORD g_launchAnimAt = 0;
static const DWORD kLaunchAnimMs = 480;

static std::wstring LowerCopy(std::wstring s) {
    if (!s.empty()) CharLowerBuffW(&s[0], (DWORD)s.size());
    return s;
}

static std::wstring FileNameOf(const std::wstring& p) {
    size_t s = p.find_last_of(L"\\/");
    return s == std::wstring::npos ? p : p.substr(s + 1);
}

static std::wstring FileTitleOf(const std::wstring& p) {
    std::wstring n = FileNameOf(p);
    size_t d = n.find_last_of(L'.');
    if (d != std::wstring::npos && d > 0) n.resize(d);
    return n;
}

static bool EndsWithI(const std::wstring& s, const wchar_t* ext) {
    size_t n = wcslen(ext);
    if (s.size() < n) return false;
    return _wcsicmp(s.c_str() + (s.size() - n), ext) == 0;
}

static std::wstring ShortcutsPath() {
    wchar_t ad[MAX_PATH];
    GetEnvironmentVariableW(L"APPDATA", ad, MAX_PATH);
    std::wstring dir = std::wstring(ad) + L"\\cursor-usage-widget";
    CreateDirectoryW(dir.c_str(), nullptr);
    return dir + L"\\shortcuts.txt";
}

static void FreeShortcutIcons() {
    for (auto& s : g_shortcuts) {
        if (s.bmp) { delete s.bmp; s.bmp = nullptr; }
        if (s.icon) { DestroyIcon(s.icon); s.icon = nullptr; }
    }
}

static HICON IconFromSysList(const std::wstring& path, int shil) {
    SHFILEINFOW fi{};
    if (!SHGetFileInfoW(path.c_str(), 0, &fi, sizeof(fi), SHGFI_SYSICONINDEX))
        return nullptr;
    IImageList* il = nullptr;
    if (FAILED(SHGetImageList(shil, IID_IImageList, (void**)&il)) || !il)
        return nullptr;
    HICON ico = nullptr;
    il->GetIcon(fi.iIcon, ILD_TRANSPARENT, &ico);
    il->Release();
    return ico;
}

static HICON LoadPathIcon(const std::wstring& path) {
    if (path.empty()) return nullptr;
    HICON ico = nullptr;
    if (EndsWithI(path, L".exe")) {
        UINT n = PrivateExtractIconsW(path.c_str(), 0, 256, 256, &ico, nullptr, 1, LR_DEFAULTCOLOR);
        if (!(n && ico)) {
            ico = nullptr;
            n = PrivateExtractIconsW(path.c_str(), 0, 48, 48, &ico, nullptr, 1, LR_DEFAULTCOLOR);
            if (!(n && ico)) ico = nullptr;
        }
    }
    if (!ico) ico = IconFromSysList(path, SHIL_JUMBO);
    if (!ico) ico = IconFromSysList(path, SHIL_EXTRALARGE);
    if (!ico) {
        SHFILEINFOW fi{};
        if (SHGetFileInfoW(path.c_str(), 0, &fi, sizeof(fi), SHGFI_ICON | SHGFI_LARGEICON))
            ico = fi.hIcon;
    }
    return ico;
}

static void AttachIcon(ShortcutItem& it) {
    if (it.bmp) { delete it.bmp; it.bmp = nullptr; }
    if (it.icon) { DestroyIcon(it.icon); it.icon = nullptr; }
    if (!it.target.empty()) it.icon = LoadPathIcon(it.target);
    if (!it.icon) it.icon = LoadPathIcon(it.path);
}

static Bitmap* IconBmp(ShortcutItem& s) {
    if (!s.bmp && s.icon) s.bmp = Bitmap::FromHICON(s.icon);
    return s.bmp;
}

static bool ResolveLnk(const std::wstring& lnk, std::wstring& target) {
    target.clear();
    IShellLinkW* sl = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER, IID_IShellLinkW, (void**)&sl);
    if (FAILED(hr) || !sl) return false;
    IPersistFile* pf = nullptr;
    bool ok = false;
    if (SUCCEEDED(sl->QueryInterface(IID_IPersistFile, (void**)&pf)) && pf) {
        if (SUCCEEDED(pf->Load(lnk.c_str(), STGM_READ))) {
            sl->Resolve(nullptr, SLR_NO_UI | SLR_NOUPDATE | SLR_NOSEARCH);
            wchar_t buf[MAX_PATH] = {};
            WIN32_FIND_DATAW fd{};
            if (SUCCEEDED(sl->GetPath(buf, MAX_PATH, &fd, SLGP_RAWPATH)) && buf[0]) {
                target = buf;
                ok = true;
            }
        }
        pf->Release();
    }
    sl->Release();
    return ok;
}

static void FillShortcutMeta(ShortcutItem& it) {
    it.name = FileTitleOf(it.path);
    if (EndsWithI(it.path, L".lnk"))
        ResolveLnk(it.path, it.target);
    else
        it.target = it.path;
}

static bool SkipInstalled(const std::wstring& name, const std::wstring& target) {
    std::wstring n = LowerCopy(name);
    std::wstring t = LowerCopy(target);
    const wchar_t* bad[] = {
        L"uninstall", L"卸载", L"help", L"帮助", L"readme", L"release notes",
        L"documentation", L"website", L"手册", L"eula", L"license"
    };
    for (auto b : bad)
        if (n.find(b) != std::wstring::npos) return true;
    if (t.find(L"uninstall") != std::wstring::npos) return true;
    if (t.find(L"unins000") != std::wstring::npos) return true;
    return false;
}

static void AddInstalled(std::vector<InstalledApp>& out, std::set<std::wstring>& seen,
                         const std::wstring& name, const std::wstring& launch, const std::wstring& target) {
    if (name.empty() || launch.empty()) return;
    if (SkipInstalled(name, target)) return;
    std::wstring key = LowerCopy(target.empty() ? launch : target);
    if (key.empty() || seen.count(key)) return;
    seen.insert(key);
    InstalledApp a;
    a.name = name;
    a.launchPath = launch;
    a.target = target;
    out.push_back(a);
}

static void ScanDirLnks(const std::wstring& dir, std::vector<InstalledApp>& out, std::set<std::wstring>& seen) {
    WIN32_FIND_DATAW fd{};
    HANDLE h = FindFirstFileW((dir + L"\\*").c_str(), &fd);
    if (h == INVALID_HANDLE_VALUE) return;
    do {
        if (fd.cFileName[0] == L'.' && (fd.cFileName[1] == 0 || (fd.cFileName[1] == L'.' && fd.cFileName[2] == 0)))
            continue;
        std::wstring full = dir + L"\\" + fd.cFileName;
        if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            ScanDirLnks(full, out, seen);
            continue;
        }
        if (!EndsWithI(full, L".lnk")) continue;
        std::wstring target;
        ResolveLnk(full, target);
        AddInstalled(out, seen, FileTitleOf(full), full, target);
    } while (FindNextFileW(h, &fd));
    FindClose(h);
}

static std::wstring StripIconPath(std::wstring s) {
    if (s.size() >= 2 && s.front() == L'"') {
        size_t e = s.find(L'"', 1);
        if (e != std::wstring::npos) s = s.substr(1, e - 1);
    }
    size_t c = s.find_last_of(L',');
    if (c != std::wstring::npos && c > 2) {
        bool idx = true;
        for (size_t i = c + 1; i < s.size(); ++i) {
            wchar_t ch = s[i];
            if (ch == L'-' && i == c + 1) continue;
            if (ch < L'0' || ch > L'9') { idx = false; break; }
        }
        if (idx) s.resize(c);
    }
    return s;
}

static void ScanUninstallKey(HKEY root, const wchar_t* sub, std::vector<InstalledApp>& out, std::set<std::wstring>& seen) {
    HKEY k = nullptr;
    if (RegOpenKeyExW(root, sub, 0, KEY_READ, &k) != ERROR_SUCCESS) return;
    for (DWORD i = 0;; ++i) {
        wchar_t keyName[256];
        DWORD nlen = 256;
        if (RegEnumKeyExW(k, i, keyName, &nlen, nullptr, nullptr, nullptr, nullptr) != ERROR_SUCCESS) break;
        HKEY a = nullptr;
        if (RegOpenKeyExW(k, keyName, 0, KEY_READ, &a) != ERROR_SUCCESS) continue;
        wchar_t display[512] = {};
        DWORD sz = sizeof(display);
        DWORD type = 0;
        if (RegQueryValueExW(a, L"DisplayName", nullptr, &type, (LPBYTE)display, &sz) != ERROR_SUCCESS || !display[0]) {
            RegCloseKey(a);
            continue;
        }
        DWORD sys = 0;
        sz = sizeof(sys);
        if (RegQueryValueExW(a, L"SystemComponent", nullptr, &type, (LPBYTE)&sys, &sz) == ERROR_SUCCESS && sys == 1) {
            RegCloseKey(a);
            continue;
        }
        wchar_t icon[MAX_PATH] = {};
        sz = sizeof(icon);
        if (RegQueryValueExW(a, L"DisplayIcon", nullptr, &type, (LPBYTE)icon, &sz) != ERROR_SUCCESS || !icon[0]) {
            RegCloseKey(a);
            continue;
        }
        std::wstring exe = StripIconPath(icon);
        if (!EndsWithI(exe, L".exe")) {
            RegCloseKey(a);
            continue;
        }
        if (GetFileAttributesW(exe.c_str()) == INVALID_FILE_ATTRIBUTES) {
            RegCloseKey(a);
            continue;
        }
        AddInstalled(out, seen, display, exe, exe);
        RegCloseKey(a);
    }
    RegCloseKey(k);
}

static bool IsSystemInstalledApp(const InstalledApp& a) {
    std::wstring pth = LowerCopy(a.target.empty() ? a.launchPath : a.target);
    std::wstring n = LowerCopy(a.name);
    const wchar_t* keys[] = {
        L"\\windows\\", L"\\system32\\", L"\\syswow64\\",
        L"\\windowsapps\\", L"\\systemapps\\",
        L"\\program files\\windows nt\\",
        L"\\program files\\windows photo viewer\\",
        L"\\windowsdefender\\", L"microsoftedge", L"\\ime\\",
        L"\\inputmethod\\", L"\\accessibility\\",
        L"\\internet explorer\\", L"\\program files\\internet explorer\\",
        L"\\program files (x86)\\internet explorer\\"
    };
    for (auto k : keys) if (pth.find(k) != std::wstring::npos) return true;
    if (pth.find(L"\\windows accessories\\") != std::wstring::npos) return true;
    if (pth.find(L"\\administrative tools\\") != std::wstring::npos) return true;
    if (pth.find(L"\\system tools\\") != std::wstring::npos) return true;
    if (n == L"cmd" || n == L"powershell" || n.find(L"windows powershell") != std::wstring::npos) return true;
    if (n.find(L"internet explorer") != std::wstring::npos) return true;
    if (n.find(L"developer command prompt") != std::wstring::npos) return true;
    if (n.find(L"x64 native tools") != std::wstring::npos || n.find(L"x86 native tools") != std::wstring::npos) return true;
    return false;
}

static std::vector<InstalledApp> EnumInstalledApps() {
    std::vector<InstalledApp> out;
    std::set<std::wstring> seen;
    wchar_t folder[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_PROGRAMS, nullptr, SHGFP_TYPE_CURRENT, folder)))
        ScanDirLnks(folder, out, seen);
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_COMMON_PROGRAMS, nullptr, SHGFP_TYPE_CURRENT, folder)))
        ScanDirLnks(folder, out, seen);
    ScanUninstallKey(HKEY_CURRENT_USER, L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, seen);
    ScanUninstallKey(HKEY_LOCAL_MACHINE, L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, seen);
    ScanUninstallKey(HKEY_LOCAL_MACHINE, L"Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall", out, seen);
    std::sort(out.begin(), out.end(), [](const InstalledApp& a, const InstalledApp& b) {
        return _wcsicmp(a.name.c_str(), b.name.c_str()) < 0;
    });
    return out;
}

static void LoadShortcuts() {
    FreeShortcutIcons();
    g_shortcuts.clear();
    std::ifstream in(ShortcutsPath());
    if (!in) return;
    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.empty()) continue;
        ShortcutItem it;
        it.path = Utf8ToWide(line);
        if (it.path.empty()) continue;
        if (GetFileAttributesW(it.path.c_str()) == INVALID_FILE_ATTRIBUTES) continue;
        FillShortcutMeta(it);
        AttachIcon(it);
        g_shortcuts.push_back(it);
        if ((int)g_shortcuts.size() >= kMaxShortcuts) break;
    }
}

static void SaveShortcuts() {
    std::ofstream out(ShortcutsPath());
    if (!out) return;
    for (auto& s : g_shortcuts) out << WideToUtf8(s.path) << "\n";
}

static bool SameExe(const std::wstring& a, const std::wstring& b) {
    if (a.empty() || b.empty()) return false;
    if (_wcsicmp(a.c_str(), b.c_str()) == 0) return true;
    std::wstring na = FileNameOf(a);
    std::wstring nb = FileNameOf(b);
    if (_wcsicmp(na.c_str(), nb.c_str()) != 0) return false;
    const wchar_t* skip[] = {
        L"explorer.exe", L"svchost.exe", L"runtimebroker.exe",
        L"applicationframehost.exe", L"cursorusage.exe"
    };
    for (auto s : skip)
        if (_wcsicmp(na.c_str(), s) == 0) return false;
    return true;
}

struct FindAppWnd {
    std::wstring target;
    DWORD selfPid = 0;
    HWND best = nullptr;
};

static BOOL CALLBACK EnumAppWndProc(HWND hwnd, LPARAM lp) {
    auto* st = (FindAppWnd*)lp;
    if (hwnd == g.hwnd) return TRUE;
    if (!IsWindowVisible(hwnd) || GetWindow(hwnd, GW_OWNER)) return TRUE;
    LONG_PTR ex = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
    if (ex & WS_EX_TOOLWINDOW) return TRUE;
    RECT rc{};
    GetWindowRect(hwnd, &rc);
    if (rc.right - rc.left < 40 || rc.bottom - rc.top < 40) return TRUE;
    DWORD pid = 0;
    GetWindowThreadProcessId(hwnd, &pid);
    if (!pid || pid == st->selfPid) return TRUE;
    HANDLE ph = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (!ph) return TRUE;
    wchar_t buf[MAX_PATH] = {};
    DWORD n = MAX_PATH;
    BOOL ok = QueryFullProcessImageNameW(ph, 0, buf, &n);
    CloseHandle(ph);
    if (!ok || !buf[0]) return TRUE;
    if (!SameExe(buf, st->target)) return TRUE;
    st->best = hwnd;
    return FALSE;
}

static void ForceForeground(HWND w) {
    if (!w || !IsWindow(w)) return;
    if (IsIconic(w)) ShowWindowAsync(w, SW_RESTORE);
    else ShowWindowAsync(w, SW_SHOW);
    SetWindowPos(w, HWND_TOP, 0, 0, 0, 0,
                 SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW | SWP_ASYNCWINDOWPOS | SWP_NOACTIVATE);
    AllowSetForegroundWindow(ASFW_ANY);
    SetForegroundWindow(w);
}

static HWND FindRunningWindow(const std::wstring& target) {
    if (target.empty() || !EndsWithI(target, L".exe")) return nullptr;
    FindAppWnd st;
    st.target = target;
    st.selfPid = GetCurrentProcessId();
    EnumWindows(EnumAppWndProc, (LPARAM)&st);
    return st.best;
}

struct LaunchJob {
    std::wstring path;
    std::wstring target;
};

static DWORD WINAPI LaunchThread(LPVOID p) {
    auto* job = (LaunchJob*)p;
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    HWND running = FindRunningWindow(job->target);
    if (running) {
        ForceForeground(running);
    } else {
        SHELLEXECUTEINFOW sei{ sizeof(sei) };
        sei.fMask = SEE_MASK_FLAG_NO_UI;
        sei.lpVerb = L"open";
        sei.lpFile = job->path.c_str();
        sei.nShow = SW_SHOWNORMAL;
        ShellExecuteExW(&sei);
    }
    CoUninitialize();
    delete job;
    return 0;
}

static int QuickRowsUsed() {
    int n = (int)g_shortcuts.size();
    if (n <= 0) return 0;
    if (n >= 3) return 3;
    return n;
}

static int QuickColsUsed() {
    int n = (int)g_shortcuts.size();
    if (n <= 0) return 0;
    int rows = QuickRowsUsed();
    int cols = (n + rows - 1) / rows;
    if (cols > 3) cols = 3;
    return cols;
}

static int QuickColW() {
    int cols = QuickColsUsed();
    if (cols <= 0) return 0;
    return QuickPad() * 2 + QuickTilePx() * cols + QuickGap() * (cols - 1);
}

static int QuickContentH() {
    int rows = QuickRowsUsed();
    if (rows <= 0) return 0;
    return QuickPad() * 2 + rows * QuickTilePx() + (rows - 1) * QuickRowGap();
}

static void RoundRectPath(GraphicsPath& path, float x, float y, float w, float h, float r);

struct ManageDlg {
    HWND hwnd = nullptr;
    HWND list = nullptr;
    HWND search = nullptr;
    HWND showSys = nullptr;
    std::vector<InstalledApp> apps;
    std::set<std::wstring> selected;
    std::vector<int> visible;
    bool rebuilding = false;
    bool showSystem = false;
    RECT closeBtn{};
    RECT cancelBtn{};
    RECT addBtn{};
} g_manage;
static const int kManageW = 360;
static const int kManageH = 480;

static std::wstring ManageFilterText() {
    if (!g_manage.search) return L"";
    int n = GetWindowTextLengthW(g_manage.search);
    std::wstring s(n, 0);
    if (n) GetWindowTextW(g_manage.search, &s[0], n + 1);
    return LowerCopy(s);
}

static void ManageRebuildList() {
    if (!g_manage.list) return;
    g_manage.rebuilding = true;
    ListView_DeleteAllItems(g_manage.list);
    g_manage.visible.clear();
    std::wstring f = ManageFilterText();
    for (int i = 0; i < (int)g_manage.apps.size(); ++i) {
        if (!g_manage.showSystem && IsSystemInstalledApp(g_manage.apps[i]))
            continue;
        if (!f.empty() && LowerCopy(g_manage.apps[i].name).find(f) == std::wstring::npos)
            continue;
        LVITEMW it{};
        it.mask = LVIF_TEXT;
        it.iItem = (int)g_manage.visible.size();
        it.pszText = (LPWSTR)g_manage.apps[i].name.c_str();
        int row = ListView_InsertItem(g_manage.list, &it);
        g_manage.visible.push_back(i);
        bool on = g_manage.selected.count(LowerCopy(g_manage.apps[i].launchPath)) != 0;
        ListView_SetCheckState(g_manage.list, row, on);
    }
    g_manage.rebuilding = false;
}

static void ApplyShortcutPaths(const std::vector<std::wstring>& paths) {
    FreeShortcutIcons();
    g_shortcuts.clear();
    for (auto& pth : paths) {
        if ((int)g_shortcuts.size() >= kMaxShortcuts) break;
        ShortcutItem it;
        it.path = pth;
        FillShortcutMeta(it);
        AttachIcon(it);
        g_shortcuts.push_back(it);
    }
    SaveShortcuts();
    if (g.hwnd) {
        Place(g.hwnd);
        Repaint(g.hwnd);
    }
}

static void ManageApplyAndClose() {
    std::vector<std::wstring> next;
    std::set<std::wstring> remain = g_manage.selected;
    for (auto& s : g_shortcuts) {
        auto key = LowerCopy(s.path);
        if (remain.count(key)) {
            next.push_back(s.path);
            remain.erase(key);
        }
    }
    for (auto& a : g_manage.apps) {
        auto key = LowerCopy(a.launchPath);
        if (remain.count(key)) {
            next.push_back(a.launchPath);
            remain.erase(key);
        }
    }
    ApplyShortcutPaths(next);
    if (g_manage.hwnd) DestroyWindow(g_manage.hwnd);
}

static void ManageAddBrowse() {
    wchar_t file[32768] = {};
    OPENFILENAMEW ofn{};
    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = g_manage.hwnd;
    ofn.lpstrFile = file;
    ofn.nMaxFile = 32768;
    ofn.lpstrFilter = L"Programs\0*.exe;*.lnk\0All\0*.*\0";
    ofn.nFilterIndex = 1;
    ofn.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST | OFN_EXPLORER | OFN_ALLOWMULTISELECT;
    if (!GetOpenFileNameW(&ofn)) return;
    std::vector<std::wstring> files;
    wchar_t* p = file;
    std::wstring dir = p;
    p += dir.size() + 1;
    if (!*p) files.push_back(dir);
    else {
        while (*p) {
            files.push_back(dir + L"\\" + p);
            p += wcslen(p) + 1;
        }
    }
    for (auto& fp : files) {
        if ((int)g_manage.selected.size() >= kMaxShortcuts) {
            MessageBoxW(g_manage.hwnd, L"最多 9 个启动程序（3 行 3 列）", L"选择启动程序", MB_OK | MB_ICONINFORMATION);
            break;
        }
        InstalledApp a;
        a.launchPath = fp;
        a.name = FileTitleOf(fp);
        if (EndsWithI(fp, L".lnk")) ResolveLnk(fp, a.target);
        else a.target = fp;
        bool found = false;
        for (auto& x : g_manage.apps) {
            if (_wcsicmp(x.launchPath.c_str(), fp.c_str()) == 0) { found = true; break; }
        }
        if (!found) g_manage.apps.push_back(a);
        g_manage.selected.insert(LowerCopy(fp));
    }
    ManageRebuildList();
}

static LRESULT CALLBACK ManageProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(h, &ps);
        RECT crc; GetClientRect(h, &crc);
        HDC mem = CreateCompatibleDC(hdc);
        BITMAPINFO bmi{};
        bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
        bmi.bmiHeader.biWidth = crc.right;
        bmi.bmiHeader.biHeight = -crc.bottom;
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = BI_RGB;
        void* bits = nullptr;
        HBITMAP bmp = CreateDIBSection(nullptr, &bmi, DIB_RGB_COLORS, &bits, nullptr, 0);
        HGDIOBJ old = SelectObject(mem, bmp);
        {
            Graphics gph(mem);
            gph.SetSmoothingMode(SmoothingModeAntiAlias);
            gph.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
            SolidBrush bg(Color(255, 0xF8, 0xF8, 0xFA));
            gph.FillRectangle(&bg, 0, 0, crc.right, crc.bottom);
            GraphicsPath frame;
            RoundRectPath(frame, 0.5f, 0.5f, (float)crc.right - 1.f, (float)crc.bottom - 1.f, 12.f);
            Pen stroke(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
            gph.DrawPath(&stroke, &frame);
            FontFamily yahei(L"Microsoft YaHei UI");
            const FontFamily* fam = (yahei.GetLastStatus() == Gdiplus::Ok) ? &yahei : FontFamily::GenericSansSerif();
            Font title(fam, 15.f, Gdiplus::FontStyleBold, Gdiplus::UnitPixel);
            Font ui(fam, 12.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
            SolidBrush titleBr(Color(255, 0x20, 0x20, 0x22));
            SolidBrush muted(Color(255, 0x5A, 0x60, 0x69));
            const int pad = 16;
            gph.DrawString(L"\u6dfb\u52a0\u8f6f\u4ef6", -1, &title, PointF((float)pad, (float)pad + 2.f), &titleBr);
            RECT cr = g_manage.closeBtn;
            float cx = (cr.left + cr.right) / 2.f, cy = (cr.top + cr.bottom) / 2.f;
            Pen xp(Color(255, 0x5A, 0x60, 0x69), 1.6f);
            xp.SetStartCap(Gdiplus::LineCapRound);
            xp.SetEndCap(Gdiplus::LineCapRound);
            gph.DrawLine(&xp, cx - 5.f, cy - 5.f, cx + 5.f, cy + 5.f);
            gph.DrawLine(&xp, cx + 5.f, cy - 5.f, cx - 5.f, cy + 5.f);
            // list card
            GraphicsPath card;
            RoundRectPath(card, (float)pad + 0.5f, 122.5f, (float)(kManageW - pad * 2) - 1.f, 284.f, 8.f);
            SolidBrush white(Color(255, 255, 255, 255));
            gph.FillPath(&white, &card);
            Pen cb(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
            gph.DrawPath(&cb, &card);
            // cancel secondary
            {
                RECT ar = g_manage.cancelBtn;
                GraphicsPath ap;
                RoundRectPath(ap, (float)ar.left + 0.5f, (float)ar.top + 0.5f,
                    (float)(ar.right - ar.left) - 1.f, (float)(ar.bottom - ar.top) - 1.f, 6.f);
                SolidBrush af(Color(255, 0xF8, 0xF8, 0xFA));
                gph.FillPath(&af, &ap);
                gph.DrawPath(&cb, &ap);
                StringFormat fmt; fmt.SetAlignment(StringAlignmentCenter); fmt.SetLineAlignment(StringAlignmentCenter);
                gph.DrawString(L"\u53d6\u6d88", -1, &ui,
                    RectF((float)ar.left, (float)ar.top, (float)(ar.right - ar.left), (float)(ar.bottom - ar.top)), &fmt, &titleBr);
            }
            // add primary
            {
                RECT ar = g_manage.addBtn;
                GraphicsPath ap;
                RoundRectPath(ap, (float)ar.left + 0.5f, (float)ar.top + 0.5f,
                    (float)(ar.right - ar.left) - 1.f, (float)(ar.bottom - ar.top) - 1.f, 6.f);
                SolidBrush af(Color(255, 0x2F, 0x6F, 0xED));
                gph.FillPath(&af, &ap);
                StringFormat fmt; fmt.SetAlignment(StringAlignmentCenter); fmt.SetLineAlignment(StringAlignmentCenter);
                SolidBrush ink(Color(255, 255, 255, 255));
                gph.DrawString(L"\u6dfb\u52a0", -1, &ui,
                    RectF((float)ar.left, (float)ar.top, (float)(ar.right - ar.left), (float)(ar.bottom - ar.top)), &fmt, &ink);
            }
        }
        BitBlt(hdc, 0, 0, crc.right, crc.bottom, mem, 0, 0, SRCCOPY);
        SelectObject(mem, old);
        DeleteObject(bmp);
        DeleteDC(mem);
        EndPaint(h, &ps);
        return 0;
    }
    case WM_LBUTTONUP: {
        int x = GET_X_LPARAM(l), y = GET_Y_LPARAM(l);
        auto PtIn = [](const RECT& r, int x, int y) {
            return x >= r.left && x < r.right && y >= r.top && y < r.bottom;
        };
        if (PtIn(g_manage.closeBtn, x, y) || PtIn(g_manage.cancelBtn, x, y)) { DestroyWindow(h); return 0; }
        if (PtIn(g_manage.addBtn, x, y)) { ManageApplyAndClose(); return 0; }
        return 0;
    }
    case WM_CREATE: {
        g_manage.hwnd = h;
        g_manage.showSystem = false;
        const int pad = 16;
        g_manage.closeBtn = { kManageW - pad - 32, pad, kManageW - pad - 4, pad + 28 };
        g_manage.search = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
            WS_CHILD | WS_VISIBLE | WS_TABSTOP | ES_AUTOHSCROLL,
            pad, 52, kManageW - pad * 2, 32, h, (HMENU)200, nullptr, nullptr);
        g_manage.showSys = CreateWindowW(L"BUTTON", L"\u663e\u793a\u7cfb\u7edf\u5e94\u7528",
            WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_AUTOCHECKBOX,
            pad, 92, kManageW - pad * 2, 22, h, (HMENU)205, nullptr, nullptr);
        HWND lv = CreateWindowExW(0, WC_LISTVIEWW, L"",
            WS_CHILD | WS_VISIBLE | WS_TABSTOP | WS_VSCROLL | LVS_REPORT | LVS_SINGLESEL | LVS_SHOWSELALWAYS | LVS_NOCOLUMNHEADER,
            pad + 1, 126, kManageW - pad * 2 - 2, 280, h, (HMENU)201, nullptr, nullptr);
        g_manage.list = lv;
        ListView_SetExtendedListViewStyle(lv, LVS_EX_CHECKBOXES | LVS_EX_FULLROWSELECT | LVS_EX_DOUBLEBUFFER);
        LVCOLUMNW col{};
        col.mask = LVCF_WIDTH | LVCF_TEXT;
        col.cx = kManageW - pad * 2 - 24;
        col.pszText = (LPWSTR)L"\u540d\u79f0";
        ListView_InsertColumn(lv, 0, &col);
        g_manage.cancelBtn = { pad, kManageH - pad - 36, pad + 88, kManageH - pad - 4 };
        g_manage.addBtn = { kManageW - pad - 96, kManageH - pad - 36, kManageW - pad, kManageH - pad - 4 };
        HFONT font = (HFONT)GetStockObject(DEFAULT_GUI_FONT);
        EnumChildWindows(h, [](HWND c, LPARAM f) -> BOOL {
            SendMessageW(c, WM_SETFONT, (WPARAM)f, TRUE);
            return TRUE;
        }, (LPARAM)font);
        g_manage.apps = EnumInstalledApps();
        g_manage.selected.clear();
        for (auto& s : g_shortcuts) {
            g_manage.selected.insert(LowerCopy(s.path));
            bool found = false;
            for (auto& a : g_manage.apps)
                if (_wcsicmp(a.launchPath.c_str(), s.path.c_str()) == 0) { found = true; break; }
            if (!found) {
                InstalledApp a;
                a.launchPath = s.path;
                a.name = s.name.empty() ? FileTitleOf(s.path) : s.name;
                a.target = s.target;
                g_manage.apps.insert(g_manage.apps.begin(), a);
            }
        }
        ManageRebuildList();
        SetFocus(g_manage.search);
        return 0;
    }
    case WM_COMMAND: {
        int id = LOWORD(w);
        int code = HIWORD(w);
        if (id == 200 && code == EN_CHANGE) ManageRebuildList();
        if (id == 205) {
            g_manage.showSystem = (SendMessageW(g_manage.showSys, BM_GETCHECK, 0, 0) == BST_CHECKED);
            ManageRebuildList();
        }
        if (id == 202) ManageApplyAndClose();
        if (id == 203) DestroyWindow(h);
        if (id == 204) ManageAddBrowse();
        return 0;
    }
    case WM_NOTIFY: {
        auto* hdr = (NMHDR*)l;
        if (hdr->idFrom == 201 && hdr->code == LVN_ITEMCHANGED && !g_manage.rebuilding) {
            auto* pnm = (NMLISTVIEW*)l;
            if ((pnm->uChanged & LVIF_STATE) && pnm->iItem >= 0 && pnm->iItem < (int)g_manage.visible.size()) {
                UINT nimg = (pnm->uNewState & LVIS_STATEIMAGEMASK);
                UINT oimg = (pnm->uOldState & LVIS_STATEIMAGEMASK);
                if (nimg != oimg && nimg) {
                    bool on = ListView_GetCheckState(g_manage.list, pnm->iItem) ? true : false;
                    auto key = LowerCopy(g_manage.apps[g_manage.visible[pnm->iItem]].launchPath);
                    if (on) {
                        if ((int)g_manage.selected.size() >= kMaxShortcuts && !g_manage.selected.count(key)) {
                            g_manage.rebuilding = true;
                            ListView_SetCheckState(g_manage.list, pnm->iItem, FALSE);
                            g_manage.rebuilding = false;
                            MessageBoxW(h, L"最多 9 个启动程序（3 行 3 列）", L"选择启动程序", MB_OK | MB_ICONINFORMATION);
                        } else g_manage.selected.insert(key);
                    } else g_manage.selected.erase(key);
                }
            }
        }
        return 0;
    }
    case WM_CLOSE:
        DestroyWindow(h);
        return 0;
    case WM_DESTROY:
        g_manage.hwnd = nullptr;
        g_manage.list = nullptr;
        g_manage.search = nullptr;
        g_manage.showSys = nullptr;
        g_manage.apps.clear();
        g_manage.selected.clear();
        g_manage.visible.clear();
        return 0;
    }
    return DefWindowProcW(h, m, w, l);
}

static void OpenManageShortcuts() {
    if (g_manage.hwnd && IsWindow(g_manage.hwnd)) { ShowWindow(g_manage.hwnd, SW_SHOW); SetForegroundWindow(g_manage.hwnd); return; }
    g_manage.hwnd = nullptr;
    static bool reg = false;
    if (!reg) {
        WNDCLASSEXW wc{ sizeof(wc) };
        wc.lpfnWndProc = ManageProc;
        wc.hInstance = GetModuleHandleW(nullptr);
        wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
        wc.hbrBackground = nullptr;
        wc.lpszClassName = L"CursorUsageQuickManage";
        wc.style = CS_HREDRAW | CS_VREDRAW;
        RegisterClassExW(&wc);
        reg = true;
    }
    HWND hw = CreateWindowExW(WS_EX_TOOLWINDOW | WS_EX_TOPMOST,
        L"CursorUsageQuickManage", L"\u6dfb\u52a0\u8f6f\u4ef6",
        WS_POPUP | WS_CLIPCHILDREN,
        0, 0, kManageW, kManageH,
        nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!hw) return;
    RECT wa; SystemParametersInfo(SPI_GETWORKAREA, 0, &wa, 0);
    int x = wa.left + (wa.right - wa.left - kManageW) / 2;
    int y = wa.top + (wa.bottom - wa.top - kManageH) / 3;
    SetWindowPos(hw, HWND_TOPMOST, x, y, kManageW, kManageH, SWP_SHOWWINDOW);
    HRGN rgn = CreateRoundRectRgn(0, 0, kManageW + 1, kManageH + 1, 24, 24);
    SetWindowRgn(hw, rgn, TRUE);
    UpdateWindow(hw);
}


// ---- Settings window (美工 locked: 320 / cards / #F8F8FA) ----
static const int IDM_SETTINGS = 18;
static const int kSettingsW = 300;
static const int kSettingsWinH = 420;

struct SettingsDlg {
    HWND hwnd = nullptr;
    RECT closeBtn{};
    RECT dockBtn[3]{};
    RECT alphaBtn[4]{};
    RECT modeBtn[2]{};
    RECT botRow{};
    RECT botSwitch{};
    RECT apiRow{};
    RECT apiSwitch{};
    int scrollY = 0;
    int contentH = 0;
    int winH = 420;
    RECT addBtn{};
    RECT emptyHit{};
    RECT shortcutRow[8]{};
    int shortcutRows = 0;
} g_settings;

static void RoundRectPath(GraphicsPath& path, float x, float y, float w, float h, float r) {
    if (r < 0.5f) r = 0.5f;
    if (r * 2 > w) r = w / 2;
    if (r * 2 > h) r = h / 2;
    path.AddArc(x, y, r * 2, r * 2, 180, 90);
    path.AddArc(x + w - r * 2, y, r * 2, r * 2, 270, 90);
    path.AddArc(x + w - r * 2, y + h - r * 2, r * 2, r * 2, 0, 90);
    path.AddArc(x, y + h - r * 2, r * 2, r * 2, 90, 90);
    path.CloseFigure();
}

static int SettingsShortcutListH() {
    if (g_shortcuts.empty()) return 22; // one empty-state line, tight
    int n = (int)g_shortcuts.size();
    if (n > 8) n = 8;
    return n * 36;
}

static int SettingsContentH() {
    const int pad = 10;
    const int titleH = 24;
    const int cardGap = 10;
    const int cardPad = 10;
    const int secLabelH = 16;
    const int secGap = 4;
    const int chipH = 26;
    const int rowH = 26;
    const int addH = 26;
    const int listAddGap = 4;
    int dockCard = cardPad + chipH + cardPad;
    int modeCard = cardPad + chipH + cardPad;
    int visCard = cardPad + rowH + 6 + rowH + cardPad;
    int listH = SettingsShortcutListH();
    int scCard = cardPad + listH + listAddGap + addH + 4;
    return pad + titleH + 8
        + secLabelH + secGap + dockCard + cardGap
        + secLabelH + secGap + modeCard + cardGap
        + secLabelH + secGap + visCard + cardGap
        + secLabelH + secGap + scCard
        + pad;
}

static int SettingsWinH() {
    int ch = SettingsContentH();
    g_settings.contentH = ch;
    g_settings.winH = ch;
    g_settings.scrollY = 0; // long window, no scroll for now
    return ch;
}

static void SettingsLayout(int /*cw*/, int /*ch*/) {
    const int pad = 10;
    const int inner = kSettingsW - pad * 2;
    const int cardGap = 10;
    const int cardPad = 10;
    const int secLabelH = 16;
    const int secGap = 4;
    const int chipH = 26;
    const int listAddGap = 4;
    const int addH = 26;

    g_settings.closeBtn = { kSettingsW - pad - 32, pad, kSettingsW - pad - 4, pad + 24 };

    int y = pad + 24 + 8;

    // Card 1: dock — title above card
    y += secLabelH + secGap;
    int contentTop = y + cardPad;
    int bw = (inner - cardPad * 2 - 8 * 2) / 3;
    int bx = pad + cardPad;
    for (int i = 0; i < 3; ++i) {
        int x0 = bx + i * (bw + 8);
        g_settings.dockBtn[i] = { x0, contentTop, x0 + bw, contentTop + chipH };
    }
    y = contentTop + chipH + cardPad + cardGap;


    // 显示模式
    // display mode
    y += secLabelH + secGap;
    contentTop = y + cardPad;
    int mw = (inner - cardPad * 2 - 8) / 2;
    int mx = pad + cardPad;
    g_settings.modeBtn[0] = { mx, contentTop, mx + mw, contentTop + chipH };
    g_settings.modeBtn[1] = { mx + mw + 8, contentTop, mx + mw + 8 + mw, contentTop + chipH };
    y = contentTop + chipH + cardPad + cardGap;

    // visibility Bot+Api
    y += secLabelH + secGap;
    contentTop = y + cardPad;
    g_settings.botRow = { pad + cardPad, contentTop, pad + inner - cardPad, contentTop + 26 };
    g_settings.botSwitch = { g_settings.botRow.right - 44, contentTop + 2, g_settings.botRow.right, contentTop + 2 + 22 };
    contentTop += 26 + 6;
    g_settings.apiRow = { pad + cardPad, contentTop, pad + inner - cardPad, contentTop + 26 };
    g_settings.apiSwitch = { g_settings.apiRow.right - 44, contentTop + 2, g_settings.apiRow.right, contentTop + 2 + 22 };
    y = contentTop + 26 + cardPad + cardGap;

    // shortcuts
    y += secLabelH + secGap;
    contentTop = y + cardPad;
    int listH = SettingsShortcutListH();
    g_settings.emptyHit = { pad + cardPad, contentTop, pad + inner - cardPad, contentTop + listH };
    g_settings.shortcutRows = 0;
    if (!g_shortcuts.empty()) {
        int n = (int)g_shortcuts.size();
        if (n > 8) n = 8;
        g_settings.shortcutRows = n;
        for (int i = 0; i < n; ++i) {
            int ry = contentTop + i * 36;
            g_settings.shortcutRow[i] = { pad + cardPad, ry, pad + inner - cardPad, ry + 36 };
        }
    }
    int addY = contentTop + listH + listAddGap;
    g_settings.addBtn = { pad + cardPad, addY, pad + cardPad + 72, addY + addH };
}

static void DrawSettingsChip(Graphics& gph, const RECT& rc, const wchar_t* label, bool on, bool blueSel, Font& f) {
    float x = (float)rc.left, y = (float)rc.top, w = (float)(rc.right - rc.left), h = (float)(rc.bottom - rc.top);
    GraphicsPath path;
    RoundRectPath(path, x + 0.5f, y + 0.5f, w - 1.f, h - 1.f, 6.f);
    Color fillC = Color(255, 255, 255, 255);
    Color borderC = Color(255, 0xD8, 0xDC, 0xE1);
    float borderW = 1.f;
    if (on) {
        if (blueSel) {
            fillC = Color(255, 0xE8, 0xF1, 0xFF);
            borderC = Color(255, 0x2F, 0x6F, 0xED);
            borderW = 1.6f;
        } else {
            fillC = Color(255, 0xEE, 0xF2, 0xF7);
            borderC = Color(255, 0xA8, 0xB2, 0xC0);
            borderW = 1.4f;
        }
    }
    SolidBrush fill(fillC);
    gph.FillPath(&fill, &path);
    Pen border(borderC, borderW);
    gph.DrawPath(&border, &path);
    SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
    StringFormat fmt;
    fmt.SetAlignment(StringAlignmentCenter);
    fmt.SetLineAlignment(StringAlignmentCenter);
    gph.DrawString(label, -1, &f, RectF(x, y, w, h), &fmt, &ink);
}

static void DrawSettingsCard(Graphics& gph, float x, float y, float w, float h) {
    GraphicsPath path;
    RoundRectPath(path, x + 0.5f, y + 0.5f, w - 1.f, h - 1.f, 8.f);
    SolidBrush fill(Color(255, 255, 255, 255));
    gph.FillPath(&fill, &path);
    Pen border(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
    gph.DrawPath(&border, &path);
}

static void PaintSettings(HWND h) {
    RECT crc; GetClientRect(h, &crc);
    int cw = crc.right, ch = crc.bottom;
    if (cw <= 0 || ch <= 0) return;
    SettingsWinH(); // refresh contentH / clamp scroll
    SettingsLayout(kSettingsW, g_settings.contentH);
    PAINTSTRUCT ps;
    HDC hdc = BeginPaint(h, &ps);
    HDC mem = CreateCompatibleDC(hdc);
    BITMAPINFO bmi{};
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = cw;
    bmi.bmiHeader.biHeight = -ch;
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 32;
    bmi.bmiHeader.biCompression = BI_RGB;
    void* bits = nullptr;
    HBITMAP bmp = CreateDIBSection(nullptr, &bmi, DIB_RGB_COLORS, &bits, nullptr, 0);
    HGDIOBJ old = SelectObject(mem, bmp);
    {
        Graphics gph(mem);
        gph.SetSmoothingMode(SmoothingModeAntiAlias);
        gph.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
        gph.SetPixelOffsetMode(PixelOffsetModeHighQuality);

        SolidBrush bg(Color(255, 0xF8, 0xF8, 0xFA));
        gph.FillRectangle(&bg, 0, 0, cw, ch);
        GraphicsPath frame;
        RoundRectPath(frame, 0.5f, 0.5f, (float)cw - 1.f, (float)ch - 1.f, 12.f);
        Pen stroke(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
        gph.DrawPath(&stroke, &frame);

        FontFamily yahei(L"Microsoft YaHei UI");
        const FontFamily* fam = (yahei.GetLastStatus() == Gdiplus::Ok) ? &yahei : FontFamily::GenericSansSerif();
        Font title(fam, 15.f, Gdiplus::FontStyleBold, Gdiplus::UnitPixel);
        Font sec(fam, 11.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        Font ui(fam, 12.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        Font uiSm(fam, 11.f, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        SolidBrush titleBr(Color(255, 0x20, 0x20, 0x22));
        SolidBrush secBr(Color(255, 0x5A, 0x60, 0x69));
        SolidBrush muted(Color(255, 0x78, 0x7D, 0x85));

        const int pad = 10;
        const int inner = kSettingsW - pad * 2;
        const int cardGap = 10;
        const int cardPad = 10;
        const int chipH = 26;

        gph.DrawString(L"\u8bbe\u7f6e", -1, &title, PointF((float)pad, (float)pad + 2.f), &titleBr);
        {
            RECT cr = g_settings.closeBtn;
            float cx = (cr.left + cr.right) / 2.f;
            float cy = (cr.top + cr.bottom) / 2.f;
            Pen xp(Color(255, 0x5A, 0x60, 0x69), 1.6f);
            xp.SetStartCap(Gdiplus::LineCapRound);
            xp.SetEndCap(Gdiplus::LineCapRound);
            gph.DrawLine(&xp, cx - 5.f, cy - 5.f, cx + 5.f, cy + 5.f);
            gph.DrawLine(&xp, cx + 5.f, cy - 5.f, cx - 5.f, cy + 5.f);
        }

        int y = pad + 28 + 12;
        const int secLabelH = 16;
        const int secGap = 4;
        const int listAddGap = 4;
        const int addH = 28;

        // --- 贴边 ---
        {
            gph.DrawString(L"\u8d34\u8fb9", -1, &sec, PointF((float)pad, (float)y), &secBr);
            y += secLabelH + secGap;
            int cardH = cardPad + chipH + cardPad;
            DrawSettingsCard(gph, (float)pad, (float)y, (float)inner, (float)cardH);
            const wchar_t* docks[] = { L"\u5de6", L"\u53f3", L"\u9876" };
            for (int i = 0; i < 3; ++i)
                DrawSettingsChip(gph, g_settings.dockBtn[i], docks[i], g.dockEdge == i, true, ui);
            y += cardH + cardGap;
        }


                // --- 显示模式 ---
        {
            gph.DrawString(L"\u663e\u793a\u6a21\u5f0f", -1, &sec, PointF((float)pad, (float)y), &secBr);
            y += secLabelH + secGap;
            int cardH = cardPad + chipH + cardPad;
            DrawSettingsCard(gph, (float)pad, (float)y, (float)inner, (float)cardH);
            DrawSettingsChip(gph, g_settings.modeBtn[0], L"\u56db\u73af", g.ringMode == 0, true, ui);
            DrawSettingsChip(gph, g_settings.modeBtn[1], L"\u53cc\u73af", g.ringMode == 1, true, ui);
            y += cardH + cardGap;
        }

        // --- visibility Bot + Api ---
        {
            gph.DrawString(L"\u663e\u793a\u9879", -1, &sec, PointF((float)pad, (float)y), &secBr);
            y += secLabelH + secGap;
            int cardH = cardPad + 26 + 6 + 26 + cardPad;
            DrawSettingsCard(gph, (float)pad, (float)y, (float)inner, (float)cardH);
            RECT rc = g_settings.botRow;
            gph.DrawString(L"\u663e\u793a Bot \u7528\u91cf", -1, &ui,
                PointF((float)rc.left, (float)rc.top + 3.f), &titleBr);
            float tx = (float)g_settings.botSwitch.left;
            float ty = (float)g_settings.botSwitch.top;
            float tw = 44.f, th = 22.f;
            GraphicsPath tpath;
            RoundRectPath(tpath, tx, ty, tw, th, th / 2.f);
            SolidBrush tfill(g.showBot ? Color(255, 0x2F, 0x6F, 0xED) : Color(255, 0xD0, 0xD4, 0xDA));
            gph.FillPath(&tfill, &tpath);
            float knob = th - 4.f;
            float kx = g.showBot ? (tx + tw - knob - 2.f) : (tx + 2.f);
            SolidBrush knobBr(Color(255, 255, 255, 255));
            gph.FillEllipse(&knobBr, kx, ty + 2.f, knob, knob);
            rc = g_settings.apiRow;
            gph.DrawString(L"\u663e\u793a Api \u7528\u91cf", -1, &ui,
                PointF((float)rc.left, (float)rc.top + 3.f), &titleBr);
            tx = (float)g_settings.apiSwitch.left;
            ty = (float)g_settings.apiSwitch.top;
            GraphicsPath tpath2;
            RoundRectPath(tpath2, tx, ty, tw, th, th / 2.f);
            SolidBrush tfill2(g.showApi ? Color(255, 0x2F, 0x6F, 0xED) : Color(255, 0xD0, 0xD4, 0xDA));
            gph.FillPath(&tfill2, &tpath2);
            kx = g.showApi ? (tx + tw - knob - 2.f) : (tx + 2.f);
            gph.FillEllipse(&knobBr, kx, ty + 2.f, knob, knob);
            y += cardH + cardGap;
        }

        // --- 快捷方式 ---
        {
            gph.DrawString(L"\u5feb\u6377\u65b9\u5f0f", -1, &sec, PointF((float)pad, (float)y), &secBr);
            y += secLabelH + secGap;
            int listH = SettingsShortcutListH();
            int cardH = cardPad + listH + listAddGap + addH + 4;
            DrawSettingsCard(gph, (float)pad, (float)y, (float)inner, (float)cardH);
            if (g_shortcuts.empty()) {
                StringFormat fmt;
                fmt.SetAlignment(StringAlignmentCenter);
                fmt.SetLineAlignment(StringAlignmentCenter);
                RECT er = g_settings.emptyHit;
                gph.DrawString(L"\u6dfb\u52a0\u5e38\u7528\u8f6f\u4ef6", -1, &uiSm,
                    RectF((float)er.left, (float)er.top, (float)(er.right - er.left), (float)(er.bottom - er.top)),
                    &fmt, &muted);
            } else {
                int n = g_settings.shortcutRows;
                {
                    HDC gdc = gph.GetHDC();
                    for (int i = 0; i < n; ++i) {
                        RECT rr = g_settings.shortcutRow[i];
                        int icon = 28;
                        int iy = rr.top + (36 - icon) / 2;
                        int ix = rr.left;
                        if (g_shortcuts[i].icon)
                            DrawIconEx(gdc, ix, iy, g_shortcuts[i].icon, icon, icon, 0, nullptr, DI_NORMAL);
                    }
                    gph.ReleaseHDC(gdc);
                }
                for (int i = 0; i < n; ++i) {
                    RECT rr = g_settings.shortcutRow[i];
                    int icon = 28;
                    int iy = rr.top + (36 - icon) / 2;
                    int ix = rr.left;
                    if (!g_shortcuts[i].icon) {
                        SolidBrush ph(Color(255, 0xE8, 0xEA, 0xEE));
                        gph.FillRectangle(&ph, (float)ix, (float)iy, (float)icon, (float)icon);
                    }
                    std::wstring name = g_shortcuts[i].name;
                    if (name.empty()) {
                        const wchar_t* nms = g_shortcuts[i].path.c_str();
                        for (const wchar_t* q = nms; *q; ++q)
                            if (*q == L'\\' || *q == L'/') nms = q + 1;
                        name = nms;
                    }
                    gph.DrawString(name.c_str(), -1, &uiSm,
                        PointF((float)(ix + icon + 10), (float)(rr.top + 8)), &titleBr);
                }
            }
            {
                RECT ar = g_settings.addBtn;
                float x = (float)ar.left, yy = (float)ar.top, ww = (float)(ar.right - ar.left), hh = (float)(ar.bottom - ar.top);
                GraphicsPath ap;
                RoundRectPath(ap, x + 0.5f, yy + 0.5f, ww - 1.f, hh - 1.f, 6.f);
                SolidBrush af(Color(255, 0xF8, 0xF8, 0xFA));
                gph.FillPath(&af, &ap);
                Pen ab(Color(255, 0xD8, 0xDC, 0xE1), 1.f);
                gph.DrawPath(&ab, &ap);
                StringFormat fmt;
                fmt.SetAlignment(StringAlignmentCenter);
                fmt.SetLineAlignment(StringAlignmentCenter);
                SolidBrush ink(Color(255, 0x20, 0x20, 0x22));
                gph.DrawString(L"\u6dfb\u52a0", -1, &uiSm, RectF(x, yy, ww, hh), &fmt, &ink);
            }
        }

    }
    BitBlt(hdc, 0, 0, cw, ch, mem, 0, 0, SRCCOPY);
    SelectObject(mem, old);
    DeleteObject(bmp);
    DeleteDC(mem);
    EndPaint(h, &ps);
}

static bool PtIn(const RECT& r, int x, int y) {
    return x >= r.left && x < r.right && y >= r.top && y < r.bottom;
}

static void SettingsResize(HWND h) {
    if (!h || !IsWindow(h)) return;
    int hgt = SettingsWinH();
    RECT rc; GetWindowRect(h, &rc);
    SetWindowPos(h, nullptr, rc.left, rc.top, kSettingsW, hgt, SWP_NOZORDER | SWP_NOACTIVATE);
    HRGN rgn = CreateRoundRectRgn(0, 0, kSettingsW + 1, hgt + 1, 24, 24);
    SetWindowRgn(h, rgn, TRUE);
    SettingsLayout(kSettingsW, g_settings.contentH);
    InvalidateRect(h, nullptr, FALSE);
}

static void SettingsApplyMain(bool needPlace) {
    if (!g.hwnd) return;
    if (needPlace) Place(g.hwnd);
    Repaint(g.hwnd);
}

static LRESULT CALLBACK SettingsProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
    case WM_CREATE:
        g_settings.hwnd = h;
        {
            g_settings.scrollY = 0;
            int hgt = SettingsWinH();
            SettingsLayout(kSettingsW, g_settings.contentH);
            HRGN rgn = CreateRoundRectRgn(0, 0, kSettingsW + 1, hgt + 1, 24, 24);
            SetWindowRgn(h, rgn, TRUE);
        }
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT:
        PaintSettings(h);
        return 0;
    case WM_LBUTTONDOWN: {
        int x = GET_X_LPARAM(l), y = GET_Y_LPARAM(l);
        if (PtIn(g_settings.closeBtn, x, y)) { DestroyWindow(h); return 0; }
        if (y < 34 && !PtIn(g_settings.closeBtn, x, y)) {
            ReleaseCapture();
            SendMessageW(h, WM_NCLBUTTONDOWN, HTCAPTION, 0);
            return 0;
        }
        return 0;
    }
    case WM_LBUTTONUP: {
        int x = GET_X_LPARAM(l), y = GET_Y_LPARAM(l);
        if (PtIn(g_settings.closeBtn, x, y)) { DestroyWindow(h); return 0; }
        for (int i = 0; i < 3; ++i) if (PtIn(g_settings.dockBtn[i], x, y)) {
            if (g.dockEdge != i) { g.dockEdge = i; g.y = -1; }
            SaveConfig(); SettingsApplyMain(true); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        for (int i = 0; i < 2; ++i) if (PtIn(g_settings.modeBtn[i], x, y)) {
            g.ringMode = i; SaveConfig(); SettingsApplyMain(true); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        if (PtIn(g_settings.botRow, x, y) || PtIn(g_settings.botSwitch, x, y)) {
            g.showBot = !g.showBot; SaveConfig(); SettingsApplyMain(true); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        if (PtIn(g_settings.apiRow, x, y) || PtIn(g_settings.apiSwitch, x, y)) {
            g.showApi = !g.showApi; SaveConfig(); SettingsApplyMain(true); InvalidateRect(h, nullptr, FALSE); return 0;
        }
        if (PtIn(g_settings.addBtn, x, y) || (g_shortcuts.empty() && PtIn(g_settings.emptyHit, x, y))) {
            OpenManageShortcuts();
            return 0;
        }
        return 0;
    }
    case WM_CLOSE:
        DestroyWindow(h);
        return 0;
    case WM_DESTROY:
        if (g_settings.hwnd == h) g_settings.hwnd = nullptr;
        return 0;
    }
    return DefWindowProcW(h, m, w, l);
}

static void OpenSettings() {
    if (g_settings.hwnd && !IsWindow(g_settings.hwnd))
        g_settings.hwnd = nullptr;
    if (g_settings.hwnd && IsWindow(g_settings.hwnd)) {
        SettingsResize(g_settings.hwnd);
        ShowWindow(g_settings.hwnd, SW_SHOW);
        SetForegroundWindow(g_settings.hwnd);
        return;
    }
    g_settings.hwnd = nullptr;
    static ATOM atom = 0;
    if (!atom) {
        WNDCLASSEXW wc{ sizeof(wc) };
        wc.lpfnWndProc = SettingsProc;
        wc.hInstance = GetModuleHandleW(nullptr);
        wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
        wc.hbrBackground = nullptr;
        wc.lpszClassName = L"CursorUsageSettings";
        wc.style = CS_HREDRAW | CS_VREDRAW;
        atom = RegisterClassExW(&wc);
    }
    int hgt = SettingsWinH();
    RECT wa; SystemParametersInfo(SPI_GETWORKAREA, 0, &wa, 0);
    int x = wa.left + (wa.right - wa.left - kSettingsW) / 2;
    int y = wa.top + (wa.bottom - wa.top - hgt) / 3;
    HWND hw = CreateWindowExW(WS_EX_TOOLWINDOW | WS_EX_TOPMOST,
        L"CursorUsageSettings", L"\u8bbe\u7f6e",
        WS_POPUP | WS_CLIPCHILDREN,
        x, y, kSettingsW, hgt,
        nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!hw) return;
    ShowWindow(hw, SW_SHOW);
    UpdateWindow(hw);
}



static void DrawQuickLaunch(Graphics& gph, Font& ui, float x, float y, float qw, float qh) {
    g_quickRect.left = (LONG)x;
    g_quickRect.top = (LONG)y;
    g_quickRect.right = (LONG)(x + qw);
    g_quickRect.bottom = (LONG)(y + qh);
    g_quickHit = true;
    if (g_shortcuts.empty()) {
        g_quickHit = false;
        return;
    }
    int icon = QuickIconPx();
    int tile = QuickTilePx();
    int gap = QuickGap();
    int pad = QuickPad();
    int rowGap = QuickRowGap();
    int rows = QuickRowsUsed();
    int cols = QuickColsUsed();
    if (rows < 1) rows = 1;
    if (cols < 1) cols = 1;
    (void)cols;
    DWORD now = GetTickCount();
    auto oldInterp = gph.GetInterpolationMode();
    gph.SetInterpolationMode(InterpolationModeHighQualityBicubic);
    float rad = (float)S(10);
    if (rad < 8.f) rad = 8.f;
    for (int i = 0; i < (int)g_shortcuts.size(); ++i) {
        int col = i / rows;
        int row = i % rows;
        float ix = x + pad + col * (tile + gap);
        float iy = y + pad + row * (tile + rowGap);
        float scale = 1.f;
        float t = -1.f;
        if (i == g_launchAnim && g_launchAnimAt) {
            t = (float)(now - g_launchAnimAt) / (float)kLaunchAnimMs;
            if (t < 0.f) t = 0.f;
            if (t > 1.f) t = 1.f;
            if (t < 0.18f) {
                float u = t / 0.18f;
                scale = 1.f - 0.08f * u;
            } else if (t < 0.45f) {
                float u = (t - 0.18f) / 0.27f;
                scale = 0.92f + 0.14f * u;
            } else {
                float u = (t - 0.45f) / 0.55f;
                scale = 1.06f - 0.06f * u;
            }
        }
        float tw = (float)tile * scale;
        float th = tw;
        float tx = ix + ((float)tile - tw) * 0.5f;
        float ty = iy + ((float)tile - th) * 0.5f;
        BYTE fillA = g.dark ? (BYTE)48 : (BYTE)210;
        Color fill = g.dark ? Color(fillA, 0x48, 0x48, 0x50) : Color(fillA, 0xF4, 0xF5, 0xF7);
        Color stroke = g.dark ? Color(255, 0x58, 0x58, 0x62) : Color(255, 0xD2, 0xD6, 0xDC);
        if (t >= 0.f) {
            BYTE glowA = (BYTE)((1.f - t) * 90);
            Color glow = g.dark ? Color(glowA, 0x6A, 0xC8, 0xFF) : Color(glowA, 0x3A, 0x8A, 0xE8);
            SolidBrush gb(glow);
            FillRoundRect(gph, tx - 3.f, ty - 3.f, tw + 6.f, th + 6.f, rad + 3.f, gb);
            stroke = g.dark ? Color(255, 0x7A, 0xC8, 0xF0) : Color(255, 0x4A, 0x8E, 0xD8);
        }
        SolidBrush tileBr(fill);
        FillRoundRect(gph, tx, ty, tw, th, rad, tileBr);
        Pen tilePen(stroke, 1.15f);
        tilePen.SetAlignment(PenAlignmentInset);
        StrokeRoundRect(gph, tx, ty, tw, th, rad, tilePen);
        float sz = (float)icon * scale;
        float ox = tx + ((tw - sz) * 0.5f);
        float oy = ty + ((th - sz) * 0.5f);
        Bitmap* bmp = IconBmp(g_shortcuts[i]);
        if (bmp) {
            gph.DrawImage(bmp, ox, oy, sz, sz);
        } else if (g_shortcuts[i].icon) {
            HDC hdc = gph.GetHDC();
            DrawIconEx(hdc, (int)(ox + 0.5f), (int)(oy + 0.5f), g_shortcuts[i].icon,
                       (int)(sz + 0.5f), (int)(sz + 0.5f), 0, nullptr, DI_NORMAL);
            gph.ReleaseHDC(hdc);
        } else {
            SolidBrush br(Color(255, 0xD8, 0xDC, 0xE1));
            gph.FillRectangle(&br, ox, oy, sz, sz);
        }
    }
    gph.SetInterpolationMode(oldInterp);
}

static bool QuickLayout(float* qx, float* qy, float* qw, float* qh) {
    int qpw = QuickColW();
    if (qpw <= 0 || !g.expanded) return false;
    int usageW = S(BASE_PANEL_W);
    int split = S(16);
    *qw = (float)qpw;
    *qh = (float)QuickContentH();
    if (*qh < (float)S(40)) *qh = (float)S(40);
    *qy = (float)S(46);
    *qx = (float)(usageW + split);
    return true;
}

static POINT ClientFromWindow(HWND h, POINT screen) {
    RECT wr{};
    GetWindowRect(h, &wr);
    screen.x -= wr.left;
    screen.y -= wr.top;
    return screen;
}

static POINT ClientFromLParam(HWND h, LPARAM l, bool screen) {
    POINT pt{ GET_X_LPARAM(l), GET_Y_LPARAM(l) };
    if (screen) return ClientFromWindow(h, pt);
    return pt;
}

static int HitQuickIndex(int mx, int my) {
    if (g_shortcuts.empty()) return -2;
    float x = 0, y = 0, qw = 0, qh = 0;
    if (!QuickLayout(&x, &y, &qw, &qh)) return -2;
    if (mx < x || my < y || mx >= x + qw || my >= y + qh) return -2;
    int tile = QuickTilePx();
    int gap = QuickGap();
    int pad = QuickPad();
    int rowGap = QuickRowGap();
    int rows = QuickRowsUsed();
    if (rows < 1) rows = 1;
    int hitX = gap / 2 + 4;
    int hitY = rowGap / 2 + 4;
    int best = -1;
    int bestD = 0x7fffffff;
    for (int i = 0; i < (int)g_shortcuts.size(); ++i) {
        int col = i / rows;
        int row = i % rows;
        int ix = (int)(x + pad + col * (tile + gap));
        int iy = (int)(y + pad + row * (tile + rowGap));
        if (mx >= ix - hitX && my >= iy - hitY && mx < ix + tile + hitX && my < iy + tile + hitY)
            return i;
        int cx = ix + tile / 2;
        int cy = iy + tile / 2;
        int dx = mx - cx, dy = my - cy;
        int d = dx * dx + dy * dy;
        if (d < bestD) { bestD = d; best = i; }
    }
    return best;
}

static int HitQuickFromMsg(HWND h, LPARAM l, bool screen) {
    POINT a = ClientFromLParam(h, l, screen);
    int qi = HitQuickIndex(a.x, a.y);
    if (qi >= 0) return qi;
    POINT sp{};
    GetCursorPos(&sp);
    POINT b = ClientFromWindow(h, sp);
    return HitQuickIndex(b.x, b.y);
}

static void BeginLaunchAnim(int idx) {
    g_launchAnim = idx;
    g_launchAnimAt = GetTickCount();
    g.holdUntil = g_launchAnimAt + kLaunchAnimMs + 200;
    if (g.hwnd) {
        SetTimer(g.hwnd, 3, 16, nullptr);
        Repaint(g.hwnd);
    }
}

static HWND g_hotspot[9]{};
static void LaunchShortcut(int idx);

static LRESULT CALLBACK HotspotProc(HWND hs, UINT m, WPARAM w, LPARAM l) {
    if (m == WM_LBUTTONDOWN || m == WM_LBUTTONDBLCLK) {
        int idx = (int)GetWindowLongPtrW(hs, GWLP_USERDATA);
        LaunchShortcut(idx);
        return 0;
    }
    if (m == WM_PAINT) {
        PAINTSTRUCT ps;
        BeginPaint(hs, &ps);
        EndPaint(hs, &ps);
        return 0;
    }
    if (m == WM_ERASEBKGND) return 1;
    if (m == WM_NCHITTEST) return HTCLIENT;
    return DefWindowProcW(hs, m, w, l);
}

static void SyncHotspots(HWND parent) {
    if (!parent) return;
    static bool reg = false;
    if (!reg) {
        WNDCLASSEXW wc{ sizeof(wc) };
        wc.lpfnWndProc = HotspotProc;
        wc.hInstance = GetModuleHandleW(nullptr);
        wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
        wc.hbrBackground = (HBRUSH)GetStockObject(NULL_BRUSH);
        wc.lpszClassName = L"CursorUsageHotspot";
        RegisterClassExW(&wc);
        reg = true;
    }
    float x = 0, y = 0, qw = 0, qh = 0;
    bool show = g.expanded && QuickLayout(&x, &y, &qw, &qh) && !g_shortcuts.empty();
    int tile = QuickTilePx();
    int gap = QuickGap();
    int pad = QuickPad();
    int rowGap = QuickRowGap();
    int rows = QuickRowsUsed();
    if (rows < 1) rows = 1;
    for (int i = 0; i < kMaxShortcuts; ++i) {
        if (!show || i >= (int)g_shortcuts.size()) {
            if (g_hotspot[i]) ShowWindow(g_hotspot[i], SW_HIDE);
            continue;
        }
        int col = i / rows;
        int row = i % rows;
        int ix = (int)(x + pad + col * (tile + gap));
        int iy = (int)(y + pad + row * (tile + rowGap));
        if (!g_hotspot[i]) {
            g_hotspot[i] = CreateWindowExW(WS_EX_LAYERED,
                L"CursorUsageHotspot", L"", WS_CHILD | WS_VISIBLE,
                ix, iy, tile, tile, parent, nullptr, GetModuleHandleW(nullptr), nullptr);
            if (g_hotspot[i])
                SetLayeredWindowAttributes(g_hotspot[i], 0, 1, LWA_ALPHA);
        } else {
            SetWindowPos(g_hotspot[i], HWND_TOP, ix, iy, tile, tile,
                         SWP_NOACTIVATE | SWP_SHOWWINDOW);
        }
        if (g_hotspot[i]) SetWindowLongPtrW(g_hotspot[i], GWLP_USERDATA, i);
    }
}

static void DestroyHotspots() {
    for (int i = 0; i < kMaxShortcuts; ++i) {
        if (g_hotspot[i]) {
            DestroyWindow(g_hotspot[i]);
            g_hotspot[i] = nullptr;
        }
    }
}

static void LaunchShortcut(int idx) {
    if (idx < 0 || idx >= (int)g_shortcuts.size()) return;
    BeginLaunchAnim(idx);
    auto* job = new LaunchJob;
    job->path = g_shortcuts[idx].path;
    job->target = g_shortcuts[idx].target;
    HANDLE th = CreateThread(nullptr, 0, LaunchThread, job, 0, nullptr);
    if (th) CloseHandle(th);
    else {
        delete job;
        SHELLEXECUTEINFOW sei{ sizeof(sei) };
        sei.fMask = SEE_MASK_ASYNCOK | SEE_MASK_FLAG_NO_UI;
        sei.lpVerb = L"open";
        sei.lpFile = g_shortcuts[idx].path.c_str();
        sei.nShow = SW_SHOWNORMAL;
        ShellExecuteExW(&sei);
    }
}

static void DrawQuickEmpty(Graphics& gph, Font& ui, float x, float y, float qw, float qh) {
    DrawQuickLaunch(gph, ui, x, y, qw, qh);
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

    HDC compat = hdc ? hdc : GetDC(h);
    void* bits = nullptr;
    HDC mem = CreateCompatibleDC(compat);
    HBITMAP bmp = MakeDib(w, hh, &bits);
    HGDIOBJ old = bmp ? SelectObject(mem, bmp) : nullptr;
    HDC target = bmp ? mem : compat;

    {
    Graphics gph(target);
    gph.SetSmoothingMode(SmoothingModeAntiAlias);
    gph.SetPixelOffsetMode(PixelOffsetModeHighQuality);
    gph.SetCompositingQuality(CompositingQualityHighQuality);
    gph.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
    BYTE ba = 255; // opacity feature removed
    if (ba < 1) ba = 1;
    Color washTop = g.dark ? Color(255, 0x2A, 0x2A, 0x2E) : Color(255, 255, 255, 255);
    Color washBot = g.dark ? Color(255, 0x1E, 0x1E, 0x22) : Color(255, 238, 239, 242);
    gph.Clear(Color(0, 0, 0, 0));
    GraphicsPath body;
    float rad = (float)S(12);
    AddBodyPath(body, (float)w, (float)hh, rad, 0.5f);
    Color fillTop = g.dark ? Color(ba, 0x2A, 0x2A, 0x2E) : Color(ba, 255, 255, 255);
    Color fillBot = g.dark ? Color(ba, 0x1E, 0x1E, 0x22) : Color(ba, 238, 239, 242);
    LinearGradientBrush wash(PointF(0.f, 0.f), PointF(0.f, (float)hh), fillTop, fillBot);
    gph.FillPath(&wash, &body);
    Pen rim(g.dark ? Color(255, 0x3A, 0x3A, 0x40) : Color(255, 0xD8, 0xDC, 0xE1), 1.0f);
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
    SolidBrush white(g.dark ? Color(255, 0xE8, 0xE8, 0xEA) : Color(255, 32, 32, 34));
    SolidBrush muted(g.dark ? Color(200, 0xA0, 0xA4, 0xAE) : Color(200, 90, 96, 105));

    if (!g.expanded) {
        float r = RingR();
        if (g.dockEdge == 2 && g.ringMode == 1) {
            float Ro = DualOuterR();
            float gap = 12.f;
            float padL = 12.f;
            float cy = (float)hh * 0.5f;
            float x0 = padL + Ro;
            float step = 2.f * Ro + gap;
            DrawConcentricPair(gph, x0, cy,
                g.snap.autoP, 0, true,
                g.snap.api, 1, true,
                true, true);
            if (g.showApi || g.showBot) {
                DrawConcentricPair(gph, x0 + step, cy,
                    g.snap.total, 2, true,
                    g.snap.botP, 3, g.snap.botKnown,
                    g.showApi, g.showBot);
            }
        } else if (g.dockEdge == 2) {
            float gap = (float)kRingGap;
            float padL = (float)kCollapsedPad;
            float cy = (float)S(6) + r;
            float x0 = padL + r;
            float step = 2.f * r + gap;
            DrawCollapsedRingsHV(gph, num, x0, cy, r, step, 0.f, true);
        } else if (g.ringMode == 1) {
            float cx = w * 0.5f;
            float Ro = DualOuterR();
            float topPad = (float)kCollapsedPad;
            float y0 = Ro + topPad;
            float step = DualCenterGap();
            DrawConcentricPair(gph, cx, y0,
                g.snap.autoP, 0, true,
                g.snap.api, 1, true,
                true, true);
            if (g.showApi || g.showBot) {
                DrawConcentricPair(gph, cx, y0 + step,
                    g.snap.total, 2, true,
                    g.snap.botP, 3, g.snap.botKnown,
                    g.showApi, g.showBot);
            }
        } else {
            float cx = w * 0.5f;
            float topPad = (float)kCollapsedPad;
            float y0 = r + topPad;
            float step = RingStepV();
            DrawCollapsedRingsHV(gph, num, cx, y0, r, 0.f, step, false);
        }
    } else {


        int pad = S(20);
        int usageW = S(BASE_PANEL_W);
        float quickX = 0, quickY = 0, quickW = 0, quickH = 0;
        bool showQ = QuickLayout(&quickX, &quickY, &quickW, &quickH);
        int usageLeft = 0;
        int ux = usageLeft + pad;
        int ur = usageLeft + usageW;
        DrawTitleMark(gph, (float)ux, (float)S(14));
        gph.DrawString(L"Cursor 用量", -1, &title, PointF((float)ux + 20.f, (float)S(14)), &white);
        int y = S(46);
        if (!g.snap.ok) {
            DrawRight(gph, L"\u672a\u8bfb\u5230", ui, muted, (float)(ur - pad), (float)S(16));
            gph.DrawString(L"\u672a\u8bfb\u5230\u7528\u91cf", -1, &ui, PointF((float)ux, (float)S(46)), &muted);
        } else {
            if (!g.snap.membership.empty())
                DrawRight(gph, Utf8ToWide(g.snap.membership), ui, muted, (float)(ur - pad), (float)S(16));
            y = S(46);
            y = Meter(gph, ui, sm, ux, y, ur, L"Auto", g.snap.autoP, L"");
            y = Meter(gph, ui, sm, ux, y, ur, L"Models", g.snap.api, L"");
            if (g.showApi)
                y = Meter(gph, ui, sm, ux, y, ur, L"API", g.snap.total, L"");
            if (g.showBot)
                y = Meter(gph, ui, sm, ux, y, ur, L"Bot", g.snap.botP, L"", g.snap.botKnown);
        }

        g_quickHit = false;
        if (showQ)
            DrawQuickLaunch(gph, ui, quickX, quickY, quickW, quickH);

        if (g.snap.ok) {
        int viewY = y + S(8);
        int ty = viewY;
        gph.DrawString(L"今日 Token", -1, &ui, PointF((float)ux, (float)ty), &white);
        DrawRight(gph, FormatTok(g.snap.today()), num, white, (float)(ur - pad), (float)ty);
        ty += S(24);
        std::wstring detail = L"入 " + FormatTok(g.snap.tin) + L"  ·  出 " + FormatTok(g.snap.tout);
        if (g.snap.tcache) detail += L"  ·  缓存 " + FormatTok(g.snap.tcache);
        gph.DrawString(detail.c_str(), -1, &sm, PointF((float)ux, (float)ty), &muted);
        ty += S(16);
        for (auto& row : g.snap.models) {
            std::wstring name = Utf8ToWide(row.name);
            if (name.size() > 20) name = name.substr(0, 19) + L"…";
            gph.DrawString(name.c_str(), -1, &sm, PointF((float)ux, (float)ty), &muted);
            DrawRight(gph, FormatTok(row.tokens), sm, muted, (float)(ur - pad), (float)ty);
            ty += S(16);
        }
        ty += S(4);
        wchar_t foot[64];
        if (g.snap.ok) swprintf(foot, 64, L"%02d:%02d:%02d", g.snap.fetched.wHour, g.snap.fetched.wMinute, g.snap.fetched.wSecond);
        else {
            std::wstring err = Utf8ToWide(g.snap.error);
            wcsncpy(foot, err.c_str(), 63); foot[63] = 0;
        }
        gph.DrawString(foot, -1, &sm, PointF((float)ux, (float)ty), &muted);
        } else {
            std::wstring err = g.snap.error.empty() ? L"未读到用量" : Utf8ToWide(g.snap.error);
            gph.DrawString(err.c_str(), -1, &sm, PointF((float)ux, (float)(y + S(28))), &muted);
        }

    }
    }

    if (bmp && bits) {
        // Premultiply for ULW soft edges (GDI+ writes straight alpha into DIB).
        auto* px = (BYTE*)bits;
        const int n = w * hh;
        for (int i = 0; i < n; ++i) {
            BYTE* p = px + i * 4;
            BYTE a = p[3];
            if (a == 255) continue;
            if (a == 0) { p[0] = p[1] = p[2] = 0; continue; }
            p[0] = (BYTE)((p[0] * a) / 255);
            p[1] = (BYTE)((p[1] * a) / 255);
            p[2] = (BYTE)((p[2] * a) / 255);
        }
        RECT wr{}; GetWindowRect(h, &wr);
        POINT dst{ wr.left, wr.top };
        POINT src{ 0, 0 };
        SIZE sz{ w, hh };
        BLENDFUNCTION bf{ AC_SRC_OVER, 0, 255, AC_SRC_ALPHA };
        UpdateLayeredWindow(h, nullptr, &dst, &sz, mem, &src, 0, &bf, ULW_ALPHA);
        SelectObject(mem, old);
        DeleteObject(bmp);
    } else if (bmp) {
        SelectObject(mem, old);
        DeleteObject(bmp);
    }
    DeleteDC(mem);
    if (!hdc && compat) ReleaseDC(h, compat);
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
        RefreshTheme(h);
        Place(h);
        Refresh();
        SetTimer(h, 1, 45000, nullptr);
        SetTimer(h, 2, 2000, nullptr); // theme poll
        SetTimer(h, 4, 500, nullptr); // stay above other windows
        return 0;
    case WM_WINDOWPOSCHANGING: {
        auto* wp = (WINDOWPOS*)l;
        if (wp && !(wp->flags & SWP_NOZORDER))
            wp->hwndInsertAfter = HWND_TOPMOST;
        return 0;
    }
    case WM_ACTIVATEAPP:
        KeepTopMost(h);
        return 0;
    case WM_DISPLAYCHANGE:
        KeepTopMost(h);
        Place(h);
        return 0;
    case WM_TIMER:
        if (w == 3) {
            if (g_launchAnim < 0 || GetTickCount() - g_launchAnimAt >= kLaunchAnimMs) {
                g_launchAnim = -1;
                KillTimer(h, 3);
            }
            Repaint(h);
            return 0;
        }
        if (w == 4) { KeepTopMost(h); return 0; }
        if (w == 2) { RefreshTheme(h); KeepTopMost(h); return 0; }
        Refresh();
        return 0;
    case WM_SETTINGCHANGE:
        if (l && (wcscmp((LPCWSTR)l, L"ImmersiveColorSet") == 0 || wcscmp((LPCWSTR)l, L"WindowsThemeElement") == 0))
            RefreshTheme(h);
        return 0;
    case WM_USAGE: {
        Snapshot* s = (Snapshot*)l;
        if (s) { g.snap = *s; delete s; }
        g.scrollY = ClampI(g.scrollY, 0, MaxScroll());
        if (g.expanded) Place(h);
        Repaint(h);
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
    case WM_NCHITTEST:
        return HTCLIENT;
    case WM_NCLBUTTONDOWN:
    case WM_LBUTTONDOWN: {
        bool nc = (m == WM_NCLBUTTONDOWN);
        POINT cpt = ClientFromLParam(h, l, nc);
        g.pressLaunch = -1;
        if (g.expanded) {
            int qi = HitQuickFromMsg(h, l, nc);
            if (qi >= 0) {
                g.pressLaunch = qi;
                g.dragging = false;
                g.scrolling = false;
                SetCapture(h);
                LaunchShortcut(qi);
                return 0;
            }
        }
        if (g.expanded && MaxScroll() > 0 && cpt.y >= TokenViewY()) {
            g.scrolling = true;
            g.press = cpt;
            SetCapture(h);
            return 0;
        }
        POINT sp{};
        GetCursorPos(&sp);
        g.press = sp;
        g.pressY = g.y;
        g.dragging = false;
        g.scrolling = false;
        SetCapture(h);
        return 0;
    }
    case WM_MOUSEMOVE:
        if (g.expanded && !g.tracking) TrackLeave(h);
        if (g.pressLaunch >= 0) return 0;
        if (GetCapture() == h && g.scrolling) {
            int dy = GET_Y_LPARAM(l) - g.press.y;
            g.press.y = GET_Y_LPARAM(l);
            g.scrollY = ClampI(g.scrollY + dy * MaxScroll() / (std::max)(1, TokenViewH() - 16), 0, MaxScroll());
            Repaint(h);
            return 0;
        }
        if (GetCapture() == h && !g.scrolling) {
            POINT pt;
            GetCursorPos(&pt);
            int ax = abs(pt.x - g.press.x);
            int ay = abs(pt.y - g.press.y);
            int along = (g.dockEdge == 2) ? ax : ay;
            if (along > 10 && along >= ax && along >= ay) {
                g.dragging = true;
                if (g.dockEdge == 2)
                    g.y = g.pressY + (pt.x - g.press.x);
                else
                    g.y = g.pressY + (pt.y - g.press.y);
                DragMove(h);
            }
        }
        return 0;
    case WM_MOUSEWHEEL:
        if (g.expanded && MaxScroll() > 0) {
            int delta = GET_WHEEL_DELTA_WPARAM(w);
            g.scrollY = ClampI(g.scrollY - delta / WHEEL_DELTA * S(28), 0, MaxScroll());
            Repaint(h);
            return 0;
        }
        break;
    case WM_NCLBUTTONUP:
    case WM_LBUTTONUP: {
        int launch = g.pressLaunch;
        g.pressLaunch = -1;
        ReleaseCapture();
        if (launch >= 0) return 0;
        if (g.scrolling) {
            g.scrolling = false;
            return 0;
        }
        if (g.dragging) {
            DragMove(h);
            ApplyRegion(h);
            SaveConfig();
        } else if (g.expanded) {
            int qi = HitQuickFromMsg(h, l, m == WM_NCLBUTTONUP);
            if (qi == -1) OpenManageShortcuts();
            else if (qi >= 0) LaunchShortcut(qi);
        } else {
            g.expanded = true;
            g.scrollY = 0;
            g.holdUntil = GetTickCount() + 800;
            Place(h);
            g.tracking = false;
            TrackLeave(h);
        }
        return 0;
    }
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
        AppendMenuW(menu, MF_STRING, 10, L"\u7acb\u5373\u5237\u65b0");
        AppendMenuW(menu, MF_STRING, 11, L"\u6253\u5f00\u7528\u91cf\u9875");
        AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(menu, MF_STRING, IDM_SETTINGS, L"\u8bbe\u7f6e");
        AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(menu, MF_STRING, 14, L"\u9000\u51fa");
        int cmd = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON, pt.x, pt.y, 0, h, nullptr);
        DestroyMenu(menu);
        if (cmd == 10) Refresh();
        if (cmd == 11) ShellExecuteW(nullptr, L"open", L"https://cursor.com/dashboard", nullptr, nullptr, SW_SHOWNORMAL);
        if (cmd == IDM_SETTINGS) OpenSettings();
        if (cmd == 14) DestroyWindow(h);
        return 0;
    }
    case WM_DESTROY:
        SaveConfig();
        DestroyHotspots();
        FreeShortcutIcons();
        KillTimer(h, 1);
        KillTimer(h, 2);
        KillTimer(h, 3);
        KillTimer(h, 4);
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProc(h, m, w, l);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE, LPWSTR, int) {
    // Must run before any USER32/GDI call, or mouse coords drift when the window is not at (0,0).
    using SetDpiCtxFn = BOOL (WINAPI*)(HANDLE);
    auto setDpi = (SetDpiCtxFn)GetProcAddress(GetModuleHandleW(L"user32.dll"), "SetProcessDpiAwarenessContext");
    if (!setDpi || !setDpi((HANDLE)-4))
        SetProcessDPIAware();

    HANDLE mu = CreateMutexW(nullptr, TRUE, L"CursorUsageWidgetCpp");
    if (GetLastError() == ERROR_ALREADY_EXISTS) return 0;
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    INITCOMMONCONTROLSEX icc{ sizeof(icc), ICC_LISTVIEW_CLASSES };
    InitCommonControlsEx(&icc);
    LoadConfig();
    LoadShortcuts();
    g.dark = ReadAppsDark();
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
        if (g_manage.hwnd && IsDialogMessageW(g_manage.hwnd, &msg)) continue;
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    Gdiplus::GdiplusShutdown(g.gdip);
    FreeShortcutIcons();
    CoUninitialize();
    CloseHandle(mu);
    return 0;
}

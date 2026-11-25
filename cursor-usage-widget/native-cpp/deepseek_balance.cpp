#include "deepseek_balance.h"
#include <map>
#include <set>
#include <stdexcept>
#include <utility>
#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <winhttp.h>
#pragma comment(lib, "winhttp.lib")
#endif

namespace deepseek {
namespace {
const std::size_t kMaxResponse = 1024 * 1024;
// Small strict JSON reader, private to this module: no dependency on UI parser.
struct Json {
    enum Kind { Null, Bool, Number, String, Array, Object } kind = Null;
    bool boolean = false;
    std::string text;
    std::vector<Json> array;
    std::map<std::string, Json> object;
};
class Reader {
    const std::string& s;
    std::size_t p = 0;
    void fail() const { throw std::runtime_error("invalid JSON"); }
    void ws() { while (p < s.size() && (s[p]==' ' || s[p]=='\t' || s[p]=='\r' || s[p]=='\n')) ++p; }
    bool eat(char c) { if (p < s.size() && s[p] == c) { ++p; return true; } return false; }
    bool digit() const { return p < s.size() && s[p] >= '0' && s[p] <= '9'; }
    void literal(const char* str) { for (; *str; ++str) if (!eat(*str)) fail(); }
    unsigned hex4() {
        unsigned n = 0;
        for (int i=0; i<4; ++i) {
            if (p >= s.size()) fail();
            unsigned char c = static_cast<unsigned char>(s[p++]);
            unsigned v = c >= '0' && c <= '9' ? c-'0' : c >= 'a' && c <= 'f' ? c-'a'+10 : c >= 'A' && c <= 'F' ? c-'A'+10 : 99;
            if (v > 15) fail();
            n = n*16+v;
        }
        return n;
    }
    static void utf8(std::string& out, unsigned n) {
        if (n < 0x80) out += static_cast<char>(n);
        else if (n < 0x800) { out += static_cast<char>(0xc0|(n>>6)); out += static_cast<char>(0x80|(n&63)); }
        else if (n < 0x10000) { out += static_cast<char>(0xe0|(n>>12)); out += static_cast<char>(0x80|((n>>6)&63)); out += static_cast<char>(0x80|(n&63)); }
        else { out += static_cast<char>(0xf0|(n>>18)); out += static_cast<char>(0x80|((n>>12)&63)); out += static_cast<char>(0x80|((n>>6)&63)); out += static_cast<char>(0x80|(n&63)); }
    }
    std::string string() {
        if (!eat('"')) fail();
        std::string out;
        while (p < s.size()) {
            unsigned char c = static_cast<unsigned char>(s[p++]);
            if (c == '"') return out;
            if (c < 0x20) fail();
            if (c != '\\') { out += static_cast<char>(c); continue; }
            if (p >= s.size()) fail();
            switch (s[p++]) {
            case '"': out+='"'; break; case '\\': out+='\\'; break; case '/': out+='/'; break;
            case 'b': out+='\b'; break; case 'f': out+='\f'; break; case 'n': out+='\n'; break;
            case 'r': out+='\r'; break; case 't': out+='\t'; break;
            case 'u': {
                unsigned n=hex4();
                if (n>=0xd800 && n<=0xdbff) {
                    if (!eat('\\') || !eat('u')) fail();
                    unsigned low=hex4(); if (low<0xdc00 || low>0xdfff) fail();
                    n=0x10000+((n-0xd800)<<10)+(low-0xdc00);
                } else if (n>=0xdc00 && n<=0xdfff) fail();
                utf8(out,n); break;
            }
            default: fail();
            }
        }
        fail(); return out;
    }
    Json value(unsigned depth) {
        if (depth > 32) fail();
        ws(); if (p >= s.size()) fail();
        Json v;
        switch (s[p]) {
        case 'n': literal("null"); return v;
        case 't': literal("true"); v.kind=Json::Bool; v.boolean=true; return v;
        case 'f': literal("false"); v.kind=Json::Bool; return v;
        case '"': v.kind=Json::String; v.text=string(); return v;
        case '[':
            ++p; v.kind=Json::Array; ws(); if (eat(']')) return v;
            do { v.array.push_back(value(depth+1)); ws(); if (eat(']')) return v; } while (eat(','));
            fail(); break;
        case '{':
            ++p; v.kind=Json::Object; ws(); if (eat('}')) return v;
            do {
                ws(); auto key=string(); ws(); if (!eat(':')) fail();
                auto child=value(depth+1);
                if (!v.object.emplace(std::move(key),std::move(child)).second) fail();
                ws(); if (eat('}')) return v;
            } while (eat(','));
            fail(); break;
        default: {
            v.kind=Json::Number; std::size_t start=p;
            eat('-'); if (!eat('0')) { if (!digit()) fail(); while (digit()) ++p; }
            if (eat('.')) { if (!digit()) fail(); while (digit()) ++p; }
            if (eat('e') || eat('E')) { if (!eat('+')) eat('-'); if (!digit()) fail(); while (digit()) ++p; }
            v.text=s.substr(start,p-start); return v;
        }
        }
        fail(); return v;
    }
public:
    explicit Reader(const std::string& input) : s(input) {}
    Json parse() { auto v=value(0); ws(); if (p != s.size()) fail(); return v; }
};
const Json* field(const Json& v, const char* key, Json::Kind kind) {
    auto i=v.object.find(key);
    return v.kind == Json::Object && i!=v.object.end() && i->second.kind==kind ? &i->second : nullptr;
}
bool decimal(const std::string& s) {
    if (s.empty() || s.size()>128) return false;
    std::size_t i=s[0]=='-' ? 1 : 0, start=i;
    while (i<s.size() && s[i]>='0' && s[i]<='9') ++i;
    if (i==start) return false;
    if (i<s.size() && s[i]=='.') {
        start=++i; while (i<s.size() && s[i]>='0' && s[i]<='9') ++i;
        if (i==start) return false;
    }
    return i==s.size();
}
bool validKey(const std::string& key) {
    if (key.empty() || key.size()>4096) return false;
    for (unsigned char c : key) if (c<=32 || c>=127) return false;
    return true;
}
#ifdef _WIN32
struct Internet {
    HINTERNET h;
    explicit Internet(HINTERNET handle) : h(handle) {}
    ~Internet() { if (h) WinHttpCloseHandle(h); }
    Internet(const Internet&) = delete;
    Internet& operator=(const Internet&) = delete;
};
#endif
} // namespace

bool ParseBalance(const std::string& json, bool& available,
                  std::vector<CurrencyBalance>& balances) {
    if (json.empty() || json.size()>kMaxResponse) return false;
    try {
        auto root=Reader(json).parse();
        auto a=field(root,"is_available",Json::Bool);
        auto infos=field(root,"balance_infos",Json::Array);
        if (!a || !infos || infos->array.empty() || infos->array.size()>32) return false;
        std::vector<CurrencyBalance> parsed;
        std::set<std::string> currencies;
        for (const auto& item : infos->array) {
            auto c=field(item,"currency",Json::String);
            auto t=field(item,"total_balance",Json::String);
            auto g=field(item,"granted_balance",Json::String);
            auto u=field(item,"topped_up_balance",Json::String);
            if (!c || !t || !g || !u || c->text.size()!=3) return false;
            for (char ch : c->text) if (ch<'A' || ch>'Z') return false;
            if (!currencies.insert(c->text).second || !decimal(t->text) || !decimal(g->text) || !decimal(u->text)) return false;
            parsed.push_back({c->text,t->text,g->text,u->text});
        }
        balances=std::move(parsed); available=a->boolean; return true;
    } catch (const std::exception&) { return false; }
}

BalanceHttpResponse RequestBalance(const std::string& api_key) {
    BalanceHttpResponse out;
    if (!validKey(api_key)) return out;
#ifdef _WIN32
    Internet session(WinHttpOpen(L"CursorUsage/DeepSeekBalance", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
                                WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0));
    if (!session.h || !WinHttpSetTimeouts(session.h,8000,8000,15000,15000)) return out;
    Internet conn(WinHttpConnect(session.h,L"api.deepseek.com",INTERNET_DEFAULT_HTTPS_PORT,0));
    if (!conn.h) return out;
    Internet req(WinHttpOpenRequest(conn.h,L"GET",L"/user/balance",nullptr,WINHTTP_NO_REFERER,
                                   WINHTTP_DEFAULT_ACCEPT_TYPES,WINHTTP_FLAG_SECURE));
    if (!req.h) return out;
    // Do not forward bearer tokens to redirect targets. Use normal TLS validation.
    DWORD policy=WINHTTP_OPTION_REDIRECT_POLICY_NEVER;
    if (!WinHttpSetOption(req.h,WINHTTP_OPTION_REDIRECT_POLICY,&policy,sizeof(policy))) return out;
    std::wstring headers=L"Accept: application/json\r\nAuthorization: Bearer ";
    headers.append(api_key.begin(),api_key.end()); headers+=L"\r\n";
    BOOL sent=WinHttpSendRequest(req.h,headers.c_str(),static_cast<DWORD>(headers.size()),
                                WINHTTP_NO_REQUEST_DATA,0,0,0);
    SecureZeroMemory(&headers[0],headers.size()*sizeof(wchar_t));
    if (!sent || !WinHttpReceiveResponse(req.h,nullptr)) return out;
    DWORD status=0, size=sizeof(status);
    if (!WinHttpQueryHeaders(req.h,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,
                            WINHTTP_HEADER_NAME_BY_INDEX,&status,&size,WINHTTP_NO_HEADER_INDEX)) return out;
    out.http_status=status;
    // Error bodies may contain diagnostics: don't retain or expose them.
    if (status!=200) { out.transport_ok=true; return out; }
    char buffer[8192];
    for (;;) {
        DWORD bytes=0;
        if (!WinHttpReadData(req.h,buffer,sizeof(buffer),&bytes)) { out.body.clear(); return out; }
        if (!bytes) break;
        if (out.body.size()+bytes>kMaxResponse) { out.body.clear(); return out; }
        out.body.append(buffer,bytes);
    }
    out.transport_ok=true;
#endif
    return out;
}

BalanceSnapshot RefreshBalance(const std::string& api_key, const BalanceSnapshot& previous,
                               std::time_t now_utc, const BalanceTransport& transport) {
    if (api_key.empty()) return BalanceSnapshot();
    BalanceSnapshot next=previous;
    next.status=BalanceStatus::UpdateFailed; next.http_status=0;
    next.error=BalanceError::InvalidKey;
    if (!validKey(api_key)) return next;
    next.error=BalanceError::Network;
    BalanceHttpResponse response;
    try { response=transport ? transport(api_key) : RequestBalance(api_key); }
    catch (const std::exception&) { return next; }
    next.http_status=response.http_status;
    if (!response.transport_ok) return next;
    next.error=BalanceError::Http;
    if (response.http_status!=200) return next;
    std::vector<CurrencyBalance> parsed; bool available=false;
    next.error=BalanceError::InvalidResponse;
    if (!ParseBalance(response.body,available,parsed)) return next;
    next.balances=std::move(parsed); next.is_available=available;
    next.has_balance=true; next.last_success_utc=now_utc;
    next.status=BalanceStatus::Ready; next.error=BalanceError::None;
    return next;
}
} // namespace deepseek

// Standalone: cl /EHsc /std:c++14 deepseek_balance_test.cpp deepseek_balance.cpp winhttp.lib
#include "deepseek_balance.h"
#include <iostream>
#include <stdexcept>
using namespace deepseek;
static int checks=0;
static void check(bool ok, const char* label) {
    ++checks; if (!ok) { std::cerr << "FAIL: " << label << '\n'; throw std::runtime_error("test failed"); }
}
static std::string row(const std::string& currency, const std::string& amount) {
    return "{\"currency\":\""+currency+"\",\"total_balance\":\""+amount+"\",\"granted_balance\":\"0.00\",\"topped_up_balance\":\""+amount+"\"}";
}
static std::string body(const std::string& rows, bool available=true) {
    return std::string("{\"is_available\":")+(available?"true":"false")+",\"balance_infos\":["+rows+"]}";
}
int main() {
    try {
        auto good=body(row("CNY","110.00")+","+row("USD","0.12345678901234567890"));
        bool available=false; std::vector<CurrencyBalance> parsed;
        check(ParseBalance(good,available,parsed),"multi currency parses");
        check(available && parsed.size()==2 && parsed[0].currency=="CNY" && parsed[1].currency=="USD","currency not merged");
        check(parsed[1].total_balance=="0.12345678901234567890","decimal precision preserved");
        auto transport=[&](const std::string&) { return BalanceHttpResponse{true,200,good}; };
        auto ready=RefreshBalance("test-key",BalanceSnapshot(),100,transport);
        check(ready.status==BalanceStatus::Ready && ready.has_balance && ready.last_success_utc==100,"success timestamp");
        auto fail=[&](unsigned status,const std::string& text,bool network=true) {
            auto t=[&](const std::string&) { return BalanceHttpResponse{network,status,text}; };
            auto r=RefreshBalance("test-key",ready,200,t);
            check(r.status==BalanceStatus::UpdateFailed && r.has_balance && r.last_success_utc==100 && r.balances[0].total_balance=="110.00","failure preserves data and time");
            return r;
        };
        for (unsigned code : {401u,403u,429u,500u,302u,204u})
            check(fail(code,"sensitive diagnostics").error==BalanceError::Http,"http error classification");
        check(fail(0,"",false).error==BalanceError::Network,"network failure");
        for (const auto& invalid : {std::string(""),std::string("null"),std::string("<html>ok</html>"),std::string("{}"),body(""),good+"junk",std::string("{\"is_available\":true,\"is_available\":false,\"balance_infos\":[]}"),body(row("CNY","NaN")),body(row("CNY","")),body(row("CNY","1e2")),body(row("CNY","0"))+",",body(row("CNY","1")+","+row("CNY","2"))})
            check(fail(200,invalid).error==BalanceError::InvalidResponse,"schema failure");
        auto invalidType=body(row("CNY","1"));
        auto at=invalidType.find("\"total_balance\":\"1\"");
        invalidType.replace(at,std::string("\"total_balance\":\"1\"").size(),"\"total_balance\":1");
        check(fail(200,invalidType).error==BalanceError::InvalidResponse,"numeric money rejected");
        auto initial=RefreshBalance("test-key",BalanceSnapshot(),300,[](const std::string&) { return BalanceHttpResponse{false,0,""}; });
        check(!initial.has_balance && initial.balances.empty() && initial.last_success_utc==0,"first failure not zero balance");
        auto zero=RefreshBalance("test-key",ready,400,[](const std::string&) { return BalanceHttpResponse{true,200,body(row("CNY","0.00"),false)}; });
        check(zero.status==BalanceStatus::Ready && zero.has_balance && !zero.is_available && zero.balances[0].total_balance=="0.00","real zero with unavailable is successful data");
        check(zero.last_success_utc==400,"zero balance updates timestamp");
        check(ParseBalance(body(row("USD","-0.01"),false),available,parsed) && parsed[0].total_balance=="-0.01","negative balance preserved");
        bool called=false;
        auto noCall=[&](const std::string&) { called=true; return BalanceHttpResponse(); };
        auto empty=RefreshBalance("",ready,500,noCall);
        check(!called && empty.status==BalanceStatus::NotConfigured && !empty.has_balance && empty.balances.empty() && empty.last_success_utc==0,"unconfigure clears previous account");
        for (const std::string key : {std::string("x\r\nInjected: true"),std::string(" "),std::string(4097,'x')}) {
            auto r=RefreshBalance(key,ready,500,noCall);
            check(!called && r.error==BalanceError::InvalidKey && r.last_success_utc==100,"invalid key no transport");
        }
        auto thrown=RefreshBalance("test-key",ready,600,[](const std::string&)->BalanceHttpResponse { throw std::runtime_error("must not leak"); });
        check(thrown.error==BalanceError::Network && thrown.last_success_utc==100,"transport exception safe");
        auto before=parsed;
        check(!ParseBalance("{}",available,parsed) && parsed[0].total_balance==before[0].total_balance,"parse failure atomic");
        std::cout << "PASS " << checks << " checks (mock only; no credentials or live calls)\n";
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}

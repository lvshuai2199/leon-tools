#pragma once
#include <ctime>
#include <functional>
#include <string>
#include <vector>

// Synchronous, UI-independent collector. Run on a worker thread. No key storage,
// logging, scheduling or UI code. Link winhttp.lib on Windows.
namespace deepseek {
enum class BalanceStatus { NotConfigured, Ready, UpdateFailed };
enum class BalanceError { None, InvalidKey, Network, Http, InvalidResponse };
struct CurrencyBalance {
    std::string currency;
    // Preserve decimal strings exactly; never convert currencies or round via double.
    std::string total_balance;
    std::string granted_balance;
    std::string topped_up_balance;
};
struct BalanceSnapshot {
    BalanceStatus status = BalanceStatus::NotConfigured;
    BalanceError error = BalanceError::None;
    unsigned http_status = 0;
    bool has_balance = false;
    bool is_available = false; // API-call eligibility, NOT request success.
    std::time_t last_success_utc = 0; // Epoch; UI formats in Beijing time.
    std::vector<CurrencyBalance> balances;
};
struct BalanceHttpResponse {
    bool transport_ok = false;
    unsigned http_status = 0;
    std::string body;
};
// Injection point for deterministic tests. Never include credentials in results.
using BalanceTransport = std::function<BalanceHttpResponse(const std::string& api_key)>;
BalanceHttpResponse RequestBalance(const std::string& api_key);
// Atomic parse: output is untouched on failure; accepts only the documented schema.
bool ParseBalance(const std::string& json, bool& is_available,
                  std::vector<CurrencyBalance>& balances);
// Failure keeps previous data and success time. Empty key clears old account data.
// Caller must pass an empty previous snapshot when changing accounts/keys.
BalanceSnapshot RefreshBalance(const std::string& api_key,
                               const BalanceSnapshot& previous,
                               std::time_t now_utc,
                               const BalanceTransport& transport = BalanceTransport());
} // namespace deepseek

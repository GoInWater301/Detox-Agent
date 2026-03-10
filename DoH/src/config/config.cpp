#include "config/config.hpp"

#include <cstdlib>
#include <fstream>
#include <spdlog/spdlog.h>
#include <string>
#include <thread>

namespace doh {

// ─────────────────────────────────────────────────────────────────────────────
// .env file loader
//
// Reads KEY=VALUE pairs from a file and calls setenv() for any key that is
// not already present in the process environment.
// Environment variables always take priority over .env entries.
//
// Supported syntax:
//   KEY=value
//   KEY="value with spaces"
//   KEY='value with spaces'
//   export KEY=value        (optional 'export' prefix is stripped)
//   # this is a comment
//   (blank lines are ignored)
// ─────────────────────────────────────────────────────────────────────────────

namespace {

std::string_view trim(std::string_view s) {
    while (!s.empty() && std::isspace(static_cast<unsigned char>(s.front())))
        s.remove_prefix(1);
    while (!s.empty() && std::isspace(static_cast<unsigned char>(s.back())))
        s.remove_suffix(1);
    return s;
}

std::string_view strip_quotes(std::string_view s) {
    if (s.size() >= 2 &&
        ((s.front() == '"' && s.back() == '"') ||
         (s.front() == '\'' && s.back() == '\''))) {
        return s.substr(1, s.size() - 2);
    }
    return s;
}

void load_dotenv(const std::string& path) {
    std::ifstream f(path);
    if (!f.is_open()) return;  // .env is optional

    spdlog::info("Loading .env file: {}", path);
    std::string line;
    while (std::getline(f, line)) {
        std::string_view sv = line;

        // Strip inline comment
        if (const auto hash = sv.find('#'); hash != std::string_view::npos)
            sv = sv.substr(0, hash);

        sv = trim(sv);
        if (sv.empty()) continue;

        // Strip optional "export " prefix
        if (sv.starts_with("export ") || sv.starts_with("export\t"))
            sv = trim(sv.substr(7));

        const auto eq = sv.find('=');
        if (eq == std::string_view::npos) continue;

        const auto key = trim(sv.substr(0, eq));
        const auto val = strip_quotes(trim(sv.substr(eq + 1)));

        if (key.empty()) continue;

        // setenv with overwrite=0: environment variable takes priority
        const std::string key_s(key);
        if (std::getenv(key_s.c_str()) == nullptr) {
            ::setenv(key_s.c_str(), std::string(val).c_str(), /*overwrite=*/0);
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Typed env-var readers (all fall back to defaults if the variable is unset)
// ─────────────────────────────────────────────────────────────────────────────

std::string env_str(const char* key, std::string_view def) {
    const char* v = std::getenv(key);
    return v ? v : std::string(def);
}

uint16_t env_u16(const char* key, uint16_t def) {
    const char* v = std::getenv(key);
    return v ? static_cast<uint16_t>(std::stoul(v)) : def;
}

uint32_t env_u32(const char* key, uint32_t def) {
    const char* v = std::getenv(key);
    return v ? static_cast<uint32_t>(std::stoul(v)) : def;
}

std::size_t env_sz(const char* key, std::size_t def) {
    const char* v = std::getenv(key);
    return v ? static_cast<std::size_t>(std::stoull(v)) : def;
}

BlockResponsePolicy env_block_response_policy(const char* key,
                                              BlockResponsePolicy def) {
    const std::string value = env_str(key, "NXDOMAIN");
    if (value == "REFUSED" || value == "refused") {
        return BlockResponsePolicy::refused;
    }
    if (value != "NXDOMAIN" && value != "nxdomain") {
        spdlog::warn("Invalid {}='{}'; falling back to NXDOMAIN", key, value);
    }
    return def;
}

} // namespace

// ─────────────────────────────────────────────────────────────────────────────

Config Config::from_env() {
    // 1. Load .env file first (env vars override .env entries)
    const std::string env_file = env_str("DOH_ENV_FILE", ".env");
    load_dotenv(env_file);

    // 2. Read all settings from the environment
    Config c;
    c.listen_address       = env_str("DOH_LISTEN_ADDR",    "0.0.0.0");
    c.listen_port          = env_u16("DOH_LISTEN_PORT",    443);
    c.cert_chain_file      = env_str("DOH_CERT_CHAIN",     "certs/fullchain.pem");
    c.private_key_file     = env_str("DOH_PRIVATE_KEY",    "certs/privkey.pem");
    c.dns_upstream_host    = env_str("DOH_DNS_UPSTREAM",   "8.8.8.8");
    c.dns_upstream_port    = env_u16("DOH_DNS_PORT",       53);
    c.dns_timeout_ms       = env_u32("DOH_DNS_TIMEOUT_MS", 3000);
    c.dns_min_ttl_s        = env_u32("DOH_DNS_MIN_TTL",    30);
    c.dns_max_ttl_s        = env_u32("DOH_DNS_MAX_TTL",    60);
    c.filter_enabled       = env_str("DOH_FILTER_ENABLED", "false") == "true";
    c.redis_host           = env_str("DOH_REDIS_HOST",     "127.0.0.1");
    c.redis_port           = env_u16("DOH_REDIS_PORT",     6379);
    c.redis_password       = env_str("DOH_REDIS_PASSWORD", "");
    c.block_response_policy = env_block_response_policy(
        "DOH_BLOCK_RESPONSE", BlockResponsePolicy::nxdomain);
    c.analytics_endpoint   = env_str("DOH_ANALYTICS_EP",   "localhost:50051");
    c.analytics_queue_cap  = env_sz ("DOH_ANALYTICS_CAP",  4096);

    const auto hw  = std::thread::hardware_concurrency();
    c.thread_count = (hw > 0) ? hw : 4;

    return c;
}

} // namespace doh

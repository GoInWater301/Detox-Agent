#include "filter/redis_domain_filter.hpp"

#include <spdlog/spdlog.h>

namespace doh::filter {

namespace net   = boost::asio;
namespace redis = boost::redis;

// ─────────────────────────────────────────────────────────────────────────────
// Lua script — executed atomically on the Redis server side.
//
// ARGV[1] = user_id       (e.g. "alice")
// ARGV[2] = queried domain (e.g. "ads.tracker.example.com")
//
// Algorithm:
//   For each suffix of the domain (most-specific first):
//     ads.tracker.example.com
//     tracker.example.com
//     example.com
//     com
//   Check both doh:block:global and doh:block:{user_id}.
//   Return 1 on first hit (blocked), 0 if none match (allowed).
//
// One EVAL = one round-trip, regardless of domain depth.
// ─────────────────────────────────────────────────────────────────────────────
static constexpr std::string_view kCheckScript = R"lua(
local global_key = "doh:block:global"
local user_key   = "doh:block:" .. ARGV[1]
local d          = ARGV[2]
while #d > 0 do
    if redis.call("SISMEMBER", global_key, d) == 1 then return 1 end
    if redis.call("SISMEMBER", user_key,   d) == 1 then return 1 end
    local dot = string.find(d, ".", 1, true)
    if not dot then break end
    d = string.sub(d, dot + 1)
end
return 0
)lua";

// ─────────────────────────────────────────────────────────────────────────────

RedisDomainFilter::RedisDomainFilter(net::io_context& ioc,
                                      std::string      host,
                                      uint16_t         port,
                                      std::string      password)
    : conn_(ioc)
{
    redis::config cfg;
    cfg.addr.host = host;
    cfg.addr.port = std::to_string(port);
    if (!password.empty()) cfg.password = std::move(password);

    // async_run keeps the connection alive and handles reconnection.
    // net::detached: we don't need the completion token for the run loop.
    conn_.async_run(cfg, {}, net::detached);

    spdlog::info("Redis domain filter → {}:{}", host, port);
}

void RedisDomainFilter::async_check(std::string                  user_id,
                                     std::string                  domain,
                                     net::any_io_executor         ex,
                                     FilterCallback               cb)
{
    if (domain.empty()) { cb(false); return; }

    // Shared ownership keeps req/resp alive for the duration of async_exec.
    auto req  = std::make_shared<redis::request>();
    auto resp = std::make_shared<redis::response<long long>>();

    req->push("EVAL", kCheckScript, "0", user_id, domain);

    conn_.async_exec(*req, *resp,
        net::bind_executor(ex,
            [req, resp,
             user_id = std::move(user_id),
             domain  = std::move(domain),
             cb      = std::move(cb)]
            (boost::system::error_code ec, std::size_t) mutable
            {
                if (ec) {
                    // Fail-open: Redis unavailable → allow the query through.
                    spdlog::warn("Filter Redis error [{}/{}]: {}",
                                 user_id, domain, ec.message());
                    cb(false);
                    return;
                }

                const bool blocked = (std::get<0>(*resp).value() == 1LL);
                if (blocked) {
                    spdlog::info("Blocked [{}/{}]", user_id, domain);
                }
                cb(blocked);
            }));
}

} // namespace doh::filter

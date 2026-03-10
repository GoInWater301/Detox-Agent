#pragma once

#include "filter/domain_filter.hpp"

#include <boost/asio.hpp>
#include <boost/redis.hpp>
#include <shared_mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>

namespace doh::filter {

// Redis-backed cached per-user domain filter.
//
// Redis data model
// ─────────────────
//   SET  doh:block:global      — global blocklist (applies to all users)
//   SET  doh:block:{user_id}   — per-user blocklist
//
// Blocking semantics
// ──────────────────
//   Adding "example.com" to a set blocks:
//     • example.com   (exact match)
//     • *.example.com (all subdomains, e.g. ads.example.com, api.example.com)
//
//   Redis is used only as the source of truth. The request path consults a
//   local in-memory snapshot that is refreshed in the background.
//
// Failure mode
// ────────────
//   If Redis is unreachable the filter can fail-open (allow) or fail-closed
//   (block), controlled by configuration.
class RedisDomainFilter : public DomainFilter {
public:
    struct Snapshot {
        std::unordered_set<std::string> global;
        std::unordered_map<std::string, std::unordered_set<std::string>> per_user;
    };

    RedisDomainFilter(boost::asio::io_context& ioc,
                      std::string              host,
                      uint16_t                 port,
                      std::string              password,
                      uint32_t                 timeout_ms,
                      uint32_t                 refresh_ms,
                      bool                     fail_open);

    void async_check(std::string                  user_id,
                      std::string                  domain,
                      boost::asio::any_io_executor ex,
                      FilterCallback               cb) override;

private:
    void schedule_refresh(std::chrono::milliseconds delay);
    void refresh_snapshot();

    boost::asio::steady_timer refresh_timer_;
    boost::redis::config redis_cfg_;
    std::chrono::milliseconds timeout_;
    std::chrono::milliseconds refresh_interval_;
    bool fail_open_ = true;
    bool ready_ = false;
    mutable std::shared_mutex snapshot_mu_;
    Snapshot snapshot_;
};

} // namespace doh::filter

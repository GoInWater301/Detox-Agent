#pragma once

#include "filter/domain_filter.hpp"

#include <boost/asio.hpp>
#include <boost/redis.hpp>
#include <string>

namespace doh::filter {

// Redis-backed per-user domain filter.
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
//   A single EVAL call runs a Lua script on Redis that iterates from the most
//   specific label to the TLD, checking both sets at each level.
//   This means one network round-trip per DoH request.
//
// Failure mode
// ────────────
//   If Redis is unreachable the filter fails-open (allows the query through)
//   and logs a warning.  The DoH service remains fully operational.
class RedisDomainFilter : public DomainFilter {
public:
    RedisDomainFilter(boost::asio::io_context& ioc,
                      std::string              host,
                      uint16_t                 port,
                      std::string              password);

    void async_check(std::string                  user_id,
                      std::string                  domain,
                      boost::asio::any_io_executor ex,
                      FilterCallback               cb) override;

private:
    boost::redis::connection conn_;
};

} // namespace doh::filter

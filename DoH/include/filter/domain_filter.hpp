#pragma once

#include <boost/asio/any_io_executor.hpp>
#include <functional>
#include <string>

namespace doh::filter {

// Called exactly once: true = domain is blocked, false = allow.
using FilterCallback = std::function<void(bool blocked)>;

// Abstract, async domain filter interface.
//
// Implementations MUST:
//   - Never block the calling thread.
//   - Invoke cb exactly once, on the provided executor.
//   - Default to allow (cb(false)) on internal error (fail-open).
struct DomainFilter {
    virtual ~DomainFilter() = default;

    // Checks whether `domain` is blocked for `user_id`.
    // Both the per-user blocklist and the global blocklist are consulted.
    // Suffix matching applies: blocking "example.com" also blocks "ads.example.com".
    virtual void async_check(std::string                  user_id,
                              std::string                  domain,
                              boost::asio::any_io_executor ex,
                              FilterCallback               cb) = 0;
};

} // namespace doh::filter

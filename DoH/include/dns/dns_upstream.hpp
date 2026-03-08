#pragma once

#include <cstdint>
#include <functional>
#include <vector>
#include <boost/system/error_code.hpp>

namespace doh::dns {

// Callback type for all async DNS upstream operations.
// Called exactly once, on the executor that initiated the query.
using QueryCallback =
    std::function<void(boost::system::error_code, std::vector<uint8_t>)>;

// Abstract DNS upstream transport.
// Implementations must be stateless with respect to individual queries:
// each async_query() call is completely independent.
struct DnsUpstream {
    virtual ~DnsUpstream() = default;
    virtual void async_query(std::vector<uint8_t> query, QueryCallback cb) = 0;
};

} // namespace doh::dns

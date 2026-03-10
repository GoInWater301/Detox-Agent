#pragma once

#include "dns/dns_upstream.hpp"

#include <boost/asio/any_io_executor.hpp>
#include <cstdint>
#include <functional>
#include <string>

namespace doh::dns {

using ForwardCallback =
    std::function<void(boost::system::error_code, std::vector<uint8_t>, bool used_tcp)>;

// Hybrid DNS forwarder: tries UDP first, falls back to TCP when the UDP reply
// is truncated or the UDP path times out.
// Stateless — creates a fresh socket for each query. Safe to call from
// multiple threads simultaneously.
class DnsForwarder {
public:
    DnsForwarder(std::string upstream_host,
                 uint16_t    upstream_port,
                 uint32_t    timeout_ms);

    // Initiates an async DNS lookup on the given executor.
    // The callback is invoked exactly once, on `ex`.
    void async_forward(boost::asio::any_io_executor ex,
                       std::vector<uint8_t>         query,
                       ForwardCallback              cb);

private:
    std::string upstream_host_;
    uint16_t    upstream_port_;
    uint32_t    timeout_ms_;
};

} // namespace doh::dns

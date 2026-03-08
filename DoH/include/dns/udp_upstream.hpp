#pragma once

#include "dns/dns_upstream.hpp"

#include <boost/asio/any_io_executor.hpp>
#include <cstdint>
#include <string>

namespace doh::dns {

// Sends one DNS query via UDP (one-shot socket per query).
// A steady_timer enforces the timeout; on expiry the callback receives
// boost::asio::error::timed_out.
class UdpUpstream : public DnsUpstream {
public:
    UdpUpstream(boost::asio::any_io_executor ex,
                std::string                  host,
                uint16_t                     port,
                uint32_t                     timeout_ms);

    void async_query(std::vector<uint8_t> query, QueryCallback cb) override;

private:
    boost::asio::any_io_executor ex_;
    std::string                  host_;
    uint16_t                     port_;
    uint32_t                     timeout_ms_;
};

} // namespace doh::dns

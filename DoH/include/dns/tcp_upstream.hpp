#pragma once

#include "dns/dns_upstream.hpp"

#include <boost/asio/any_io_executor.hpp>
#include <cstdint>
#include <string>

namespace doh::dns {

// Sends a DNS query over TCP using the RFC 1035 §4.2.2 two-byte length-prefix
// framing. Used as fallback when the UDP response has TC=1.
class TcpUpstream : public DnsUpstream {
public:
    TcpUpstream(boost::asio::any_io_executor ex,
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

#include "dns/dns_forwarder.hpp"
#include "dns/udp_upstream.hpp"
#include "dns/tcp_upstream.hpp"
#include "util/dns_wire.hpp"

#include <boost/asio/error.hpp>
#include <spdlog/spdlog.h>

namespace doh::dns {

DnsForwarder::DnsForwarder(std::string host, uint16_t port, uint32_t timeout_ms)
    : upstream_host_(std::move(host))
    , upstream_port_(port)
    , timeout_ms_   (timeout_ms)
{}

void DnsForwarder::async_forward(boost::asio::any_io_executor ex,
                                  std::vector<uint8_t>         query,
                                  ForwardCallback              cb) {
    auto udp = std::make_shared<UdpUpstream>(ex, upstream_host_, upstream_port_, timeout_ms_);

    // Keep a copy of the query for the potential TCP retry
    udp->async_query(query,
        [ex,
         host    = upstream_host_,
         port    = upstream_port_,
         tms     = timeout_ms_,
         query   = std::move(query),
         cb      = std::move(cb)]
        (boost::system::error_code ec, std::vector<uint8_t> response) mutable
        {
            const bool retry_tcp =
                (!ec && doh::util::is_dns_truncated(response)) ||
                (ec == boost::asio::error::timed_out);

            if (retry_tcp) {
                if (ec == boost::asio::error::timed_out) {
                    spdlog::debug("UDP upstream timed out for {}:{}; retrying over TCP",
                                  host, port);
                }

                // TC=1 or UDP timeout: retry over TCP
                auto tcp_up = std::make_shared<TcpUpstream>(ex, host, port, tms);
                tcp_up->async_query(
                    std::move(query),
                    [cb = std::move(cb)](boost::system::error_code tcp_ec,
                                         std::vector<uint8_t> tcp_response) mutable {
                        cb(tcp_ec, std::move(tcp_response), true);
                    });
            } else {
                cb(ec, std::move(response), false);
            }
        });
}

} // namespace doh::dns

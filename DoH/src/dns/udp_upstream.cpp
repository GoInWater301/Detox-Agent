#include "dns/udp_upstream.hpp"

#include <boost/asio.hpp>
#include <array>
#include <memory>

namespace doh::dns {

namespace net = boost::asio;
using     udp = net::ip::udp;

// ─────────────────────────────────────────────────────────────────────────────
// Internal state for one UDP query (kept alive via shared_ptr)
// ─────────────────────────────────────────────────────────────────────────────
namespace {

struct UdpState : std::enable_shared_from_this<UdpState> {
    udp::socket                sock;
    net::steady_timer          timer;
    std::vector<uint8_t>       query;
    std::array<uint8_t, 65536> rbuf{};
    udp::endpoint              remote;
    QueryCallback              cb;
    bool                       done = false;

    UdpState(net::any_io_executor ex,
             std::vector<uint8_t> q,
             uint32_t             timeout_ms,
             QueryCallback        c)
        : sock (ex, udp::v4())
        , timer(ex)
        , query(std::move(q))
        , cb   (std::move(c))
    {
        timer.expires_after(std::chrono::milliseconds(timeout_ms));
    }

    void start(udp::endpoint ep) {
        remote = ep;

        // Arm timeout
        timer.async_wait([s = shared_from_this()](boost::system::error_code ec) {
            if (!ec && !s->done) {
                s->done = true;
                s->sock.cancel();
                s->cb(net::error::timed_out, {});
            }
        });

        // Send query
        sock.async_send_to(net::buffer(query), ep,
            [s = shared_from_this()](boost::system::error_code ec, std::size_t) {
                if (ec) { s->fail(ec); return; }

                // Receive response
                s->sock.async_receive_from(
                    net::buffer(s->rbuf), s->remote,
                    [s](boost::system::error_code ec2, std::size_t n) {
                        if (!s->done) {
                            s->done = true;
                            s->timer.cancel();
                            if (ec2) s->cb(ec2, {});
                            else     s->cb({}, {s->rbuf.begin(), s->rbuf.begin() + n});
                        }
                    });
            });
    }

    void fail(boost::system::error_code ec) {
        if (!done) { done = true; timer.cancel(); cb(ec, {}); }
    }
};

} // namespace

// ─────────────────────────────────────────────────────────────────────────────

UdpUpstream::UdpUpstream(net::any_io_executor ex,
                          std::string          host,
                          uint16_t             port,
                          uint32_t             timeout_ms)
    : ex_(std::move(ex))
    , host_(std::move(host))
    , port_(port)
    , timeout_ms_(timeout_ms)
{}

void UdpUpstream::async_query(std::vector<uint8_t> query, QueryCallback cb) {
    boost::system::error_code ec;
    const auto addr = net::ip::make_address(host_, ec);
    if (ec) { cb(ec, {}); return; }

    auto s = std::make_shared<UdpState>(ex_, std::move(query), timeout_ms_, std::move(cb));
    s->start(udp::endpoint{addr, port_});
}

} // namespace doh::dns

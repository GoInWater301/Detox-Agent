#include "dns/tcp_upstream.hpp"

#include <boost/asio.hpp>
#include <array>
#include <memory>

namespace doh::dns {

namespace net = boost::asio;
using     tcp = net::ip::tcp;

// ─────────────────────────────────────────────────────────────────────────────
// Internal state for one TCP query (RFC 1035 §4.2.2 length-prefix framing)
// ─────────────────────────────────────────────────────────────────────────────
namespace {

struct TcpState : std::enable_shared_from_this<TcpState> {
    tcp::socket            sock;
    net::steady_timer      timer;
    std::vector<uint8_t>   wire;      // 2-byte big-endian length + raw query
    std::array<uint8_t, 2> lenbuf{};
    std::vector<uint8_t>   response;
    QueryCallback          cb;
    bool                   done = false;

    TcpState(net::any_io_executor ex,
             std::vector<uint8_t> query,
             uint32_t             timeout_ms,
             QueryCallback        c)
        : sock (ex)
        , timer(ex)
        , cb   (std::move(c))
    {
        // Prepend 2-byte big-endian message length
        const auto len = static_cast<uint16_t>(query.size());
        wire.reserve(2 + query.size());
        wire.push_back(static_cast<uint8_t>(len >> 8));
        wire.push_back(static_cast<uint8_t>(len & 0xFFu));
        wire.insert(wire.end(), query.begin(), query.end());

        // TCP is slower; give it twice the UDP timeout
        timer.expires_after(std::chrono::milliseconds(timeout_ms * 2));
    }

    void start(tcp::endpoint ep) {
        timer.async_wait([s = shared_from_this()](boost::system::error_code ec) {
            if (!ec && !s->done) {
                s->done = true;
                s->sock.cancel();
                s->cb(net::error::timed_out, {});
            }
        });

        sock.async_connect(ep,
            [s = shared_from_this()](boost::system::error_code ec) {
                if (ec) { s->fail(ec); return; }
                net::async_write(s->sock, net::buffer(s->wire),
                    [s](boost::system::error_code ec2, std::size_t) {
                        if (ec2) { s->fail(ec2); return; }
                        s->read_length();
                    });
            });
    }

    void read_length() {
        net::async_read(sock, net::buffer(lenbuf),
            [s = shared_from_this()](boost::system::error_code ec, std::size_t) {
                if (ec) { s->fail(ec); return; }
                const uint16_t n = (static_cast<uint16_t>(s->lenbuf[0]) << 8)
                                 |  static_cast<uint16_t>(s->lenbuf[1]);
                s->response.resize(n);
                s->read_body();
            });
    }

    void read_body() {
        net::async_read(sock, net::buffer(response),
            [s = shared_from_this()](boost::system::error_code ec, std::size_t) {
                if (!s->done) {
                    s->done = true;
                    s->timer.cancel();
                    if (ec) s->cb(ec, {});
                    else    s->cb({}, std::move(s->response));
                }
            });
    }

    void fail(boost::system::error_code ec) {
        if (!done) { done = true; timer.cancel(); cb(ec, {}); }
    }
};

} // namespace

// ─────────────────────────────────────────────────────────────────────────────

TcpUpstream::TcpUpstream(net::any_io_executor ex,
                          std::string          host,
                          uint16_t             port,
                          uint32_t             timeout_ms)
    : ex_(std::move(ex))
    , host_(std::move(host))
    , port_(port)
    , timeout_ms_(timeout_ms)
{}

void TcpUpstream::async_query(std::vector<uint8_t> query, QueryCallback cb) {
    boost::system::error_code ec;
    const auto addr = net::ip::make_address(host_, ec);
    if (ec) { cb(ec, {}); return; }

    auto s = std::make_shared<TcpState>(ex_, std::move(query), timeout_ms_, std::move(cb));
    s->start(tcp::endpoint{addr, port_});
}

} // namespace doh::dns

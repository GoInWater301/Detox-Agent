#pragma once

#include <boost/beast.hpp>
#include <boost/beast/ssl.hpp>
#include <memory>
#include <optional>
#include <string>
#include <vector>

#include "config/config.hpp"
#include "dns/dns_forwarder.hpp"
#include "analytics/analytics_client.hpp"
#include "filter/domain_filter.hpp"

namespace doh::session {

namespace beast = boost::beast;
namespace http  = beast::http;
namespace net   = boost::asio;
namespace ssl   = boost::asio::ssl;
using     tcp   = net::ip::tcp;

// Handles the full lifecycle of one HTTPS connection:
//
//   TLS handshake
//     → HTTP read
//       → path / payload validation
//         → domain filter check  (async, Redis; skipped if filter == nullptr)
//           ├─ blocked: synthesize configured DNS block response, HTTP 200, done
//           └─ allowed: DNS forward → clamp TTL → HTTP 200
//                         → fire-and-forget analytics
//
// Keep-alive: after each successful response the session loops back to read.
// Lifetime:   managed by shared_ptr via enable_shared_from_this.
class HttpsSession : public std::enable_shared_from_this<HttpsSession> {
public:
    HttpsSession(tcp::socket&&                               socket,
                 ssl::context&                               ssl_ctx,
                 const Config&                               cfg,
                 std::shared_ptr<analytics::AnalyticsClient> analytics,
                 std::shared_ptr<filter::DomainFilter>       filter = nullptr);

    void run();

private:
    // ── async chain ──────────────────────────────────────────────────────────
    void do_handshake();
    void on_handshake(beast::error_code ec);

    void do_read();
    void on_read(beast::error_code ec, std::size_t bytes);

    void handle_doh_request();

    // Called after the filter check (or directly if no filter is configured).
    void do_forward(std::string user_id,
                    std::string queried_domain,
                    std::vector<uint8_t> payload,
                    std::chrono::steady_clock::time_point t0,
                    int64_t ts_us);

    void do_send(http::response<http::vector_body<uint8_t>> res);
    void on_write(beast::error_code ec, std::size_t bytes, bool close);

    void do_close();

    // ── helpers ──────────────────────────────────────────────────────────────
    http::response<http::vector_body<uint8_t>>
    make_error_response(http::status status, std::string_view body);

    http::response<http::vector_body<uint8_t>>
    make_dns_response(std::vector<uint8_t> dns_body, uint32_t cache_max_age);

    std::optional<std::vector<uint8_t>> extract_dns_payload() const;

    // ── state ────────────────────────────────────────────────────────────────
    beast::ssl_stream<tcp::socket>       stream_;
    beast::flat_buffer                   buffer_;
    std::optional<http::request_parser<http::string_body>> parser_;
    http::request<http::string_body>     req_;
    std::string                          client_ip_;

    Config                               cfg_;
    dns::DnsForwarder                    forwarder_;
    std::shared_ptr<analytics::AnalyticsClient> analytics_;
    std::shared_ptr<filter::DomainFilter>       filter_;
};

} // namespace doh::session

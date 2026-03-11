#include "session/https_session.hpp"
#include "util/path_parser.hpp"
#include "util/base64url.hpp"
#include "util/dns_wire.hpp"

#include <spdlog/spdlog.h>
#include <chrono>

namespace doh::session {

static constexpr std::size_t kBodyLimit = 65535;  // max DNS message (RFC 1035)

// ─────────────────────────────────────────────────────────────────────────────
// Construction
// ─────────────────────────────────────────────────────────────────────────────

HttpsSession::HttpsSession(tcp::socket&&  socket,
                            ssl::context&  ssl_ctx,
                            const Config&  cfg,
                            std::shared_ptr<analytics::AnalyticsClient> analytics,
                            std::shared_ptr<filter::DomainFilter>       filter)
    : stream_   (std::move(socket), ssl_ctx)
    , cfg_      (cfg)
    , forwarder_(cfg.dns_upstream_host, cfg.dns_upstream_port, cfg.dns_timeout_ms)
    , analytics_(std::move(analytics))
    , filter_   (std::move(filter))
{
    boost::system::error_code ec;
    const auto ep = stream_.next_layer().remote_endpoint(ec);
    client_ip_    = ec ? "unknown" : ep.address().to_string();
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

void HttpsSession::run() {
    net::dispatch(stream_.get_executor(),
                  [self = shared_from_this()] { self->do_handshake(); });
}

// ─────────────────────────────────────────────────────────────────────────────
// TLS handshake
// ─────────────────────────────────────────────────────────────────────────────

void HttpsSession::do_handshake() {
    stream_.async_handshake(ssl::stream_base::server,
        [self = shared_from_this()](beast::error_code ec) {
            self->on_handshake(ec);
        });
}

void HttpsSession::on_handshake(beast::error_code ec) {
    if (ec) {
        spdlog::debug("TLS handshake failed [{}]: {}", client_ip_, ec.message());
        return;
    }
    do_read();
}

// ─────────────────────────────────────────────────────────────────────────────
// HTTP read
// ─────────────────────────────────────────────────────────────────────────────

void HttpsSession::do_read() {
    parser_.emplace();
    parser_->body_limit(kBodyLimit);

    http::async_read(stream_, buffer_, *parser_,
        [self = shared_from_this()](beast::error_code ec, std::size_t n) {
            if (!ec) self->req_ = self->parser_->release();
            self->on_read(ec, n);
        });
}

void HttpsSession::on_read(beast::error_code ec, std::size_t) {
    if (ec == http::error::end_of_stream) { do_close(); return; }

    if (ec == http::error::body_limit) {
        do_send(make_error_response(http::status::payload_too_large,
                                    "DNS message exceeds 65535 bytes"));
        return;
    }
    if (ec) {
        spdlog::debug("Read error [{}]: {}", client_ip_, ec.message());
        return;
    }

    handle_doh_request();
}

// ─────────────────────────────────────────────────────────────────────────────
// DoH request dispatch
// ─────────────────────────────────────────────────────────────────────────────

void HttpsSession::handle_doh_request() {
    // ── 1. Validate and extract user_id (zero-copy string_view) ──────────────
    const auto user_id_opt = util::parse_user_id(req_.target());
    if (!user_id_opt) {
        spdlog::warn("Rejected request [{}] target={} reason=invalid_path",
                     client_ip_, req_.target());
        do_send(make_error_response(http::status::forbidden,
                                    "Path must be /{user_id}/dns-query"));
        return;
    }
    std::string user_id{*user_id_opt};
    spdlog::info("DoH request [{}] method={} user={} target={}",
                 client_ip_, req_.method_string(), user_id, req_.target());

    // ── 2. Extract DNS wire payload ───────────────────────────────────────────
    auto payload_opt = extract_dns_payload();
    if (!payload_opt || payload_opt->empty()) {
        spdlog::warn("Rejected request [{}/{}] reason=invalid_payload",
                     client_ip_, user_id);
        do_send(make_error_response(http::status::bad_request,
                                    "Missing or invalid DNS payload"));
        return;
    }
    std::vector<uint8_t> payload = std::move(*payload_opt);

    // ── 3. Extract queried domain from DNS QNAME (zero-alloc) ────────────────
    std::string queried_domain = util::dns_query_domain(payload);
    if (queried_domain.empty()) {
        spdlog::warn("Failed to parse DNS question [{}/{}]; bypassing filter",
                     client_ip_, user_id);
    } else {
        spdlog::debug("DoH query [{}/{}] → {}", client_ip_, user_id, queried_domain);
    }

    // ── 4. Timing metadata ────────────────────────────────────────────────────
    const auto t0    = std::chrono::steady_clock::now();
    const auto ts_us = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();

    // ── 5. Domain filter check (async; skipped if no filter configured) ───────
    if (filter_) {
        auto filter_user_id = user_id;
        auto filter_domain = queried_domain;
        filter_->async_check(std::move(filter_user_id), std::move(filter_domain),
            stream_.get_executor(),
            [self = shared_from_this(),
             user_id        = std::move(user_id),
             queried_domain = std::move(queried_domain),
             payload        = std::move(payload),
             t0, ts_us]
            (bool blocked) mutable
            {
                if (blocked) {
                    // Synthesize the configured DNS block response. The query
                    // never touches the upstream DNS server.
                    std::vector<uint8_t> response;
                    const char* policy_name = "NXDOMAIN";
                    switch (self->cfg_.block_response_policy) {
                    case BlockResponsePolicy::refused:
                        response = util::dns_make_refused(payload);
                        policy_name = "REFUSED";
                        break;
                    case BlockResponsePolicy::nxdomain:
                    default:
                        response = util::dns_make_nxdomain(payload);
                        break;
                    }
                    if (response.empty()) {
                        self->do_send(self->make_error_response(
                            http::status::bad_request, "Malformed DNS query"));
                        return;
                    }
                    spdlog::info("{} synthesized [{}/{}] domain={}",
                                 policy_name, self->client_ip_, user_id,
                                 queried_domain);
                    self->do_send(self->make_dns_response(std::move(response),
                                                          self->cfg_.dns_min_ttl_s));
                    return;
                }
                // Allowed: proceed to upstream DNS
                self->do_forward(std::move(user_id), std::move(queried_domain),
                                 std::move(payload), t0, ts_us);
            });
    } else {
        // No filter configured: forward directly
        do_forward(std::move(user_id), std::move(queried_domain),
                   std::move(payload), t0, ts_us);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DNS forwarding (called after a successful filter check)
// ─────────────────────────────────────────────────────────────────────────────

void HttpsSession::do_forward(std::string  user_id,
                               std::string  queried_domain,
                               std::vector<uint8_t> payload,
                               std::chrono::steady_clock::time_point t0,
                               int64_t ts_us)
{
    auto self = shared_from_this();

    forwarder_.async_forward(
        stream_.get_executor(),
        payload,  // copy for analytics; moved into lambda
        [self,
         user_id        = std::move(user_id),
         queried_domain = std::move(queried_domain),
         raw_query      = payload,
         t0, ts_us]
        (beast::error_code ec, std::vector<uint8_t> dns_response, bool used_tcp) mutable
        {
            if (ec) {
                spdlog::warn("DNS forward error [{}]: {}", self->client_ip_, ec.message());
                self->do_send(self->make_error_response(
                    http::status::bad_gateway, "DNS upstream error"));
                return;
            }

            const auto latency_us = static_cast<uint32_t>(
                std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now() - t0).count());

            // Clamp all RR TTLs to [min_ttl, max_ttl] for monitoring density
            const uint32_t effective_ttl = util::dns_clamp_response_ttl(
                dns_response,
                self->cfg_.dns_min_ttl_s,
                self->cfg_.dns_max_ttl_s);

            spdlog::info("DoH response [{}/{}] domain={} bytes={} latency_us={} upstream={}",
                         self->client_ip_, user_id, queried_domain,
                         dns_response.size(), latency_us,
                         used_tcp ? "tcp" : "udp");

            // Fire-and-forget analytics (non-blocking)
            if (self->analytics_) {
                self->analytics_->send({
                    .user_id        = user_id,
                    .queried_domain = queried_domain,
                    .client_ip      = self->client_ip_,
                    .raw_query      = std::move(raw_query),
                    .raw_response   = dns_response,
                    .timestamp_us   = ts_us,
                    .latency_us     = latency_us,
                    .used_tcp       = used_tcp,
                });
            }

            self->do_send(self->make_dns_response(std::move(dns_response), effective_ttl));
        });
}

// ─────────────────────────────────────────────────────────────────────────────
// Payload extraction (GET base64url / POST binary)
// ─────────────────────────────────────────────────────────────────────────────

std::optional<std::vector<uint8_t>> HttpsSession::extract_dns_payload() const {
    if (req_.method() == http::verb::post) {
        const auto& body = req_.body();
        return std::vector<uint8_t>{body.begin(), body.end()};
    }

    if (req_.method() == http::verb::get) {
        const std::string_view target{req_.target()};
        const auto qpos = target.find('?');
        if (qpos == std::string_view::npos) return std::nullopt;

        std::string_view params = target.substr(qpos + 1);
        while (!params.empty()) {
            const auto amp = params.find('&');
            const auto kv  = params.substr(0, amp);
            if (kv.starts_with("dns=")) {
                return util::base64url_decode(kv.substr(4));
            }
            if (amp == std::string_view::npos) break;
            params.remove_prefix(amp + 1);
        }
        return std::nullopt;
    }

    return std::nullopt;
}

// ─────────────────────────────────────────────────────────────────────────────
// HTTP write
// ─────────────────────────────────────────────────────────────────────────────

void HttpsSession::do_send(http::response<http::vector_body<uint8_t>> res) {
    const bool close = res.need_eof();
    auto sp = std::make_shared<http::response<http::vector_body<uint8_t>>>(std::move(res));

    http::async_write(stream_, *sp,
        [self = shared_from_this(), sp, close]
        (beast::error_code ec, std::size_t n) {
            self->on_write(ec, n, close);
        });
}

void HttpsSession::on_write(beast::error_code ec, std::size_t, bool close) {
    if (ec) {
        spdlog::debug("Write error [{}]: {}", client_ip_, ec.message());
        return;
    }
    if (close) { do_close(); return; }
    do_read();
}

// ─────────────────────────────────────────────────────────────────────────────
// Graceful SSL shutdown
// ─────────────────────────────────────────────────────────────────────────────

void HttpsSession::do_close() {
    stream_.async_shutdown(
        [self = shared_from_this()](beast::error_code ec) {
            if (ec &&
                ec != net::error::eof &&
                ec != ssl::error::stream_truncated)
                spdlog::debug("Shutdown [{}]: {}", self->client_ip_, ec.message());
        });
}

// ─────────────────────────────────────────────────────────────────────────────
// Response builders
// ─────────────────────────────────────────────────────────────────────────────

http::response<http::vector_body<uint8_t>>
HttpsSession::make_dns_response(std::vector<uint8_t> dns_body, uint32_t cache_max_age) {
    http::response<http::vector_body<uint8_t>> res{
        http::status::ok, req_.version()};
    res.set(http::field::content_type, "application/dns-message");
    res.set(http::field::cache_control, "max-age=" + std::to_string(cache_max_age));
    res.keep_alive(req_.keep_alive());
    res.body() = std::move(dns_body);
    res.prepare_payload();
    return res;
}

http::response<http::vector_body<uint8_t>>
HttpsSession::make_error_response(http::status status, std::string_view body) {
    http::response<http::vector_body<uint8_t>> res{status, req_.version()};
    res.set(http::field::content_type, "text/plain");
    res.keep_alive(false);
    res.body().assign(body.begin(), body.end());
    res.prepare_payload();
    return res;
}

} // namespace doh::session

#include <boost/asio.hpp>
#include <spdlog/spdlog.h>

#include <memory>
#include <stdexcept>
#include <thread>
#include <vector>

#include "config/config.hpp"
#include "server/tls_context.hpp"
#include "server/listener.hpp"
#include "analytics/grpc_stream_client.hpp"
#include "filter/redis_domain_filter.hpp"

namespace {

spdlog::level::level_enum parse_log_level(std::string_view level) {
    if (level == "trace") return spdlog::level::trace;
    if (level == "debug") return spdlog::level::debug;
    if (level == "info") return spdlog::level::info;
    if (level == "warn") return spdlog::level::warn;
    if (level == "error") return spdlog::level::err;
    if (level == "critical") return spdlog::level::critical;
    if (level == "off") return spdlog::level::off;
    throw std::runtime_error("Unsupported log level");
}

} // namespace

int main() {
    spdlog::set_pattern("[%Y-%m-%dT%H:%M:%S.%e] [%^%l%$] %v");

    // ── Configuration (env vars > .env file > defaults) ───────────────────
    const auto cfg = doh::Config::from_env();
    spdlog::set_level(parse_log_level(cfg.log_level));
    spdlog::flush_on(spdlog::level::info);
    spdlog::info("DoH forwarder — {}:{} | upstream={}:{} | threads={} | filter={}",
                 cfg.listen_address, cfg.listen_port,
                 cfg.dns_upstream_host, cfg.dns_upstream_port,
                 cfg.thread_count,
                 cfg.filter_enabled ? "redis" : "off");
    spdlog::info("Log level set to {}", cfg.log_level);

    // ── TLS context (fail-fast on bad cert path) ───────────────────────────
    auto ssl_ctx = doh::server::make_tls_context(cfg.cert_chain_file,
                                                  cfg.private_key_file);

    // ── io_context (N threads) ─────────────────────────────────────────────
    boost::asio::io_context ioc{static_cast<int>(cfg.thread_count)};

    // ── Domain filter (optional, Redis-backed) ─────────────────────────────
    std::shared_ptr<doh::filter::DomainFilter> filter;
    if (cfg.filter_enabled) {
        filter = std::make_shared<doh::filter::RedisDomainFilter>(
            ioc, cfg.redis_host, cfg.redis_port, cfg.redis_password,
            cfg.redis_timeout_ms, cfg.redis_refresh_ms, cfg.filter_fail_open);
    }

    // ── Analytics gRPC client (fault-tolerant, fire-and-forget) ───────────
    auto analytics = std::make_shared<doh::analytics::GrpcStreamClient>(
        cfg.analytics_endpoint, cfg.analytics_queue_cap);

    // ── Graceful shutdown ──────────────────────────────────────────────────
    boost::asio::signal_set signals(ioc, SIGINT, SIGTERM);
    signals.async_wait([&ioc](boost::system::error_code, int sig) {
        spdlog::info("Signal {} received — stopping...", sig);
        ioc.stop();
    });

    // ── Start TCP acceptor ─────────────────────────────────────────────────
    std::make_shared<doh::server::Listener>(
        ioc, ssl_ctx, cfg, analytics, filter)->run();

    // ── Thread pool ────────────────────────────────────────────────────────
    std::vector<std::jthread> pool;
    pool.reserve(cfg.thread_count - 1);
    for (std::size_t i = 0; i + 1 < cfg.thread_count; ++i) {
        pool.emplace_back([&ioc] { ioc.run(); });
    }
    ioc.run();

    spdlog::info("Server stopped.");
    return 0;
}

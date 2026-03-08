#include <boost/asio.hpp>
#include <spdlog/spdlog.h>

#include <memory>
#include <thread>
#include <vector>

#include "config/config.hpp"
#include "server/tls_context.hpp"
#include "server/listener.hpp"
#include "analytics/grpc_stream_client.hpp"
#include "filter/redis_domain_filter.hpp"

int main() {
    spdlog::set_level(spdlog::level::info);
    spdlog::set_pattern("[%Y-%m-%dT%H:%M:%S.%e] [%^%l%$] %v");

    // ── Configuration (env vars > .env file > defaults) ───────────────────
    const auto cfg = doh::Config::from_env();
    spdlog::info("DoH forwarder — {}:{} | upstream={}:{} | threads={} | filter={}",
                 cfg.listen_address, cfg.listen_port,
                 cfg.dns_upstream_host, cfg.dns_upstream_port,
                 cfg.thread_count,
                 cfg.filter_enabled ? "redis" : "off");

    // ── TLS context (fail-fast on bad cert path) ───────────────────────────
    auto ssl_ctx = doh::server::make_tls_context(cfg.cert_chain_file,
                                                  cfg.private_key_file);

    // ── io_context (N threads) ─────────────────────────────────────────────
    boost::asio::io_context ioc{static_cast<int>(cfg.thread_count)};

    // ── Domain filter (optional, Redis-backed) ─────────────────────────────
    std::shared_ptr<doh::filter::DomainFilter> filter;
    if (cfg.filter_enabled) {
        filter = std::make_shared<doh::filter::RedisDomainFilter>(
            ioc, cfg.redis_host, cfg.redis_port, cfg.redis_password);
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

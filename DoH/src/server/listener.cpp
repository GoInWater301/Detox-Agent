#include "server/listener.hpp"
#include "session/https_session.hpp"

#include <spdlog/spdlog.h>

namespace doh::server {

Listener::Listener(net::io_context& ioc,
                   ssl::context&    ssl_ctx,
                   const Config&    cfg,
                   std::shared_ptr<analytics::AnalyticsClient> analytics,
                   std::shared_ptr<filter::DomainFilter>       filter)
    : ioc_      (ioc)
    , ssl_ctx_  (ssl_ctx)
    , acceptor_ (net::make_strand(ioc))
    , cfg_      (cfg)
    , analytics_(std::move(analytics))
    , filter_   (std::move(filter))
{
    const auto addr = net::ip::make_address(cfg.listen_address);
    const tcp::endpoint ep{addr, cfg.listen_port};

    acceptor_.open(ep.protocol());
    acceptor_.set_option(net::socket_base::reuse_address(true));
    acceptor_.bind(ep);
    acceptor_.listen(net::socket_base::max_listen_connections);

    spdlog::info("Listening on {}:{}", cfg.listen_address, cfg.listen_port);
}

void Listener::run() { do_accept(); }

void Listener::do_accept() {
    acceptor_.async_accept(
        net::make_strand(ioc_),
        [self = shared_from_this()](boost::system::error_code ec, tcp::socket sock) {
            self->on_accept(ec, std::move(sock));
        });
}

void Listener::on_accept(boost::system::error_code ec, tcp::socket socket) {
    if (ec) {
        spdlog::error("Accept error: {}", ec.message());
    } else {
        std::make_shared<session::HttpsSession>(
            std::move(socket), ssl_ctx_, cfg_, analytics_, filter_)->run();
    }
    do_accept();
}

} // namespace doh::server

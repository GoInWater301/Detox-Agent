#pragma once

#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <memory>

#include "config/config.hpp"

namespace doh::analytics { struct AnalyticsClient; }
namespace doh::filter    { struct DomainFilter; }

namespace doh::server {

namespace net = boost::asio;
namespace ssl = boost::asio::ssl;
using     tcp = net::ip::tcp;

// Accepts incoming TCP connections on the configured endpoint and spawns
// one HttpsSession per connection. Runs entirely on the io_context strand.
class Listener : public std::enable_shared_from_this<Listener> {
public:
    Listener(net::io_context&                            ioc,
             ssl::context&                               ssl_ctx,
             const Config&                               cfg,
             std::shared_ptr<analytics::AnalyticsClient> analytics,
             std::shared_ptr<filter::DomainFilter>       filter = nullptr);

    void run();

private:
    void do_accept();
    void on_accept(boost::system::error_code ec, tcp::socket socket);

    net::io_context&                            ioc_;
    ssl::context&                               ssl_ctx_;
    tcp::acceptor                               acceptor_;
    Config                                      cfg_;
    std::shared_ptr<analytics::AnalyticsClient> analytics_;
    std::shared_ptr<filter::DomainFilter>       filter_;
};

} // namespace doh::server

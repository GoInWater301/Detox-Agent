#include "server/tls_context.hpp"

#include <spdlog/spdlog.h>

namespace doh::server {

ssl::context make_tls_context(const std::string& cert_chain_file,
                               const std::string& private_key_file) {
    ssl::context ctx{ssl::context::tls_server};

    ctx.set_options(ssl::context::default_workarounds |
                    ssl::context::no_sslv2           |
                    ssl::context::no_sslv3           |
                    ssl::context::single_dh_use);

    ctx.use_certificate_chain_file(cert_chain_file);
    ctx.use_private_key_file(private_key_file, ssl::context::pem);

    spdlog::info("TLS context loaded — cert={} key={}",
                 cert_chain_file, private_key_file);
    return ctx;
}

} // namespace doh::server

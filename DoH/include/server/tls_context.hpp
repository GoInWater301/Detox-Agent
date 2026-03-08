#pragma once

#include <boost/asio/ssl/context.hpp>
#include <string>

namespace doh::server {

namespace ssl = boost::asio::ssl;

// Loads a Let's Encrypt certificate chain and private key into an ssl::context
// configured for TLS 1.2+ server use.
// Throws boost::system::system_error on file I/O failure.
[[nodiscard]] ssl::context make_tls_context(const std::string& cert_chain_file,
                                            const std::string& private_key_file);

} // namespace doh::server

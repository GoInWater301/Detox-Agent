#include "server/tls_context.hpp"

#include <filesystem>
#include <fstream>
#include <stdexcept>

#include <spdlog/spdlog.h>

namespace doh::server {

namespace {

std::string require_readable_file(const std::string& path,
                                  std::string_view   label) {
    namespace fs = std::filesystem;

    std::ifstream stream(path, std::ios::binary);
    if (!stream.good()) {
        throw std::runtime_error("TLS " + std::string(label) +
                                 " file is not readable: " + path);
    }

    std::error_code ec;
    const auto canonical = fs::weakly_canonical(path, ec);
    return ec ? path : canonical.string();
}

} // namespace

ssl::context make_tls_context(const std::string& cert_chain_file,
                              const std::string& private_key_file) {
    const auto cert_path = require_readable_file(cert_chain_file, "certificate");
    const auto key_path  = require_readable_file(private_key_file, "private key");

    ssl::context ctx{ssl::context::tls_server};

    ctx.set_options(ssl::context::default_workarounds |
                    ssl::context::no_sslv2           |
                    ssl::context::no_sslv3           |
                    ssl::context::single_dh_use);

    ctx.set_default_verify_paths();
    ctx.use_certificate_chain_file(cert_path);
    ctx.use_private_key_file(key_path, ssl::context::pem);

    spdlog::info("TLS context loaded — cert={} key={}",
                 cert_path, key_path);
    return ctx;
}

} // namespace doh::server

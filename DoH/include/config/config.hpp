#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

namespace doh {

enum class BlockResponsePolicy {
    nxdomain,
    refused,
};

// All runtime settings. Populated once at startup from environment variables.
// Treated as immutable after construction.
struct Config {
    // Network
    std::string listen_address   = "0.0.0.0";
    uint16_t    listen_port      = 443;
    std::size_t thread_count     = 0;      // 0 → hardware_concurrency

    // TLS (Let's Encrypt)
    std::string cert_chain_file  = "certs/fullchain.pem";
    std::string private_key_file = "certs/privkey.pem";

    // DNS upstream
    std::string dns_upstream_host = "8.8.8.8";
    uint16_t    dns_upstream_port = 53;
    uint32_t    dns_timeout_ms    = 3000;  // UDP timeout before TCP fallback

    // DNS TTL override (사용자 모니터링 목적)
    // 업스트림 응답의 모든 TTL을 [min, max] 범위로 강제 조정.
    // 짧은 TTL = 클라이언트가 자주 재질의 → 더 많은 모니터링 데이터 수집 가능.
    uint32_t dns_min_ttl_s = 30;   // 최소 TTL (초). 업스트림 TTL < min → min으로 올림
    uint32_t dns_max_ttl_s = 60;   // 최대 TTL (초). 업스트림 TTL > max → max로 내림

    // Domain filter (Redis)
    bool        filter_enabled   = false;
    std::string redis_host       = "127.0.0.1";
    uint16_t    redis_port       = 6379;
    std::string redis_password   = "";
    uint32_t    redis_timeout_ms = 1000;
    uint32_t    redis_refresh_ms = 1000;
    bool        filter_fail_open = true;
    BlockResponsePolicy block_response_policy = BlockResponsePolicy::nxdomain;

    // Analytics gRPC (fire-and-forget)
    std::string analytics_endpoint  = "localhost:50051";
    std::size_t analytics_queue_cap = 4096;  // max buffered events before drop

    // Logging
    std::string log_level = "debug";

    [[nodiscard]] static Config from_env();
};

} // namespace doh

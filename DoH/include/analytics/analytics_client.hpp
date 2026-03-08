#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace doh::analytics {

struct QueryEvent {
    std::string          user_id;          // URL 경로 /{user_id}/dns-query 에서 추출
    std::string          queried_domain;   // DNS QNAME (e.g. "example.com")
    std::string          client_ip;
    std::vector<uint8_t> raw_query;
    std::vector<uint8_t> raw_response;
    int64_t              timestamp_us = 0;  // system_clock, microseconds since epoch
    uint32_t             latency_us   = 0;  // steady_clock, end-to-end resolve time
    bool                 used_tcp     = false;
};

// Thread-safe, non-blocking analytics sink.
// Implementations MUST NOT block or throw from send().
struct AnalyticsClient {
    virtual ~AnalyticsClient() = default;
    virtual void send(QueryEvent event) noexcept = 0;
};

} // namespace doh::analytics

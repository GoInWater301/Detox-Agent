package com.pnu.detox_agent.webserver.dto;

public record DomainUsageDto(
        String domain,
        long requestCount,
        long firstAccess,
        long lastAccess,
        long totalDuration,
        long averageResponseTimeMs) {
}

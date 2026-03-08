package com.pnu.detox_agent.webserver.entity;

public record DomainUsageRecord(
        String userId,
        String domain,
        long firstAccess,
        long lastAccess,
        long count,
        long totalDuration,
        long averageResponseTimeMs) {
}

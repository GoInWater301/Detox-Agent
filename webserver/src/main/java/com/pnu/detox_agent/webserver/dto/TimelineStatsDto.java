package com.pnu.detox_agent.webserver.dto;

public record TimelineStatsDto(
        String bucket,
        long totalQueries,
        long uniqueDomains) {
}

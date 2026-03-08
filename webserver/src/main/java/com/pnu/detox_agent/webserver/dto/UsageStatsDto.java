package com.pnu.detox_agent.webserver.dto;

import java.util.List;

public record UsageStatsDto(
        String userId,
        String period,
        long totalQueries,
        long uniqueDomains,
        List<DomainUsageDto> topDomains) {
}

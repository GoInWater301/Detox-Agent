package com.pnu.detox_agent.webserver.dto.ai;

import com.pnu.detox_agent.webserver.dto.UsageStatsDto;

public record AiReviewRequestDto(
        String sessionId,
        String prompt,
        UsageStatsDto usage) {
}

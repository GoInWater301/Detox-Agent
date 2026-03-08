package com.pnu.detox_agent.webserver.dto.ai;

public record AiReviewSseEventDto(
        String type,
        String token,
        boolean done,
        String model,
        String messageId,
        String error) {
}

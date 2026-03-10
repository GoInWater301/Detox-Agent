package com.pnu.detox_agent.webserver.dto.auth;

public record AuthResponseDto(
        String accessToken,
        String tokenType,
        String username,
        String dohUrl
) {
    public AuthResponseDto(String accessToken, String username, String dohUrl) {
        this(accessToken, "Bearer", username, dohUrl);
    }
}

package com.pnu.detox_agent.webserver.controller;

import com.pnu.detox_agent.webserver.dto.auth.AuthResponseDto;
import com.pnu.detox_agent.webserver.dto.auth.LoginRequestDto;
import com.pnu.detox_agent.webserver.dto.auth.RegisterRequestDto;
import com.pnu.detox_agent.webserver.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입 및 로그인")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "회원가입", description = "새 사용자를 등록하고 JWT 토큰 및 개인 DoH URL을 반환합니다.")
    public Mono<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "자격증명을 검증하고 JWT 토큰 및 DoH URL을 반환합니다.")
    public Mono<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }
}

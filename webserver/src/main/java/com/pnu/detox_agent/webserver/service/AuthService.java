package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.dto.auth.AuthResponseDto;
import com.pnu.detox_agent.webserver.dto.auth.LoginRequestDto;
import com.pnu.detox_agent.webserver.dto.auth.RegisterRequestDto;
import com.pnu.detox_agent.webserver.entity.DoHEndpointEntity;
import com.pnu.detox_agent.webserver.entity.UserEntity;
import com.pnu.detox_agent.webserver.repository.UserRepository;
import com.pnu.detox_agent.webserver.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DoHAddressService doHAddressService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입: 사용자 생성 + DoH 엔드포인트 자동 배정
     */
    public Mono<AuthResponseDto> register(RegisterRequestDto request) {
        return userRepository.existsByUsername(request.username())
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Mono.<Boolean>error(
                                new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 사용자명입니다."));
                    }
                    return userRepository.existsByEmail(request.email());
                })
                .flatMap(emailExists -> {
                    if (emailExists) {
                        return Mono.<DoHEndpointEntity>error(
                                new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."));
                    }
                    return doHAddressService.assignDoHEndpoint();
                })
                .flatMap(endpoint -> {
                    String dohToken = UUID.randomUUID().toString().replace("-", "");

                    UserEntity user = UserEntity.builder()
                            .username(request.username())
                            .email(request.email())
                            .passwordHash(passwordEncoder.encode(request.password()))
                            .role("USER")
                            .dohToken(dohToken)
                            .dohEndpointId(endpoint.getId())
                            .enabled(true)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return userRepository.save(user)
                            .flatMap(saved ->
                                    doHAddressService.incrementUserCount(endpoint.getId())
                                            .thenReturn(saved)
                            )
                            .flatMap(saved ->
                                    doHAddressService.getDoHUrl(saved)
                                            .map(dohUrl -> {
                                                String token = jwtService.generateToken(saved.getUsername());
                                                log.info("새 사용자 등록 완료: username={}, dohUrl={}", saved.getUsername(), dohUrl);
                                                return new AuthResponseDto(token, saved.getUsername(), dohUrl);
                                            })
                                            .switchIfEmpty(Mono.fromSupplier(() -> {
                                                String token = jwtService.generateToken(saved.getUsername());
                                                log.info("새 사용자 등록 완료: username={}, dohUrl=null", saved.getUsername());
                                                return new AuthResponseDto(token, saved.getUsername(), null);
                                            }))
                            );
                });
    }

    /**
     * 로그인: 자격증명 검증 후 JWT + DoH URL 반환
     */
    public Mono<AuthResponseDto> login(LoginRequestDto request) {
        return userRepository.findByUsername(request.username())
                .filter(user -> user.isEnabled() && passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자명 또는 비밀번호가 올바르지 않습니다.")))
                .flatMap(user ->
                        doHAddressService.getDoHUrl(user)
                                .map(dohUrl -> new AuthResponseDto(
                                        jwtService.generateToken(user.getUsername()),
                                        user.getUsername(),
                                        dohUrl
                                ))
                                .switchIfEmpty(Mono.fromSupplier(() -> new AuthResponseDto(
                                        jwtService.generateToken(user.getUsername()),
                                        user.getUsername(),
                                        null
                                )))
                );
    }
}

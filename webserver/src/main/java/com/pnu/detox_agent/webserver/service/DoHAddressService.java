package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.entity.DoHEndpointEntity;
import com.pnu.detox_agent.webserver.entity.UserEntity;
import com.pnu.detox_agent.webserver.repository.DoHEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoHAddressService {

    private final DoHEndpointRepository doHEndpointRepository;

    @Value("${doh.fallback-base-url}")
    private String fallbackBaseUrl;

    /**
     * 로드 밸런싱 기반으로 가장 여유 있는 DoH 엔드포인트 선택
     * DB에 엔드포인트가 없으면 fallback URL 기반 임시 엔드포인트 반환
     */
    public Mono<DoHEndpointEntity> assignDoHEndpoint() {
        return doHEndpointRepository.findLeastLoadedEndpoint()
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("활성 DoH 엔드포인트가 없어 fallback URL을 사용합니다: {}", fallbackBaseUrl);
                    return DoHEndpointEntity.builder()
                            .id(null)
                            .name("fallback")
                            .baseUrl(fallbackBaseUrl)
                            .region("KR")
                            .currentUsers(0)
                            .maxUsers(Integer.MAX_VALUE)
                            .active(true)
                            .build();
                }));
    }

    /**
     * 엔드포인트 사용자 수 증가 (실제 DB 엔드포인트인 경우에만)
     */
    public Mono<Void> incrementUserCount(Long endpointId) {
        if (endpointId == null) {
            return Mono.empty();
        }
        return doHEndpointRepository.incrementCurrentUsers(endpointId)
                .doOnNext(updated -> log.debug("DoH 엔드포인트 {} 사용자 수 증가 완료", endpointId))
                .then();
    }

    /**
     * 사용자의 개인 DoH URL 반환
     * 형식: {endpoint.baseUrl}/{doh_token}/dns-query
     * 예시: https://doh.leeswallow.click/{doh_token}/dns-query
     */
    public Mono<String> getDoHUrl(UserEntity user) {
        if (user.getDohToken() == null) {
            return Mono.empty();
        }
        if (user.getDohEndpointId() == null) {
            return Mono.just(buildDoHUrl(fallbackBaseUrl, user.getDohToken()));
        }
        return doHEndpointRepository.findById(user.getDohEndpointId())
                .map(endpoint -> buildDoHUrl(endpoint.getBaseUrl(), user.getDohToken()))
                .switchIfEmpty(Mono.just(buildDoHUrl(fallbackBaseUrl, user.getDohToken())));
    }

    private String buildDoHUrl(String baseUrl, String dohToken) {
        return baseUrl.stripTrailing() + "/" + dohToken + "/dns-query";
    }
}

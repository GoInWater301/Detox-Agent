package com.pnu.detox_agent.webserver.repository;

import com.pnu.detox_agent.webserver.entity.DoHEndpointEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface DoHEndpointRepository extends ReactiveCrudRepository<DoHEndpointEntity, Long> {

    /**
     * 현재 사용자 수가 가장 적은 활성 엔드포인트 조회 (로드 밸런싱)
     */
    @Query("SELECT * FROM doh_endpoints WHERE active = true AND current_users < max_users ORDER BY current_users ASC LIMIT 1")
    Mono<DoHEndpointEntity> findLeastLoadedEndpoint();

    /**
     * 엔드포인트 사용자 수 증가 (원자적)
     */
    @Modifying
    @Query("UPDATE doh_endpoints SET current_users = current_users + 1 WHERE id = :id")
    Mono<Integer> incrementCurrentUsers(Long id);
}

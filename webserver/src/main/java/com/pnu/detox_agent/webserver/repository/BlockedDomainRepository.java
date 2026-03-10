package com.pnu.detox_agent.webserver.repository;

import com.pnu.detox_agent.webserver.entity.BlockedDomainEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BlockedDomainRepository extends ReactiveCrudRepository<BlockedDomainEntity, Long> {

    Flux<BlockedDomainEntity> findAllByUserTokenOrderByDomainAsc(String userToken);

    Mono<BlockedDomainEntity> findByUserTokenAndDomain(String userToken, String domain);

    Mono<Void> deleteByUserTokenAndDomain(String userToken, String domain);
}

package com.pnu.detox_agent.webserver.repository;

import com.pnu.detox_agent.webserver.entity.BlockedDomainEntity;
import java.util.Collection;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BlockedDomainRepository extends ReactiveCrudRepository<BlockedDomainEntity, Long> {

    Flux<BlockedDomainEntity> findAllByUserTokenOrderByDomainAsc(String userToken);

    Flux<BlockedDomainEntity> findAllByUserTokenAndDomainIn(String userToken, Collection<String> domains);

    Mono<BlockedDomainEntity> findByUserTokenAndDomain(String userToken, String domain);

    Mono<Void> deleteAllByUserTokenAndDomainIn(String userToken, Collection<String> domains);

    Mono<Void> deleteByUserTokenAndDomain(String userToken, String domain);
}

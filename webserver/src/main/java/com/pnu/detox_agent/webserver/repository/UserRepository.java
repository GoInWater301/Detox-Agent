package com.pnu.detox_agent.webserver.repository;

import com.pnu.detox_agent.webserver.entity.UserEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<UserEntity, Long> {

    Mono<UserEntity> findByUsername(String username);

    Mono<UserEntity> findByEmail(String email);

    Mono<UserEntity> findByDohToken(String dohToken);

    Mono<Boolean> existsByUsername(String username);

    Mono<Boolean> existsByEmail(String email);
}

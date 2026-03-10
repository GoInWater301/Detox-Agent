package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.entity.BlockedDomainEntity;
import com.pnu.detox_agent.webserver.entity.UserEntity;
import com.pnu.detox_agent.webserver.repository.BlockedDomainRepository;
import com.pnu.detox_agent.webserver.repository.UserRepository;
import java.time.Instant;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class BlocklistService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final BlockedDomainRepository blockedDomainRepository;

    public BlocklistService(
            ReactiveStringRedisTemplate redisTemplate,
            UserRepository userRepository,
            BlockedDomainRepository blockedDomainRepository) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.blockedDomainRepository = blockedDomainRepository;
    }

    public Flux<String> getBlockedDomains(String username) {
        return resolveUser(username)
                .flatMapMany(user -> blockedDomainRepository.findAllByUserTokenOrderByDomainAsc(user.getDohToken())
                        .map(BlockedDomainEntity::getDomain)
                        .collectList()
                        .flatMapMany(domains -> syncRedisBlocklist(user.getDohToken(), domains).thenMany(Flux.fromIterable(domains))));
    }

    public Mono<Long> blockDomain(String username, String domain) {
        String normalized = normalizeDomain(domain);
        if (normalized.isBlank()) {
            return Mono.just(0L);
        }
        return resolveUser(username)
                .flatMap(user -> blockedDomainRepository.findByUserTokenAndDomain(user.getDohToken(), normalized)
                        .switchIfEmpty(blockedDomainRepository.save(newBlockedDomain(user.getDohToken(), normalized)))
                        .then(syncRedisBlockEntry(user.getDohToken(), normalized))
                        .thenReturn(1L));
    }

    public Mono<Long> unblockDomain(String username, String domain) {
        String normalized = normalizeDomain(domain);
        if (normalized.isBlank()) {
            return Mono.just(0L);
        }
        return resolveUser(username)
                .flatMap(user -> blockedDomainRepository.deleteByUserTokenAndDomain(user.getDohToken(), normalized)
                        .then(redisTemplate.opsForSet().remove(blockKey(user.getDohToken()), normalized))
                        .thenReturn(1L));
    }

    public Mono<Void> warmupRedisFromDatabase() {
        return blockedDomainRepository.findAll()
                .groupBy(BlockedDomainEntity::getUserToken)
                .flatMap(group -> group.map(BlockedDomainEntity::getDomain)
                        .collectList()
                        .flatMap(domains -> syncRedisBlocklist(group.key(), domains)))
                .then();
    }

    private Mono<UserEntity> resolveUser(String username) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getDohToken() != null && !user.getDohToken().isBlank());
    }

    private String blockKey(String userToken) {
        return "doh:block:" + userToken;
    }

    private Mono<Boolean> syncRedisBlockEntry(String userToken, String domain) {
        return redisTemplate.opsForSet().add(blockKey(userToken), domain)
                .map(added -> added > 0);
    }

    private Mono<Void> syncRedisBlocklist(String userToken, java.util.List<String> domains) {
        String key = blockKey(userToken);
        return redisTemplate.unlink(key)
                .then(Mono.defer(() -> domains.isEmpty()
                        ? Mono.empty()
                        : redisTemplate.opsForSet().add(key, domains.toArray(String[]::new)).then()))
                .then();
    }

    private BlockedDomainEntity newBlockedDomain(String userToken, String domain) {
        BlockedDomainEntity entity = new BlockedDomainEntity();
        entity.setUserToken(userToken);
        entity.setDomain(domain);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }
        String normalized = domain.trim().toLowerCase();
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

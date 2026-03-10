package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.entity.BlockedDomainEntity;
import com.pnu.detox_agent.webserver.entity.UserEntity;
import com.pnu.detox_agent.webserver.repository.BlockedDomainRepository;
import com.pnu.detox_agent.webserver.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
        Set<String> targets = expandStoredDomains(domain);
        if (targets.isEmpty()) {
            return Mono.just(0L);
        }
        return resolveUser(username)
                .flatMap(user -> blockedDomainRepository.findAllByUserTokenAndDomainIn(user.getDohToken(), targets)
                        .map(BlockedDomainEntity::getDomain)
                        .collect(Collectors.toSet())
                        .flatMap(existing -> {
                            List<BlockedDomainEntity> missing = targets.stream()
                                    .filter(domainTarget -> !existing.contains(domainTarget))
                                    .map(domainTarget -> newBlockedDomain(user.getDohToken(), domainTarget))
                                    .toList();
                            return blockedDomainRepository.saveAll(missing)
                                    .then()
                                    .then(resyncUserBlocklist(user))
                                    .thenReturn((long) missing.size());
                        }));
    }

    public Mono<Long> unblockDomain(String username, String domain) {
        Set<String> targets = expandStoredDomains(domain);
        if (targets.isEmpty()) {
            return Mono.just(0L);
        }
        return resolveUser(username)
                .flatMap(user -> blockedDomainRepository.deleteAllByUserTokenAndDomainIn(user.getDohToken(), targets)
                        .then(resyncUserBlocklist(user))
                        .thenReturn((long) targets.size()));
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

    private Mono<Void> resyncUserBlocklist(UserEntity user) {
        return blockedDomainRepository.findAllByUserTokenOrderByDomainAsc(user.getDohToken())
                .map(BlockedDomainEntity::getDomain)
                .collectList()
                .flatMap(domains -> syncRedisBlocklist(user.getDohToken(), domains));
    }

    private BlockedDomainEntity newBlockedDomain(String userToken, String domain) {
        BlockedDomainEntity entity = new BlockedDomainEntity();
        entity.setUserToken(userToken);
        entity.setDomain(domain);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private String normalizeDomain(String domain) {
        String normalized = DomainAggregationPolicy.normalizeHost(domain);
        if (normalized.isBlank()) {
            return normalized;
        }
        String registrable = DomainAggregationPolicy.registrableDomain(normalized);
        return DomainAggregationPolicy.toBlockedServiceDomain(registrable);
    }

    private Set<String> expandStoredDomains(String domain) {
        String normalized = normalizeDomain(domain);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(DomainAggregationPolicy.toBlockTargets(normalized));
    }

    private Mono<Void> syncRedisBlocklist(String userToken, List<String> domains) {
        String key = blockKey(userToken);
        String[] targets = expandDomains(domains).stream()
                .sorted()
                .toArray(String[]::new);

        return redisTemplate.unlink(key)
                .then(Mono.defer(() -> targets.length == 0
                        ? Mono.empty()
                        : redisTemplate.opsForSet().add(key, targets).then()))
                .then();
    }

    private Set<String> expandDomains(List<String> domains) {
        return domains.stream()
                .map(DomainAggregationPolicy::toBlockTargets)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }
}

package com.pnu.detox_agent.webserver.service;

import com.pnu.detox_agent.webserver.entity.DnsUsageEventEntity;
import com.pnu.detox_agent.webserver.entity.UsageSnapshotEntity;
import com.pnu.detox_agent.webserver.repository.DnsUsageEventRepository;
import com.pnu.detox_agent.webserver.repository.UsageSnapshotRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UsagePersistenceService {

    private final DnsUsageEventRepository usageEventRepository;
    private final UsageSnapshotRepository usageSnapshotRepository;

    public UsagePersistenceService(
            DnsUsageEventRepository usageEventRepository,
            UsageSnapshotRepository usageSnapshotRepository) {
        this.usageEventRepository = usageEventRepository;
        this.usageSnapshotRepository = usageSnapshotRepository;
    }

    public Mono<Void> persistEvent(String userId, String domain, long timestampUs, long responseTimeMs) {
        DnsUsageEventEntity entity = new DnsUsageEventEntity();
        entity.setUserId(userId);
        entity.setDomain(domain);
        entity.setQueryTimestampUs(timestampUs);
        entity.setResponseTimeMs(responseTimeMs);
        entity.setReceivedAt(Instant.now());

        return usageEventRepository.save(entity).then();
    }

    public Mono<Void> persistSnapshot(String userId, String period, String bucket, long totalQueries, long uniqueDomains) {
        UsageSnapshotEntity entity = new UsageSnapshotEntity();
        entity.setUserId(userId);
        entity.setPeriod(period);
        entity.setBucket(bucket);
        entity.setTotalQueries(totalQueries);
        entity.setUniqueDomains(uniqueDomains);
        entity.setCreatedAt(Instant.now());

        return usageSnapshotRepository.save(entity).then();
    }
}

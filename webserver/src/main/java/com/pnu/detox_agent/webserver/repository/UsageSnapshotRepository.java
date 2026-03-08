package com.pnu.detox_agent.webserver.repository;

import com.pnu.detox_agent.webserver.entity.UsageSnapshotEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UsageSnapshotRepository extends ReactiveCrudRepository<UsageSnapshotEntity, Long> {
}

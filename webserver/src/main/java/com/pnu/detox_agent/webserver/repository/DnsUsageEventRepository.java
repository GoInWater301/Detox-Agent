package com.pnu.detox_agent.webserver.repository;

import com.pnu.detox_agent.webserver.entity.DnsUsageEventEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface DnsUsageEventRepository extends ReactiveCrudRepository<DnsUsageEventEntity, Long> {
}

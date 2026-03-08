package com.pnu.detox_agent.webserver.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("dns_usage_events")
public class DnsUsageEventEntity {

    @Id
    private Long id;

    @Column("user_id")
    private String userId;

    @Column("domain")
    private String domain;

    @Column("query_timestamp_us")
    private Long queryTimestampUs;

    @Column("response_time_ms")
    private Long responseTimeMs;

    @Column("received_at")
    private Instant receivedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Long getQueryTimestampUs() {
        return queryTimestampUs;
    }

    public void setQueryTimestampUs(Long queryTimestampUs) {
        this.queryTimestampUs = queryTimestampUs;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}

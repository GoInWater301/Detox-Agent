package com.pnu.detox_agent.webserver.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("usage_snapshots")
public class UsageSnapshotEntity {

    @Id
    private Long id;

    @Column("user_id")
    private String userId;

    @Column("period")
    private String period;

    @Column("bucket")
    private String bucket;

    @Column("total_queries")
    private Long totalQueries;

    @Column("unique_domains")
    private Long uniqueDomains;

    @Column("created_at")
    private Instant createdAt;

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

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public Long getTotalQueries() {
        return totalQueries;
    }

    public void setTotalQueries(Long totalQueries) {
        this.totalQueries = totalQueries;
    }

    public Long getUniqueDomains() {
        return uniqueDomains;
    }

    public void setUniqueDomains(Long uniqueDomains) {
        this.uniqueDomains = uniqueDomains;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

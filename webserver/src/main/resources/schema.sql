CREATE TABLE IF NOT EXISTS dns_usage_events (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    query_timestamp_us BIGINT NOT NULL,
    response_time_ms BIGINT NOT NULL,
    received_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dns_usage_events_user_id ON dns_usage_events(user_id);
CREATE INDEX IF NOT EXISTS idx_dns_usage_events_domain ON dns_usage_events(domain);
CREATE INDEX IF NOT EXISTS idx_dns_usage_events_timestamp ON dns_usage_events(query_timestamp_us);

CREATE TABLE IF NOT EXISTS usage_snapshots (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    period VARCHAR(32) NOT NULL,
    bucket VARCHAR(32) NOT NULL,
    total_queries BIGINT NOT NULL,
    unique_domains BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_snapshots_lookup ON usage_snapshots(user_id, period, bucket);

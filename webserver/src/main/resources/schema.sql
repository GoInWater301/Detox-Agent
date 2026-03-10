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

CREATE TABLE IF NOT EXISTS doh_endpoints (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    region VARCHAR(50),
    current_users INT NOT NULL DEFAULT 0,
    max_users INT NOT NULL DEFAULT 1000,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_doh_endpoints_name ON doh_endpoints(name);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    doh_token VARCHAR(255),
    doh_endpoint_id BIGINT REFERENCES doh_endpoints(id),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_doh_token ON users(doh_token);

-- 기본 DoH 엔드포인트 시드 데이터 (없을 때만 삽입)
INSERT INTO doh_endpoints (name, base_url, region, max_users, active)
    SELECT 'default', 'https://doh.detox-agent.local', 'KR', 10000, true
    WHERE NOT EXISTS (SELECT 1 FROM doh_endpoints WHERE name = 'default');

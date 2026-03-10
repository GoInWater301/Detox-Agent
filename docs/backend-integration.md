# Backend Integration Contract

DoH forwarder는 DNS 요청을 직접 저장하지 않습니다. 백엔드는 gRPC analytics 스트림을 수신한 뒤, Redis/DB에 적재하고 대시보드 조회 API를 제공해야 합니다.

---

## 목적

이 문서는 백엔드 팀이 별도 저장소에서 analytics 수집기와 대시보드 백엔드를 구현할 때 기준 계약으로 사용합니다.

- DoH 서버 책임: DNS 질의 처리, 최소 메타데이터 추출, gRPC 스트리밍 전송
- Backend 책임: 이벤트 수집, 세션화, Redis 집계, 장기 저장, 대시보드 API 제공

---

## 수집 진입점

DoH 서버는 `AnalyticsService/StreamQueries` 로 client-side streaming RPC를 엽니다.

Proto 정의:

- 파일: `protos/analytics.proto`
- 서비스: `analytics.AnalyticsService`
- RPC: `rpc StreamQueries(stream DnsQueryEvent) returns (Ack);`

전송 대상은 `DOH_ANALYTICS_EP` 환경변수로 설정합니다.

예시:

```bash
DOH_ANALYTICS_EP=analytics.internal:50051
```

---

## 현재 이벤트 스키마

`DnsQueryEvent` 필드:

- `user_id`
- `raw_query`
- `raw_response`
- `client_ip`
- `timestamp_us`
- `latency_us`
- `used_tcp`
- `queried_domain`

의미:

- `user_id`: `/{user_id}/dns-query` 경로에서 추출
- `timestamp_us`: DoH 서버가 요청을 수신한 시점의 Unix epoch microseconds
- `latency_us`: 필터 확인 이후 업스트림 DNS 조회 완료까지의 종단간 지연
- `queried_domain`: DNS question section 첫 번째 QNAME
- `used_tcp`: 현재 구현에서는 UDP 성공 케이스만 `false` 로 기록됨. TCP fallback 구분이 필요하면 추후 스키마/구현 확장이 필요함

주의:

- 현재 구현은 "허용된 요청"만 analytics로 전송합니다
- Redis 차단으로 합성된 NXDOMAIN 응답은 analytics 스트림에 실리지 않습니다
- 따라서 백엔드는 현재 스키마만으로 "차단 이벤트"나 "실패 원인"을 완전하게 알 수 없습니다

---

## 백엔드 권장 구조

### 1. Ingestor

gRPC 스트림 수신기. 책임:

- `DnsQueryEvent` 수신
- 필드 검증
- 세션 계산용 정규화
- Redis 실시간 집계 반영
- 장기 저장소(PostgreSQL 권장) 비동기 적재

### 2. Aggregator

도메인별/사용자별 usage duration 계산. 현재 백엔드 구현 규칙:

- 사용자별 마지막 DNS 이벤트를 Redis에 캐시: `usage:last-query:{user_id}`
- 캐시 필드: `domain`, `timestampUs`
- 새 `DnsQueryEvent` 수신 시 이전 캐시와 비교
- 이전 이벤트와 현재 이벤트 간격이 `180초` 이하이면 같은 세션으로 간주
- 간격이 `180초`를 초과하면 새 세션으로 간주
- 사용시간은 "이전 DNS가 현재 DNS가 들어올 때까지 유지됐다"고 가정하고 이전 도메인에 누적
- 누적 단위는 분이며, `max(1, gap_us / 60_000_000)` 규칙으로 최소 1분을 부여

예시:

- `10:00` `youtube.com`
- `10:02` `youtube.com` -> `youtube.com` 에 `2분` 누적
- `10:04` `googlevideo.com` -> 직전 `youtube.com` 에 다시 `2분` 누적
- `10:10` `naver.com` -> 직전 이벤트와 3분 초과이므로 새 세션 시작, 추가 누적 없음

DNS만으로 실제 체류 시간을 정확히 측정할 수는 없으므로, 대시보드에는 "estimated usage time (minutes)" 로 표기하는 것이 안전합니다.

### 3. Query API

대시보드용 조회 API. 권장 예시:

- `GET /users/{userId}/domains?date=2026-03-10`
- `GET /users/{userId}/timeline?from=...&to=...`
- `GET /domains/ranking?date=2026-03-10`
- `GET /users/{userId}/blocked?date=2026-03-10`

---

## Redis 권장 키 설계

Redis는 실시간 조회와 최근 집계를 위한 캐시로 사용합니다.

### 사용자별 마지막 DNS 캐시

```text
HASH usage:last-query:{user_id}
field = domain
field = timestampUs
```

### 일별 도메인 사용량

```text
HASH usage:{user_id}:{domain}:{yyyy-MM-dd}
fields = userId, domain, firstAccess, lastAccess, count, totalDuration, responseTimeSum, avgResponseTimeMs
```

- `totalDuration` 은 분 단위 누적값
- `count` 는 수신된 DNS query 개수

### 사용자별 일자 인덱스

```text
SET usage:index:user:{user_id}:{yyyy-MM-dd}
member = domain
```

### 사용자별 전체 인덱스

```text
SET usage:index:user:{user_id}
member = domain
```

### 기간별 실시간 카운터

```text
HASH stats:{daily|weekly|monthly}:{user_id}:{bucket}
field = totalQueries
field = domain:{queried_domain}
```

### 최근 이벤트 피드

```text
STREAM dns:events
fields = user_id, queried_domain, client_ip, timestamp_us, latency_us
```

### 사용자별 최근 이벤트

```text
STREAM dns:user:{user_id}:events
fields = queried_domain, timestamp_us, latency_us
```

### 차단 통계

이 forwarder는 현재 차단 이벤트를 analytics로 보내지 않으므로, 아래 키는 백엔드가 아니라 차단 로깅 기능이 추가된 이후에만 사용 가능합니다.

```text
HASH blocked:domain:2026-03-10:{user_id}
field = queried_domain
value = hit_count
```

---

## 장기 저장소 권장

Redis만으로는 장기 통계, 조건 검색, 재집계가 불편합니다. PostgreSQL 같은 영속 저장소를 함께 두는 구성을 권장합니다.

권장 테이블:

### `dns_query_event`

- `id`
- `user_id`
- `queried_domain`
- `client_ip`
- `timestamp_us`
- `latency_us`
- `used_tcp`
- `raw_query`
- `raw_response`

### `domain_usage_daily`

- `usage_date`
- `user_id`
- `queried_domain`
- `estimated_duration_minutes`
- `query_count`

### `user_usage_daily`

- `usage_date`
- `user_id`
- `estimated_duration_minutes`
- `unique_domain_count`

### `usage_snapshots`

- `user_id`
- `period`
- `bucket`
- `total_queries`
- `unique_domains`
- `created_at`

운영 권장 사항:

- `(user_id, period, bucket)` 유니크 키를 둬서 자정 배치가 idempotent 하게 동작하도록 구성
- `daily` 는 자정 직후 `00:05 UTC` 에 `전날` 버킷을 확정 저장
- `weekly`, `monthly` 는 가능하면 raw Redis 대신 `daily snapshot` 합산으로 생성

---

## 대시보드 구현 시 주의점

- DNS 질의 빈도는 실제 화면 체류시간과 동일하지 않음
- CDN/광고/서드파티 도메인이 노이즈를 만들 수 있음
- 루트 도메인 정규화(eTLD+1) 여부를 백엔드에서 결정해야 함
- TTL 강제 조정이 사용시간 추정치에 영향을 줌

대시보드에는 다음 구분을 두는 것이 좋습니다.

- exact query count
- estimated usage duration (minutes)
- blocked query count
- unique domain count

---

## 현재 Forwarder 한계

백엔드 팀이 구현 전에 알고 있어야 할 현재 한계:

1. 차단 이벤트는 analytics 스트림에 전송되지 않음
2. TCP fallback 여부가 정확히 반영되지 않음
3. 응답 TTL / DNS RCODE / HTTP 상태 같은 분석 핵심 필드가 스키마에 없음
4. 인증은 현재 `grpc::InsecureChannelCredentials()` 사용

백엔드 구현 전에 분석 정확도가 중요하다면, 이후 forwarder 쪽에서 proto 확장을 진행하는 것이 좋습니다.

---

## 권장 다음 단계

1. 백엔드 저장소에서 `analytics.proto` 기반 gRPC 수신 서버 생성
2. Redis 키 설계 확정
3. 자정 배치에서 `usage_snapshots` daily 확정 집계 구현
4. 장기 저장소 스키마에 `(user_id, period, bucket)` uniqueness 반영
5. 이후 필요 시 forwarder proto를 확장해 blocked/rcode/effective_ttl 등을 추가

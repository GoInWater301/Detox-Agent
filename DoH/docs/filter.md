# 도메인 필터

사용자별 / 전역 도메인 차단 기능입니다.
Redis SET을 사용하며, 차단된 도메인에는 합성 **NXDOMAIN** 또는 **REFUSED** DNS 응답을 반환합니다.

---

## 활성화

```bash
DOH_FILTER_ENABLED=true
DOH_REDIS_HOST=127.0.0.1
DOH_REDIS_PORT=6379
DOH_REDIS_PASSWORD=            # 없으면 생략
DOH_REDIS_TIMEOUT_MS=5000
DOH_REDIS_REFRESH_MS=1000
DOH_FILTER_FAIL_OPEN=true      # Redis 장애 시 true=허용, false=차단
DOH_BLOCK_RESPONSE=NXDOMAIN   # 또는 REFUSED
```

---

## Redis 데이터 모델

```
SET  doh:block:global        → 전역 차단 목록 (모든 사용자 적용)
SET  doh:block:{user_id}     → 사용자별 차단 목록
```

### Suffix 매칭 규칙

`"example.com"` 을 SET에 추가하면:

| 조회 도메인 | 차단 여부 |
|-------------|----------|
| `example.com` | ✅ |
| `ads.example.com` | ✅ |
| `api.tracker.example.com` | ✅ |
| `notexample.com` | ❌ |
| `myexample.com` | ❌ |

하나의 항목이 모든 하위 도메인을 재귀적으로 차단합니다.

---

## 차단 목록 관리

### 전역 차단 (모든 사용자)

```bash
# 도메인 추가
redis-cli SADD doh:block:global example.com
redis-cli SADD doh:block:global ads.doubleclick.net malware-site.ru

# 도메인 제거
redis-cli SREM doh:block:global example.com

# 차단 목록 조회
redis-cli SMEMBERS doh:block:global
```

### 사용자별 차단

```bash
# alice 사용자의 차단 목록에 추가
redis-cli SADD doh:block:alice social-media.com gaming-site.net

# alice 사용자의 차단 목록 조회
redis-cli SMEMBERS doh:block:alice

# alice의 차단 목록 전체 삭제
redis-cli DEL doh:block:alice
```

### 파일에서 일괄 추가

```bash
# blocklist.txt 형식: 한 줄에 하나의 도메인
cat blocklist.txt | xargs redis-cli SADD doh:block:global
```

### 즉시 확인 (Lua 스크립트와 동일한 로직)

```bash
# user_id=alice, domain=ads.example.com 조회 시뮬레이션
redis-cli SISMEMBER doh:block:global ads.example.com
redis-cli SISMEMBER doh:block:global example.com       # suffix 매칭
redis-cli SISMEMBER doh:block:alice  ads.example.com
redis-cli SISMEMBER doh:block:alice  example.com       # suffix 매칭
```

---

## 차단 동작

차단된 도메인에 대해 서버는 설정값에 따라 **합성 NXDOMAIN** 또는 **REFUSED** DNS 응답을 반환합니다.
업스트림 DNS 서버에는 쿼리가 전달되지 않습니다.

```
Client                          DoH Server           Redis
  │── POST /alice/dns-query ──►│                       │
  │                             │── EVAL script ──────►│
  │                             │◄── 1 (blocked) ──────│
  │                             │  (upstream DNS never contacted)
  │◄── 200 OK (synthetic block response) ──────│
```

### 차단 응답 구조

```
DNS Header:
  ID:      (원본 쿼리 ID 복사)
  Flags:   QR=1, AA=1, RCODE=3 (NXDOMAIN) 또는 5 (REFUSED)
  QDCOUNT: 1
  ANCOUNT: 0
  NSCOUNT: 0
  ARCOUNT: 0
Question:  (원본 쿼리 Question Section 복사)
```

클라이언트(브라우저, OS 리졸버)는 이를 "도메인이 존재하지 않음" 또는
"정책상 질의 거부"로 해석합니다.

---

## 내부 동작 (Lua 스크립트)

Redis 서버는 메모리 캐시 동기화 소스로만 사용합니다. 서버는 단 하나의 EVAL 호출로
전체 스냅샷을 가져와 로컬 캐시를 갱신하고, 요청 경로에서는 메모리에서만 suffix 매칭합니다.

```lua
local global_key = "doh:block:global"
local user_key   = "doh:block:" .. ARGV[1]  -- user_id
local d          = ARGV[2]                  -- queried domain

-- 가장 구체적인 레이블부터 TLD까지 순서대로 검사
while #d > 0 do
    if redis.call("SISMEMBER", global_key, d) == 1 then return 1 end
    if redis.call("SISMEMBER", user_key,   d) == 1 then return 1 end
    local dot = string.find(d, ".", 1, true)
    if not dot then break end
    d = string.sub(d, dot + 1)  -- 한 레이블 제거
end
return 0
```

`ads.tracker.example.com` 조회 시 검사 순서:

```
1. ads.tracker.example.com   (exact)
2. tracker.example.com       (parent)
3. example.com               (grandparent)
4. com                       (TLD — 보통 차단하지 않음)
```

---

## 장애 처리

Redis가 다운되거나 응답이 없으면:

- `DOH_FILTER_FAIL_OPEN=true` 이면 필터 검사를 건너뛰고 **쿼리를 허용합니다**
- `DOH_FILTER_FAIL_OPEN=false` 이면 안전하게 **쿼리를 차단합니다**
- timeout / Redis 오류는 `warn` 레벨 로그를 기록합니다

현재 구현은 Redis 스냅샷 동기화에 timeout을 두며 기본값은 `5000ms` 입니다. 필요하면 `DOH_REDIS_TIMEOUT_MS`로 조정할 수 있습니다.
추가/삭제 반영 지연은 `DOH_REDIS_REFRESH_MS` 기본값 `1000ms` 이내입니다.

운영에서 우회보다 차단 일관성이 중요하면 `DOH_FILTER_FAIL_OPEN=false` 를 권장합니다.

---

## 성능

| 항목 | 값 |
|------|-----|
| 요청 경로 네트워크 왕복 | 0회 (메모리 조회만 수행) |
| 도메인당 필터 연산 | O(depth) 해시셋 조회 (최대 ~6) |
| Redis 연결 | 백그라운드 스냅샷 동기화용 영구 연결 |
| 추가/삭제 반영 지연 | 기본 1초 (`DOH_REDIS_REFRESH_MS`) |

---

## 권장 차단 목록 소스

| 소스 | URL | 용도 |
|------|-----|------|
| Pi-hole 기본 목록 | https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts | 광고/트래커 |
| OISD | https://oisd.nl | 종합 |
| Hagezi | https://github.com/hagezi/dns-blocklists | 멀웨어/피싱 |

파일을 파싱해 Redis에 일괄 등록하는 스크립트 예시:

```bash
#!/bin/bash
# hosts 형식 파일에서 도메인만 추출해 doh:block:global 에 추가
curl -s "https://example.com/blocklist.hosts" \
  | grep '^0\.0\.0\.0 ' \
  | awk '{print $2}' \
  | grep -v '^#' \
  | xargs -L 1000 redis-cli SADD doh:block:global
```

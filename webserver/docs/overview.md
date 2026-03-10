# Detox-Agent WebServer: 프로젝트 개요

## 목차
1. [프로젝트 소개](#1-프로젝트-소개)
2. [전체 시스템 아키텍처](#2-전체-시스템-아키텍처)
3. [WebServer 역할과 책임](#3-webserver-역할과-책임)
4. [모듈 구조](#4-모듈-구조)
5. [핵심 기능 상세](#5-핵심-기능-상세)
6. [데이터 모델](#6-데이터-모델)
7. [보안 및 인증](#7-보안-및-인증)
8. [기술 스택](#8-기술-스택)
9. [서비스 간 통신](#9-서비스-간-통신)
10. [환경 설정](#10-환경-설정)

---

## 1. 프로젝트 소개

**Detox-Agent**는 디지털 중독 문제를 해결하기 위해 설계된 네트워크 레벨 DNS 필터링 및 AI 기반 분석 시스템입니다.

사용자가 인터넷을 사용할 때 발생하는 모든 DNS 쿼리를 개인 전용 DoH(DNS-over-HTTPS) 엔드포인트로 라우팅합니다. 이 과정에서 수집된 도메인 접속 이력을 실시간으로 분석하고, AI가 사용 패턴을 진단하여 맞춤형 디지털 해독 리포트를 제공합니다.

### 핵심 가치

| 문제 | 해결 방식 |
|---|---|
| 사용자가 자신의 인터넷 습관을 인지하지 못함 | DNS 레벨에서 모든 쿼리를 투명하게 수집 및 시각화 |
| 단순 차단은 실효성이 낮음 | AI가 패턴을 분석하고 맥락 있는 피드백 제공 |
| 중앙화된 DNS 서버는 프라이버시 우려 | 사용자별 전용 DoH 토큰으로 개인화 및 격리 |

---

## 2. 전체 시스템 아키텍처

본 시스템은 3개의 독립적인 백엔드 서비스와 프론트엔드로 구성된 마이크로서비스 아키텍처(MSA)입니다. 서비스 간 통신은 gRPC를 사용하여 고성능을 달성합니다.

```
┌─────────────────────────────────────────────────────────────────┐
│                         사용자 기기                               │
│                    (DNS 클라이언트 설정)                           │
└─────────────────────────┬───────────────────────────────────────┘
                          │ DoH 쿼리 (HTTPS/HTTP2)
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DoH Server (C++)                              │
│              Boost.Beast + Asio + OpenSSL                       │
│   - 사용자 토큰 기반 DNS 쿼리 수신 및 포워딩                         │
│   - 유해 도메인 필터링                                              │
│   - 쿼리 이벤트 실시간 스트리밍 ──────────────────────────────────► │
└──────────────────────────────────────────┬──────────────────────┘
                                           │ gRPC Client Streaming
                                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   WebServer (Java / Spring Boot)                 │
│                        [이 서비스]                                │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ Auth API     │  │ Dashboard API│  │ AI Review SSE API      │ │
│  │ /api/auth/** │  │/api/dashboard│  │ /api/ai/review/stream  │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
│                                                                  │
│  ┌──────────────────────────┐   ┌──────────────────────────────┐│
│  │     Redis (실시간 캐시)    │   │   PostgreSQL (영구 저장소)     ││
│  │  - 도메인별 사용량 해시    │   │  - dns_usage_events          ││
│  │  - 시간대별 통계 집계      │   │  - usage_snapshots           ││
│  └──────────────────────────┘   │  - users                     ││
│                                  │  - doh_endpoints             ││
│                                  └──────────────────────────────┘│
└──────────┬──────────────────────────────────────┬────────────────┘
           │ gRPC (UsageData)                      │ REST (SSE)
           ▼                                       │
┌──────────────────────────┐                       │
│    AI Agent (Python)     │                       │
│  PydanticAI + FastAPI    │                       │
│  - 도메인 패턴 AI 분석    │                       │
│  - 토큰 스트리밍 응답     │                       │
└──────────────────────────┘                       │
                                                   ▼
                                    ┌──────────────────────────────┐
                                    │   Frontend (React / Vite)    │
                                    │  - 실시간 통계 대시보드        │
                                    │  - AI 리뷰 스트리밍 UI        │
                                    └──────────────────────────────┘
```

### 서비스 포트 요약

| 서비스 | 프로토콜 | 포트 | 용도 |
|---|---|---|---|
| DoH Server | HTTPS | 443 / 8443 | DNS-over-HTTPS 수신 |
| WebServer (REST) | HTTP | 8080 | 대시보드 API, 인증 |
| WebServer (gRPC) | HTTP/2 | 9090 | DNS 이벤트 수신, 데이터 서빙 |
| AI Agent (REST) | HTTP | 8000 | AI 분석 요청 |
| AI Agent (gRPC) | HTTP/2 | 50051 | 토큰 스트리밍 |
| PostgreSQL | TCP | 5432 | 영구 저장 |
| Redis | TCP | 6379 | 실시간 캐시 |

---

## 3. WebServer 역할과 책임

WebServer는 시스템의 **중앙 허브**입니다. 모든 데이터가 WebServer를 통해 수집, 저장, 조회됩니다.

```
역할:
  1. 사용자 등록/로그인 및 JWT 발급
  2. DoH 엔드포인트 배정 (로드 밸런싱)
  3. DoH Server로부터 DNS 쿼리 이벤트 실시간 수집 (gRPC)
  4. Redis에 사용자별 도메인 사용량 실시간 집계
  5. PostgreSQL에 이벤트 및 스냅샷 영구 저장
  6. Dashboard REST API 제공
  7. AI Agent에 사용량 데이터 제공 (gRPC)
  8. AI 분석 결과 SSE로 프론트엔드에 릴레이
```

---

## 4. 모듈 구조

```
webserver/src/main/java/com/pnu/detox_agent/webserver/
│
├── config/
│   ├── SecurityConfig.java       # Spring Security + JWT 필터 체인
│   ├── SchedulingConfig.java     # 스케줄링 활성화
│   └── OpenApiConfig.java        # Swagger/OpenAPI 설정
│
├── controller/
│   ├── AuthController.java       # POST /api/auth/register, /login
│   ├── DashboardController.java  # GET /api/dashboard/users/{id}/...
│   └── AiReviewController.java   # POST /api/ai/review/stream (SSE)
│
├── service/
│   ├── AuthService.java          # 회원가입, 로그인 비즈니스 로직
│   ├── DoHAddressService.java    # DoH 엔드포인트 로드 밸런싱 및 URL 생성
│   ├── DashboardService.java     # 통계 조회 서비스
│   ├── UsageTrackingService.java # Redis 실시간 사용량 추적
│   ├── UsagePersistenceService.java # PostgreSQL 영구 저장
│   └── ai/
│       ├── AiAgentGrpcClient.java    # AI Agent gRPC 클라이언트
│       └── AiReviewTunnelService.java # gRPC → SSE 변환 터널
│
├── grpc/
│   └── DnsAnalyticsGrpcHandler.java  # gRPC 서버: DNS 이벤트 수신
│
├── security/
│   ├── JwtService.java           # JWT 생성 / 파싱 / 검증
│   └── JwtAuthFilter.java        # WebFlux JWT 인증 필터
│
├── entity/
│   ├── UserEntity.java           # users 테이블 매핑
│   ├── DoHEndpointEntity.java    # doh_endpoints 테이블 매핑
│   ├── DnsUsageEventEntity.java  # dns_usage_events 테이블 매핑
│   └── UsageSnapshotEntity.java  # usage_snapshots 테이블 매핑
│
├── repository/
│   ├── UserRepository.java
│   ├── DoHEndpointRepository.java
│   ├── DnsUsageEventRepository.java
│   └── UsageSnapshotRepository.java
│
├── dto/
│   ├── auth/
│   │   ├── RegisterRequestDto.java
│   │   ├── LoginRequestDto.java
│   │   └── AuthResponseDto.java
│   ├── ai/
│   │   ├── AiReviewRequestDto.java
│   │   └── AiReviewSseEventDto.java
│   ├── DomainUsageDto.java
│   ├── UsageStatsDto.java
│   └── TimelineStatsDto.java
│
└── analytics/
    └── StatisticsAggregator.java  # 스케줄링 기반 통계 집계
```

---

## 5. 핵심 기능 상세

### 5.1 사용자 인증 및 DoH 배정

사용자가 회원가입 시 시스템은 세 가지 작업을 원자적으로 수행합니다.

```
[회원가입 흐름]

클라이언트
  │
  ├─► POST /api/auth/register { username, email, password }
  │
WebServer
  ├─ 1. username/email 중복 검사 (DB)
  ├─ 2. 가장 여유 있는 DoH 엔드포인트 선택 (로드 밸런싱)
  ├─ 3. 사용자 생성 (password BCrypt 해싱)
  ├─ 4. 32자 랜덤 dohToken 발급
  ├─ 5. 엔드포인트 사용자 수 증가 (원자적 UPDATE)
  └─ 6. JWT + 개인 DoH URL 반환

응답:
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "username": "user123",
  "dohUrl": "https://doh.detox-agent.local/{dohToken}/dns-query"
}
```

발급된 `dohUrl`을 기기의 DNS 설정에 등록하면, 이후 모든 DNS 쿼리가 이 사용자 전용 엔드포인트를 통해 라우팅됩니다.

---

### 5.2 DNS 쿼리 이벤트 수집 (gRPC Streaming)

DoH Server는 DNS 쿼리를 처리할 때마다 WebServer의 gRPC 서버로 이벤트를 스트리밍합니다.

```
[DNS 이벤트 수집 흐름]

DoH Server (C++)
  │
  └─► gRPC Client Streaming: AnalyticsService/StreamQueries
      DnsQueryEvent {
        user_id: "user123",
        queried_domain: "youtube.com",
        timestamp_us: 1741400000000000,
        latency_us: 8500
      }
      ...반복...
      onCompleted()
  │
WebServer (DnsAnalyticsGrpcHandler)
  ├─ userId / domain 유효성 검사
  ├─ timestamp 정규화 (누락 시 현재 시각 사용)
  ├─ UsageTrackingService.trackUsage() → Redis 업데이트
  └─► Ack { accepted_count: N }
```

---

### 5.3 실시간 사용량 추적 (Redis)

수신된 각 DNS 이벤트는 Redis에 사용자-도메인 단위로 집계됩니다.

**Redis 키 구조:**

```
# 도메인별 사용량 해시 (실시간)
usage:{userId}:{domain}
  ├─ firstAccess    : 최초 접근 시각 (µs)
  ├─ lastAccess     : 최근 접근 시각 (µs)
  ├─ count          : 총 쿼리 횟수
  └─ totalDuration  : 누적 사용 시간 (µs)

# 사용자별 도메인 인덱스 (Sorted Set)
usage:index:user:{userId}
  → 도메인 목록 (score: lastAccess)

# 시간대별 집계 통계 (1분 스케줄로 갱신)
stats:daily:{userId}:{yyyy-MM-dd}
stats:weekly:{userId}:{yyyy-Www}
stats:monthly:{userId}:{yyyy-MM}
  ├─ totalQueries   : 총 쿼리 수
  └─ uniqueDomains  : 고유 도메인 수
```

---

### 5.4 통계 집계 및 영구 저장 (PostgreSQL)

`StatisticsAggregator`가 60초마다 Redis 데이터를 PostgreSQL의 `usage_snapshots`에 스냅샷으로 저장합니다. 원본 이벤트는 `dns_usage_events`에 별도 보관됩니다.

---

### 5.5 AI 분석 스트리밍 (SSE Tunnel)

프론트엔드가 AI 분석을 요청하면 WebServer는 AI Agent에 gRPC로 요청하고, 응답 토큰을 SSE(Server-Sent Events)로 브라우저에 실시간 스트리밍합니다.

```
[AI 리뷰 흐름]

Frontend
  │
  └─► POST /api/ai/review/stream  (Accept: text/event-stream)
      { sessionId, prompt, usage: { userId, period, ... } }
  │
WebServer (AiReviewTunnelService)
  │
  └─► gRPC: AiAgentReviewService/StreamReview
  │
AI Agent (Python / PydanticAI)
  └─► gRPC 스트리밍 응답: ReviewToken { token: "..." }
  │
WebServer → SSE 변환
  │
  └─► event: token  data: { type: "token", token: "..." }
      event: done   data: { type: "done" }

또는 오류 시:
      event: error  data: { type: "error", message: "..." }
```

---

## 6. 데이터 모델

### PostgreSQL 스키마

```sql
-- 사용자 테이블
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL DEFAULT 'USER',
    doh_token       VARCHAR(255) UNIQUE,           -- 개인 DoH 인증 토큰
    doh_endpoint_id BIGINT REFERENCES doh_endpoints(id),
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- DoH 서버 엔드포인트 풀
CREATE TABLE doh_endpoints (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    base_url      VARCHAR(255) NOT NULL,
    region        VARCHAR(50),
    current_users INT          NOT NULL DEFAULT 0,
    max_users     INT          NOT NULL DEFAULT 1000,
    active        BOOLEAN      NOT NULL DEFAULT true
);

-- 원시 DNS 이벤트 로그
CREATE TABLE dns_usage_events (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             VARCHAR(255) NOT NULL,
    domain              VARCHAR(255) NOT NULL,
    query_timestamp_us  BIGINT       NOT NULL,
    response_time_ms    BIGINT       NOT NULL,
    received_at         TIMESTAMP    NOT NULL
);

-- 시간대별 집계 스냅샷
CREATE TABLE usage_snapshots (
    id             BIGSERIAL PRIMARY KEY,
    user_id        VARCHAR(255) NOT NULL,
    period         VARCHAR(32)  NOT NULL,   -- daily | weekly | monthly
    bucket         VARCHAR(32)  NOT NULL,   -- 예: 2026-03-10
    total_queries  BIGINT       NOT NULL,
    unique_domains BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL
);
```

---

## 7. 보안 및 인증

### 7.1 JWT 기반 Stateless 인증

WebServer는 세션을 사용하지 않는 완전한 Stateless 구조입니다.

```
인증 흐름:
  1. 클라이언트 → POST /api/auth/login → JWT 발급
  2. 이후 모든 요청: Authorization: Bearer {JWT}
  3. JwtAuthFilter가 토큰 검증 후 SecurityContext에 사용자 설정

JWT 페이로드:
  - subject: username
  - issuedAt: 발급 시각
  - expiration: 발급 시각 + 24시간 (설정 가능)
  - 서명: HMAC-SHA256 (32바이트 이상 시크릿 키)
```

### 7.2 접근 제어 규칙

```
허용 (인증 불필요):
  - POST /api/auth/register
  - POST /api/auth/login
  - GET  /v3/api-docs/**
  - GET  /swagger-ui/**

인증 필요:
  - GET  /api/dashboard/**
  - POST /api/ai/review/stream
  - 그 외 모든 엔드포인트
```

### 7.3 비밀번호 보안

- BCrypt 해싱 (Spring Security 기본 강도: 10라운드)
- 평문 비밀번호는 절대 저장되지 않음

### 7.4 DoH 토큰

- 사용자 등록 시 32자 UUID 기반 랜덤 토큰 발급
- DoH Server는 이 토큰으로 쿼리를 사용자와 연결
- 토큰은 DB 고유 인덱스로 보호

---

## 8. 기술 스택

| 분류 | 기술 | 버전 | 역할 |
|---|---|---|---|
| 런타임 | Java | 21 | 언어 |
| 프레임워크 | Spring Boot | 4.0.x | 애플리케이션 프레임워크 |
| 반응형 | Spring WebFlux | 4.0.x | 비동기 논블로킹 HTTP 서버 |
| 인증 | Spring Security | 4.0.x | 보안 필터 체인 |
| JWT | jjwt | 0.12.6 | 토큰 생성 및 검증 |
| gRPC 서버 | Spring gRPC Server | 1.0.2 | DNS 이벤트 수신 |
| gRPC 클라이언트 | Spring gRPC Client | 1.0.2 | AI Agent 호출 |
| DB 드라이버 | R2DBC PostgreSQL | - | 비동기 DB 접근 |
| 캐시 | Spring Data Redis Reactive | - | 비동기 Redis 접근 |
| 빌드 | Gradle | 8.x | 빌드 도구 |
| 직렬화 | Protocol Buffers | 3.x | gRPC 메시지 포맷 |
| API 문서 | springdoc-openapi | 3.0.2 | Swagger UI |
| 유효성 검사 | Spring Validation (Jakarta) | - | DTO 검증 |
| 유틸리티 | Lombok | - | 보일러플레이트 제거 |

### 반응형 아키텍처 원칙

모든 I/O 작업은 Project Reactor의 `Mono`/`Flux`를 통한 논블로킹 방식으로 처리됩니다. 스레드 블로킹이 발생하지 않으므로 적은 리소스로 높은 동시 접속을 처리할 수 있습니다.

```
HTTP 요청 → WebFlux → Mono/Flux 체인
  ├─ R2DBC (PostgreSQL 비동기)
  ├─ ReactiveRedisTemplate (Redis 비동기)
  └─ gRPC StreamObserver (비동기 스트림)
```

---

## 9. 서비스 간 통신

### gRPC Protobuf 계약

**DNS 분석 서비스** (`dns_analytics.proto`):
```protobuf
service AnalyticsService {
  rpc StreamQueries(stream DnsQueryEvent) returns (Ack);
}

message DnsQueryEvent {
  string user_id       = 1;
  string queried_domain = 2;
  int64  timestamp_us  = 3;
  int64  latency_us    = 4;
}

message Ack {
  int64 accepted_count = 1;
}
```

**AI 에이전트 서비스** (`ai_agent.proto`):
```protobuf
service AiAgentReviewService {
  rpc StreamReview(ReviewRequest) returns (stream ReviewToken);
}
```

---

## 10. 환경 설정

### application.properties 주요 설정

```properties
# 서버
server.port=8080

# PostgreSQL (R2DBC)
spring.r2dbc.url=r2dbc:postgresql://localhost:5432/detox_analytics
spring.r2dbc.username=detox
spring.r2dbc.password=detox

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# gRPC 서버 (DNS 이벤트 수신)
spring.grpc.server.port=9090

# AI Agent gRPC 클라이언트
ai.agent.grpc.host=localhost
ai.agent.grpc.port=50051

# JWT (시크릿 키는 32바이트 이상 필수)
jwt.secret=<최소-32바이트-시크릿>
jwt.expiration=86400000    # 24시간 (ms)

# DoH 폴백 URL (DB에 엔드포인트가 없을 경우)
doh.fallback-base-url=https://doh.detox-agent.local
```

### 로컬 개발 환경 시작

```bash
# 인프라 (PostgreSQL + Redis) 실행
docker compose up -d postgres redis

# WebServer 실행
cd webserver
./gradlew bootRun

# Swagger UI 확인
open http://localhost:8080/swagger-ui.html
```

### Docker를 이용한 전체 스택 실행

```bash
# 프로젝트 루트에서
docker compose up -d --build
```

---

*최종 수정: 2026-03-10*

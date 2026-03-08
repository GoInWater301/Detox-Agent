# 아키텍처

## 전체 구조

```
Client (HTTPS/DoH)
        │
        │ TLS 1.2+
        ▼
┌─────────────────────────────────────────────────────┐
│                  doh-beast-forwarder                │
│                                                     │
│  server/Listener                                    │
│    └─ 각 연결마다 session/HttpsSession 스폰          │
│         │                                           │
│         ├─ 경로 파싱 (util/path_parser)             │
│         ├─ 페이로드 추출 (util/base64url)            │
│         │                                           │
│         ├─ dns/DnsForwarder ──────────────────────► DNS Upstream
│         │      ├─ UdpUpstream (1차 시도)            │  (8.8.8.8:53)
│         │      └─ TcpUpstream (TC=1 fallback)       │
│         │                                           │
│         └─ analytics/GrpcStreamClient ────────────► Analytics Server
│                (fire-and-forget, 비동기 큐)         │  (gRPC :50051)
└─────────────────────────────────────────────────────┘
```

---

## 요청 처리 흐름

```
1. TCP Accept          Listener::do_accept()
                         └─ net::make_strand(ioc) 로 소켓 생성
                              → HttpsSession 스폰

2. TLS Handshake       HttpsSession::do_handshake()
                         └─ stream_.async_handshake(server)

3. HTTP Read           HttpsSession::do_read()
                         └─ http::async_read(..., *parser_)
                              body_limit = 65535 bytes

4. 경로 검증           util::parse_user_id(req_.target())
                         ├─ 유효: /{user_id}/dns-query  → 계속
                         └─ 무효: 403 Forbidden 즉시 반환

5. 페이로드 추출
   ├─ GET:  ?dns=<base64url>  → util::base64url_decode()
   └─ POST: body (raw binary)

6. DNS 전달            DnsForwarder::async_forward()
                         ├─ UdpUpstream::async_query()   (timeout: dns_timeout_ms)
                         │    └─ TC=1? → TcpUpstream::async_query() (timeout: ×2)
                         └─ callback with raw DNS response

7. 분석 전송           GrpcStreamClient::send()  [non-blocking, enqueue]
                         └─ 별도 jthread에서 gRPC Write()

8. HTTP 응답           http::async_write()
                         Content-Type: application/dns-message
                         └─ keep-alive? → goto 3
                            close?      → SSL shutdown
```

---

## 스레딩 모델

```
main()
  ├─ io_context  (concurrency_hint = thread_count)
  │    │
  │    ├─ std::jthread ×(N-1)  ──► ioc.run()
  │    └─ main thread           ──► ioc.run()
  │
  │  각 HttpsSession은 자신의 strand 에서만 실행됨
  │  (Listener가 net::make_strand(ioc) 로 소켓 생성)
  │
  └─ GrpcStreamClient::worker_
       └─ std::jthread  (gRPC 전용 — io_context 외부)
```

### 스트랜드 보장
- `Listener::acceptor_` → `net::make_strand(ioc)` 스트랜드
- 각 `HttpsSession::stream_` → 생성 시 부여된 개별 스트랜드
- `HttpsSession::run()` 은 `net::dispatch()` 로 자신의 스트랜드 진입 보장
- `DnsForwarder::async_forward()` 는 세션의 executor 를 그대로 전달 → 콜백도 동일 스트랜드

---

## 모듈 의존 그래프

```
main.cpp
  ├── config/config
  ├── server/tls_context
  ├── server/listener
  └── analytics/grpc_stream_client

server/listener
  └── session/https_session

session/https_session
  ├── config/config
  ├── dns/dns_forwarder
  ├── analytics/analytics_client  (interface)
  ├── util/path_parser
  └── util/base64url

dns/dns_forwarder
  ├── dns/udp_upstream
  ├── dns/tcp_upstream
  └── util/dns_wire

analytics/grpc_stream_client
  └── analytics/analytics_client  (interface)
      └── [generated] analytics.grpc.pb.h

util/*  (stdlib only — 외부 라이브러리 의존 없음)
```

---

## 장애 격리 전략

| 장애 시나리오 | 동작 |
|---|---|
| gRPC 분석 서버 다운 | GrpcStreamClient 가 2초 백오프 후 재연결. DoH 서비스 무중단 |
| 분석 큐 가득 참 | 가장 오래된 이벤트 드롭 + spdlog warn. 메모리 한도 유지 |
| UDP DNS 타임아웃 | `dns_timeout_ms` 경과 후 TCP로 자동 재시도 |
| TCP DNS 타임아웃 | `dns_timeout_ms × 2` 경과 후 502 Bad Gateway 반환 |
| 잘못된 경로 요청 | 즉시 403 Forbidden. DNS 호출 없음 |
| 65535 bytes 초과 본문 | 즉시 413 Payload Too Large |
| TLS 핸드셰이크 실패 | 세션 조용히 종료 (debug 로그만 기록) |

---

## 도메인 분리 원칙

각 모듈은 **단일 책임**을 가지며 인터페이스를 통해서만 통신합니다.

```
┌──────────┐    ┌──────────┐    ┌──────────────┐
│  util/   │◄───│  dns/    │◄───│  session/    │
│ (순수함수)│    │ (전달)   │    │ (오케스트레이션│
└──────────┘    └──────────┘    └──────────────┘
                                       │
                               ┌───────┴────────┐
                               │  analytics/    │
                               │  (인터페이스)  │
                               └────────────────┘
```

- `util/` : 외부 라이브러리 의존 없음. 단위 테스트 용이
- `dns/DnsUpstream` : 인터페이스로 추상화 → UdpUpstream / TcpUpstream 교체 가능
- `analytics/AnalyticsClient` : 순수 가상 인터페이스 → 테스트 시 Mock 주입 가능

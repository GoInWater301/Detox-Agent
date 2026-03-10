# 모듈 레퍼런스

각 모듈의 공개 인터페이스, 책임, 내부 동작을 설명합니다.

---

## config — 설정

### `doh::Config`

```cpp
// include/config/config.hpp
namespace doh {
struct Config {
    std::string listen_address;    // DOH_LISTEN_ADDR
    uint16_t    listen_port;       // DOH_LISTEN_PORT
    std::size_t thread_count;      // hardware_concurrency (자동)

    std::string cert_chain_file;   // DOH_CERT_CHAIN
    std::string private_key_file;  // DOH_PRIVATE_KEY

    std::string dns_upstream_host; // DOH_DNS_UPSTREAM
    uint16_t    dns_upstream_port; // DOH_DNS_PORT
    uint32_t    dns_timeout_ms;    // DOH_DNS_TIMEOUT_MS

    std::string analytics_endpoint;   // DOH_ANALYTICS_EP
    std::size_t analytics_queue_cap;  // DOH_ANALYTICS_CAP

    [[nodiscard]] static Config from_env();
};
}
```

- **`from_env()`**: 환경변수를 읽어 Config 생성. 없으면 기본값 사용.
- 생성 후 **불변(immutable)**. 모든 컴포넌트에 `const Config&` 로 전달.

---

## server — 네트워크 인프라

### `doh::server::make_tls_context()`

```cpp
// include/server/tls_context.hpp
[[nodiscard]] ssl::context make_tls_context(
    const std::string& cert_chain_file,
    const std::string& private_key_file);
```

- `tls_server` 모드로 `ssl::context` 생성
- SSLv2/SSLv3 비활성화, TLS 1.2+ 강제
- 실패 시 `boost::system::system_error` throw → 서버 즉시 종료 (Fail Fast)

### `doh::server::Listener`

```cpp
// include/server/listener.hpp
class Listener : public std::enable_shared_from_this<Listener> {
public:
    Listener(net::io_context&, ssl::context&, const Config&,
             std::shared_ptr<analytics::AnalyticsClient>);
    void run();
};
```

- `tcp::acceptor` 를 `net::make_strand(ioc)` 위에 생성
- `async_accept()` 루프: 연결 수락 → `HttpsSession` 스폰 → 재귀 호출
- 각 세션 소켓은 `net::make_strand(ioc)` 개별 스트랜드 부여
- Accept 오류 시 로그만 기록하고 루프 계속 (서버 중단 없음)

---

## session — HTTP 세션

### `doh::session::HttpsSession`

```cpp
// include/session/https_session.hpp
class HttpsSession : public std::enable_shared_from_this<HttpsSession> {
public:
    HttpsSession(tcp::socket&&, ssl::context&, const Config&,
                 std::shared_ptr<analytics::AnalyticsClient>);
    void run();
};
```

**비동기 체인**

```
run()
  └─ dispatch(strand) → do_handshake()
       └─ async_handshake() → on_handshake()
            └─ do_read()
                 └─ async_read(*parser_) → on_read()
                      └─ handle_doh_request()
                           └─ forwarder_.async_forward() → [DNS callback]
                                └─ analytics_->send()    (비블로킹)
                                └─ do_send(response)
                                     └─ async_write() → on_write()
                                          ├─ keep-alive: do_read()
                                          └─ close:      do_close()
```

**오류 처리**

| 조건 | 응답 |
|------|------|
| 경로 형식 오류 | 403 |
| DNS 페이로드 없음/파싱 실패 | 400 |
| 본문 > 65535 bytes | 413 |
| DNS 업스트림 실패 | 502 |
| TLS/HTTP 오류 | 세션 종료 (응답 없음) |

---

## dns — DNS 전달

### `doh::dns::QueryCallback`

```cpp
// include/dns/dns_upstream.hpp
using QueryCallback =
    std::function<void(boost::system::error_code, std::vector<uint8_t>)>;
```

모든 비동기 DNS 작업의 콜백 타입. **정확히 한 번** 호출 보장.

### `doh::dns::DnsUpstream` (인터페이스)

```cpp
struct DnsUpstream {
    virtual ~DnsUpstream() = default;
    virtual void async_query(std::vector<uint8_t> query, QueryCallback cb) = 0;
};
```

### `doh::dns::UdpUpstream`

```cpp
// include/dns/udp_upstream.hpp
class UdpUpstream : public DnsUpstream {
public:
    UdpUpstream(any_io_executor ex, std::string host, uint16_t port,
                uint32_t timeout_ms);
    void async_query(std::vector<uint8_t> query, QueryCallback cb) override;
};
```

- 쿼리마다 새 `udp::socket` 생성 (one-shot, stateless)
- `steady_timer` 로 타임아웃 강제
- 타임아웃 시 콜백에 `net::error::timed_out` 전달

**내부 상태 (`UdpState`)**

```
UdpState (shared_ptr)
  ├─ udp::socket   — send_to() / receive_from()
  ├─ steady_timer  — 타임아웃 감시
  ├─ done: bool    — 중복 콜백 방지 (timer vs recv 경쟁)
  └─ rbuf[65536]   — 수신 버퍼
```

### `doh::dns::TcpUpstream`

```cpp
// include/dns/tcp_upstream.hpp
class TcpUpstream : public DnsUpstream {
public:
    TcpUpstream(any_io_executor ex, std::string host, uint16_t port,
                uint32_t timeout_ms);
    void async_query(std::vector<uint8_t> query, QueryCallback cb) override;
};
```

- RFC 1035 §4.2.2 TCP 프레이밍: `[2-byte big-endian length][dns message]`
- 타임아웃 = `timeout_ms × 2` (UDP보다 여유 있게)
- 비동기 체인: `connect → write(wire) → read(lenbuf[2]) → read(body)`

### `doh::dns::DnsForwarder`

```cpp
// include/dns/dns_forwarder.hpp
class DnsForwarder {
public:
    DnsForwarder(std::string host, uint16_t port, uint32_t timeout_ms);

    void async_forward(any_io_executor ex,
                       std::vector<uint8_t> query,
                       ForwardCallback cb);
};
```

**하이브리드 전략**

```
async_forward()
  └─ UdpUpstream::async_query(query)
       ├─ 성공 + TC=0: cb(ok, response)           ← 일반 경로
       ├─ 성공 + TC=1: TcpUpstream::async_query()  ← 자동 fallback
       ├─ 실패 + timeout: TcpUpstream::async_query() ← 자동 fallback
       │                 └─ cb(ok, full_response)
       └─ 실패:         cb(error, {})
```

`util::is_dns_truncated()` 로 TC bit (DNS 헤더 byte[2] bit1) 확인.

---

## analytics — 분석 클라이언트

### `doh::analytics::QueryEvent`

```cpp
// include/analytics/analytics_client.hpp
struct QueryEvent {
    std::string          user_id;
    std::string          client_ip;
    std::vector<uint8_t> raw_query;      // DNS 요청 원본
    std::vector<uint8_t> raw_response;   // DNS 응답 원본
    int64_t              timestamp_us;   // system_clock 기준 마이크로초
    uint32_t             latency_us;     // steady_clock 기준 왕복 시간
    bool                 used_tcp;       // TCP fallback 여부
};
```

### `doh::analytics::AnalyticsClient` (인터페이스)

```cpp
struct AnalyticsClient {
    virtual ~AnalyticsClient() = default;
    virtual void send(QueryEvent event) noexcept = 0;
};
```

- `noexcept`: 구현체는 절대 throw 금지
- 호출 스레드를 블록해서는 안 됨

### `doh::analytics::GrpcStreamClient`

```cpp
// include/analytics/grpc_stream_client.hpp
class GrpcStreamClient : public AnalyticsClient {
public:
    GrpcStreamClient(const std::string& endpoint, std::size_t queue_cap);
    void send(QueryEvent event) noexcept override;
};
```

**내부 동작**

```
send()  [io_context 스레드에서 호출]
  └─ mutex lock → deque::push_back() → cv.notify_one()
       (queue_cap 초과 시 가장 오래된 항목 pop + warn)

worker_  [std::jthread — gRPC 전용]
  └─ try_stream_once()
       ├─ stub_->StreamQueries(&ctx, &ack) → ClientWriter 획득
       ├─ loop: cv.wait_for(200ms) → deque drain → writer->Write(msg)
       │         Write() 실패 → WritesDone() → Finish() → return false
       └─ 정상 종료: WritesDone() → Finish() → return true

worker_loop()
  └─ while(running_): try_stream_once()
       실패 시 sleep(2s) → 재연결
```

**스레드 안전성**: `deque` 접근은 `std::mutex` 보호. `running_` 은 `std::atomic<bool>`.

---

## util — 유틸리티

### `doh::util::parse_user_id()`

```cpp
// include/util/path_parser.hpp
[[nodiscard]] std::optional<std::string_view>
parse_user_id(std::string_view target) noexcept;
```

- `/{user_id}/dns-query[?...]` 형식 파싱
- 반환값은 `target` 의 서브뷰 (Zero-copy, 메모리 할당 없음)
- 유효하지 않으면 `std::nullopt`

```
"/alice/dns-query?dns=..."  →  "alice"
"/dns-query"                →  nullopt  (user_id 없음)
"//dns-query"               →  nullopt  (user_id 빈 문자열)
"/alice/other"              →  nullopt  (suffix 불일치)
```

### `doh::util::base64url_decode()`

```cpp
// include/util/base64url.hpp
[[nodiscard]] std::optional<std::vector<uint8_t>>
base64url_decode(std::string_view input);
```

- RFC 4648 §5 base64url (`-`, `_`) 디코드
- 표준 base64 (`+`, `/`) 도 허용 (관대한 파싱)
- `=` 패딩은 무시 (RFC 8484: 패딩 없음)
- 유효하지 않은 문자 → `std::nullopt`

### `doh::util::is_dns_truncated()`

```cpp
// include/util/dns_wire.hpp
[[nodiscard]] bool
is_dns_truncated(std::span<const uint8_t> message) noexcept;
```

- DNS 헤더 바이트[2]의 bit1 (TC 플래그) 확인
- `message.size() < 4` 이면 `false`
- `DnsForwarder` 가 UDP→TCP fallback 결정에 사용

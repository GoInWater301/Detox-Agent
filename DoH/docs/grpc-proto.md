# gRPC / Protobuf 레퍼런스

분석 마이크로서비스와의 gRPC 인터페이스를 정의합니다.

---

## Proto 스키마

```protobuf
// protos/analytics.proto
syntax = "proto3";
package analytics;

service AnalyticsService {
  // Client-side streaming: DoH 서버가 이벤트를 밀어 넣고,
  // 서버가 스트림 종료 시 요약 Ack 반환.
  rpc StreamQueries(stream DnsQueryEvent) returns (Ack);
}

message DnsQueryEvent {
  string user_id      = 1;  // URL 경로에서 추출한 사용자 식별자
  bytes  raw_query    = 2;  // DNS 요청 와이어 포맷 (RFC 1035)
  bytes  raw_response = 3;  // DNS 응답 와이어 포맷
  string client_ip    = 4;  // 클라이언트 IP (IPv4 / IPv6)
  int64  timestamp_us = 5;  // system_clock 기준 마이크로초 타임스탬프
  uint32 latency_us   = 6;  // 왕복 DNS 해석 시간 (마이크로초)
  bool   used_tcp     = 7;  // true = TCP fallback 사용
}

message Ack {
  int64 accepted_count = 1;  // 분석 서버가 처리한 이벤트 수
}
```

---

## 스트리밍 패턴

```
DoH Forwarder (Client)          Analytics Server
       │                               │
       │── StreamQueries() ──────────► │  스트림 개시
       │                               │
       │── DnsQueryEvent{...} ────────►│  쿼리 이벤트 1
       │── DnsQueryEvent{...} ────────►│  쿼리 이벤트 2
       │── DnsQueryEvent{...} ────────►│  쿼리 이벤트 N
       │        ...                    │
       │── WritesDone() ──────────────►│  스트림 종료 신호
       │◄── Ack{accepted_count} ───────│  서버 응답
       │                               │
       │  (오류 발생 시 → 재연결)       │
```

### 재연결 전략

```
연결 실패 또는 Write() 오류
  └─ WritesDone() + Finish()
       └─ sleep(2초)
            └─ 새 ClientContext + 새 ClientWriter 생성
                 └─ 스트리밍 재개
```

큐에 쌓인 이벤트는 재연결 후 계속 전송됩니다.

---

## 분석 서버 구현 가이드

`AnalyticsService.StreamQueries` RPC를 구현해야 합니다.

### Python 예시 (gRPC 서버)

```python
# analytics_server.py
import grpc
from concurrent import futures
import analytics_pb2
import analytics_pb2_grpc

class AnalyticsService(analytics_pb2_grpc.AnalyticsServiceServicer):
    def StreamQueries(self, request_iterator, context):
        count = 0
        for event in request_iterator:
            print(f"[{event.user_id}] {event.client_ip} "
                  f"latency={event.latency_us}µs tcp={event.used_tcp}")
            # 여기서 DB 저장, Kafka 발행 등 처리
            count += 1
        return analytics_pb2.Ack(accepted_count=count)

server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
analytics_pb2_grpc.add_AnalyticsServiceServicer_to_server(
    AnalyticsService(), server)
server.add_insecure_port('[::]:50051')
server.start()
server.wait_for_termination()
```

### Go 예시

```go
// analytics_server.go
type server struct {
    analytics.UnimplementedAnalyticsServiceServer
}

func (s *server) StreamQueries(
    stream analytics.AnalyticsService_StreamQueriesServer,
) error {
    var count int64
    for {
        event, err := stream.Recv()
        if err == io.EOF {
            return stream.SendAndClose(&analytics.Ack{AcceptedCount: count})
        }
        if err != nil {
            return err
        }
        log.Printf("[%s] %s latency=%dµs", event.UserId, event.ClientIp, event.LatencyUs)
        // 처리 로직
        count++
    }
}
```

---

## 보안 고려사항

현재 구현은 **InsecureChannel** (`grpc::InsecureChannelCredentials()`) 을 사용합니다.
프로덕션 환경에서는 분석 서버와의 연결에도 TLS를 적용해야 합니다.

### TLS 적용 방법

```cpp
// src/analytics/grpc_stream_client.cpp
// 현재:
auto creds = grpc::InsecureChannelCredentials();

// TLS 적용:
grpc::SslCredentialsOptions opts;
opts.pem_root_certs = /* CA cert PEM string */;
auto creds = grpc::SslCredentials(opts);

channel_ = grpc::CreateChannel(endpoint_, creds);
```

---

## 메시지 필드 상세

### DnsQueryEvent

| 필드 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `user_id` | string | URL 경로의 `{user_id}` 세그먼트 | `"alice"` |
| `raw_query` | bytes | DNS 요청 와이어 포맷 (RFC 1035) | `\xaa\xbb\x01\x00...` |
| `raw_response` | bytes | DNS 응답 와이어 포맷 | `\xaa\xbb\x81\x80...` |
| `client_ip` | string | 클라이언트 IP 주소 문자열 | `"203.0.113.1"` |
| `timestamp_us` | int64 | Unix epoch 기준 마이크로초 | `1741392000000000` |
| `latency_us` | uint32 | DNS 해석 왕복 시간 (µs) | `2300` |
| `used_tcp` | bool | TCP fallback 사용 여부 | `false` |

### DNS 와이어 포맷 파싱

분석 서버에서 `raw_query` / `raw_response` 를 파싱하려면 RFC 1035 DNS 와이어 포맷을 이해해야 합니다.

```
DNS 헤더 (12 bytes):
  [0-1]  ID
  [2]    QR(1) OPCODE(4) AA(1) TC(1) RD(1)
  [3]    RA(1) Z(1) AD(1) CD(1) RCODE(4)
  [4-5]  QDCOUNT (질문 개수)
  [6-7]  ANCOUNT (응답 레코드 수)
  [8-9]  NSCOUNT
  [10-11] ARCOUNT

이후: Question Section, Answer Section ...
```

Python 파싱 예시:

```python
import struct
import dns.message  # dnspython 라이브러리

def parse_dns(raw: bytes):
    msg = dns.message.from_wire(raw)
    for q in msg.question:
        print(f"Query: {q.name} type={q.rdtype}")
    for a in msg.answer:
        print(f"Answer: {a}")
```

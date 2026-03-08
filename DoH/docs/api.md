# HTTP API 명세

DoH (DNS over HTTPS) RFC 8484 구현입니다.

---

## 공통 사항

| 항목 | 값 |
|------|-----|
| 프로토콜 | HTTPS (TLS 1.2+) |
| HTTP 버전 | HTTP/1.1 |
| 기본 포트 | 443 |
| 경로 형식 | `/{user_id}/dns-query` |

### 경로 규칙

```
/{user_id}/dns-query
  │
  └─ user_id: 영숫자 문자열, 공백/슬래시 불가
              최소 1자 이상
              예) alice, user-123, tenant_a
```

유효하지 않은 경로 (예: `/dns-query`, `//dns-query`, `/user/other`)는 즉시 **403 Forbidden** 반환.

---

## GET — base64url 쿼리

DNS 메시지를 base64url (RFC 4648 §5) 로 인코딩하여 쿼리 파라미터로 전송합니다.

### 요청

```
GET /{user_id}/dns-query?dns=<base64url-encoded-dns-message>
Host: doh.example.com
Accept: application/dns-message
```

| 파라미터 | 위치 | 필수 | 설명 |
|----------|------|------|------|
| `dns` | Query string | ✅ | DNS 메시지의 base64url 인코딩 (패딩 없음) |

### 예시

```bash
# A 레코드 조회: example.com
DNS_QUERY=$(python3 -c "
import base64, struct
# 최소 DNS query: example.com A 레코드
q = bytes.fromhex('000001000001000000000000076578616d706c6503636f6d0000010001')
print(base64.urlsafe_b64encode(q).rstrip(b'=').decode())
")

curl -s "https://doh.example.com/alice/dns-query?dns=$DNS_QUERY" \
  --output response.bin
```

---

## POST — 바이너리 바디

DNS 메시지 바이너리를 요청 본문으로 직접 전송합니다.

### 요청

```
POST /{user_id}/dns-query
Host: doh.example.com
Content-Type: application/dns-message
Accept: application/dns-message

<binary DNS message>
```

| 헤더 | 필수 | 값 |
|------|------|-----|
| `Content-Type` | 권장 | `application/dns-message` |
| `Content-Length` | 자동 | 최대 65535 bytes |

### 예시

```bash
# A 레코드 조회: example.com (binary)
printf '\x00\x00\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x07example\x03com\x00\x00\x01\x00\x01' \
  | curl -s "https://doh.example.com/alice/dns-query" \
    -H "Content-Type: application/dns-message" \
    --data-binary @- \
    --output response.bin
```

---

## 응답

### 성공 (200 OK)

```
HTTP/1.1 200 OK
Content-Type: application/dns-message
Cache-Control: no-cache
Content-Length: <n>

<binary DNS response>
```

응답 본문은 RFC 1035 DNS 와이어 포맷 바이너리입니다.

### 오류 응답

| 상태 코드 | 원인 | 본문 |
|-----------|------|------|
| `400 Bad Request` | `dns` 파라미터 누락 또는 base64url 디코딩 실패 | 텍스트 오류 메시지 |
| `403 Forbidden` | 잘못된 경로 형식 (`/{user_id}/dns-query` 미충족) | 텍스트 오류 메시지 |
| `405 Method Not Allowed` | GET / POST 외 메서드 | — |
| `413 Payload Too Large` | 요청 본문 > 65535 bytes | 텍스트 오류 메시지 |
| `502 Bad Gateway` | DNS 업스트림 타임아웃 또는 오류 | 텍스트 오류 메시지 |

---

## Keep-Alive

HTTP/1.1 기본 동작에 따라 동일 연결에서 다수의 DoH 쿼리를 연속 전송할 수 있습니다.

```
Client                          Server
  │── GET /alice/dns-query ────►│
  │◄── 200 OK (dns-response) ───│
  │── POST /alice/dns-query ───►│  (같은 TLS 연결 재사용)
  │◄── 200 OK (dns-response) ───│
  │── Connection: close ────────►│
  │◄── 200 OK + FIN ────────────│
```

---

## 클라이언트 설정 예시

### Firefox

```
about:config
  → network.trr.mode = 2
  → network.trr.uri = https://doh.example.com/default/dns-query
```

### Chrome / Chromium

```
설정 → 보안 → DNS → 맞춤 제공업체
  → https://doh.example.com/default/dns-query
```

### curl

```bash
curl --doh-url "https://doh.example.com/cli/dns-query" \
     https://target.example.com
```

### systemd-resolved

```ini
# /etc/systemd/resolved.conf
[Resolve]
DNS=127.0.0.1
DNSOverTLS=yes
```

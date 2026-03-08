# 설정 레퍼런스

설정은 **`.env` 파일** 또는 **환경변수**로 주입합니다.

```
우선순위: 프로세스 환경변수  >  .env 파일  >  코드 기본값
```

서버는 시작 시 `.env`를 로드한 뒤 환경변수를 읽습니다. 이미 설정된 환경변수는 `.env`로 덮어쓰지 않습니다.

## .env 파일

`.env.example`을 복사하여 사용하세요.

```bash
cp .env.example .env
# .env 편집 후 서버 시작
./build/doh-forwarder
```

기본 경로는 실행 디렉토리의 `.env`입니다. 다른 경로를 사용하려면:

```bash
DOH_ENV_FILE=/etc/doh/production.env ./build/doh-forwarder
```

### .env 문법

```bash
KEY=value               # 기본 형식
KEY="value with spaces" # 따옴표 지원
KEY='value with spaces'
export KEY=value        # export 접두사 지원
# 이 줄은 주석               # # 뒤는 무시
```

---

## 환경변수 목록

### 네트워크

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `DOH_LISTEN_ADDR` | `0.0.0.0` | 바인딩할 IP 주소. IPv6는 `::` |
| `DOH_LISTEN_PORT` | `443` | HTTPS 수신 포트. 1024 미만은 root 권한 또는 `CAP_NET_BIND_SERVICE` 필요 |

### TLS (Let's Encrypt)

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `DOH_CERT_CHAIN` | `certs/fullchain.pem` | 인증서 체인 파일 경로 (Let's Encrypt `fullchain.pem`) |
| `DOH_PRIVATE_KEY` | `certs/privkey.pem` | 개인 키 파일 경로 (Let's Encrypt `privkey.pem`) |

> 경로는 실행 바이너리 기준 상대 경로 또는 절대 경로 모두 가능합니다.
> 파일을 읽지 못하면 서버가 즉시 종료됩니다 (Fail Fast).

### DNS 업스트림

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `DOH_DNS_UPSTREAM` | `8.8.8.8` | DNS 업스트림 서버 IP (IPv4/IPv6, 호스트명 불가) |
| `DOH_DNS_PORT` | `53` | DNS 업스트림 포트 |
| `DOH_DNS_TIMEOUT_MS` | `3000` | UDP 응답 대기 타임아웃 (밀리초). 초과 시 TCP 재시도. TCP는 `×2` 적용 |

### 분석 (gRPC)

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `DOH_ANALYTICS_EP` | `localhost:50051` | 분석 서버 gRPC 엔드포인트 (`host:port`) |
| `DOH_ANALYTICS_CAP` | `4096` | 분석 이벤트 인메모리 큐 최대 크기. 초과 시 가장 오래된 이벤트 드롭 |

### DNS TTL 오버라이드 (사용자 모니터링)

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `DOH_DNS_MIN_TTL` | `30` | 최소 TTL (초). 업스트림 TTL이 이보다 작으면 이 값으로 올림 |
| `DOH_DNS_MAX_TTL` | `60` | 최대 TTL (초). 업스트림 TTL이 이보다 크면 이 값으로 내림 |

> **모니터링 목적**: TTL이 짧을수록 클라이언트가 자주 DoH 서버에 재질의합니다.
> 30~60초 범위는 사용자 행동 패턴 파악에 충분한 데이터 밀도를 제공하면서
> DNS 캐시 효과를 유지합니다.
>
> HTTP 응답의 `Cache-Control: max-age` 헤더도 동일한 값으로 설정됩니다.

### 성능

| 변수 | 기본값 | 설명 |
|------|--------|------|
| *(없음)* | `hardware_concurrency` | 스레드 풀 크기는 자동 감지. 현재 환경변수로 오버라이드 불가 |

---

## 배포 예시

### 개발 환경

```bash
DOH_LISTEN_PORT=8443 \
DOH_CERT_CHAIN=/path/to/fullchain.pem \
DOH_PRIVATE_KEY=/path/to/privkey.pem \
DOH_DNS_UPSTREAM=1.1.1.1 \
DOH_ANALYTICS_EP=localhost:50051 \
./build/doh-forwarder
```

### 프로덕션 (systemd)

```ini
# /etc/systemd/system/doh-forwarder.service
[Unit]
Description=DoH Beast Forwarder
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=/opt/doh-forwarder/doh-forwarder
User=doh
Group=doh

Environment=DOH_LISTEN_ADDR=0.0.0.0
Environment=DOH_LISTEN_PORT=443
Environment=DOH_CERT_CHAIN=/etc/letsencrypt/live/doh.example.com/fullchain.pem
Environment=DOH_PRIVATE_KEY=/etc/letsencrypt/live/doh.example.com/privkey.pem
Environment=DOH_DNS_UPSTREAM=8.8.8.8
Environment=DOH_DNS_PORT=53
Environment=DOH_DNS_TIMEOUT_MS=3000
Environment=DOH_ANALYTICS_EP=analytics.internal:50051
Environment=DOH_ANALYTICS_CAP=8192

# 443 포트 바인딩 권한
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE

Restart=on-failure
RestartSec=5s

[Install]
WantedBy=multi-user.target
```

### Docker

```dockerfile
FROM debian:12-slim
COPY doh-forwarder /usr/local/bin/
EXPOSE 443

ENV DOH_LISTEN_ADDR=0.0.0.0 \
    DOH_LISTEN_PORT=443 \
    DOH_DNS_UPSTREAM=8.8.8.8 \
    DOH_DNS_TIMEOUT_MS=3000 \
    DOH_ANALYTICS_CAP=4096

CMD ["doh-forwarder"]
```

```yaml
# docker-compose.yml
services:
  doh-forwarder:
    image: doh-beast-forwarder:latest
    ports:
      - "443:443"
    volumes:
      - /etc/letsencrypt/live/doh.example.com:/certs:ro
    environment:
      DOH_CERT_CHAIN: /certs/fullchain.pem
      DOH_PRIVATE_KEY: /certs/privkey.pem
      DOH_ANALYTICS_EP: analytics:50051
    restart: unless-stopped
```

---

## 인증서 갱신 (Let's Encrypt)

Let's Encrypt 인증서는 90일마다 갱신됩니다. 갱신 후 서버를 재시작해야 새 인증서가 적용됩니다.

```bash
# certbot 갱신 훅으로 자동 재시작
# /etc/letsencrypt/renewal-hooks/deploy/restart-doh.sh
#!/bin/bash
systemctl restart doh-forwarder
```

```bash
chmod +x /etc/letsencrypt/renewal-hooks/deploy/restart-doh.sh
```

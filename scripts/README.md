# Local Shell Runner

Docker 없이 로컬에서 `webserver`, `Agent`, `DoH` 3개 서비스를 동시에 실행하기 위한 스크립트입니다.

## 사용법

```sh
sh scripts/local-up.sh
sh scripts/local-status.sh
sh scripts/local-down.sh
```

`local-down.sh`는 PID 파일이 없더라도 기본 포트(`8080`, `8000`, `8443`)를 기준으로 실행 중인 프로세스를 찾아 종료를 시도합니다.
이전에 `sudo sh scripts/local-up.sh`로 띄운 프로세스는 일반 사용자 권한으로 종료되지 않을 수 있으므로, 그런 경우 안내되는 `sudo kill ...` 또는 `sudo fuser -k ...`를 사용하세요.

권장 시작 순서:

```sh
sudo fuser -k 8080/tcp 8000/tcp 50052/tcp 8443/tcp
sh scripts/local-up.sh
sh scripts/local-status.sh
```

## 로그

- 로그 디렉터리: `logs/`
- 최신 로그 심볼릭 링크:
  - `logs/webserver.log`
  - `logs/agent.log`
  - `logs/doh.log`
- 실행 시점별 원본 로그 파일:
  - `logs/webserver-YYYYMMDD-HHMMSS.log`
  - `logs/agent-YYYYMMDD-HHMMSS.log`
  - `logs/doh-YYYYMMDD-HHMMSS.log`

## 전제 조건

- `webserver`: `./gradlew bootRun` 가능해야 함
- `Agent`: `uv run main.py` 가능해야 함
- `DoH`: `./build/doh-forwarder` 또는 `DOH_BINARY` 로 지정한 실행 파일이 있어야 함
- PostgreSQL / Redis 는 Docker가 아닌 별도 로컬 또는 외부 환경에서 준비되어 있어야 함
- DoH TLS 인증서 파일이 준비되어 있어야 함

## 로컬 TLS 인증서 예시

테스트용 self-signed 인증서 생성:

```sh
cd DoH
mkdir -p certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout certs/privkey.pem \
  -out certs/fullchain.pem \
  -subj "/CN=localhost"
cd ..
```

## DoH 기본값

- `DOH_ENV_FILE` 기본값은 `.env`
- `DOH_LISTEN_ADDR` 기본값은 `0.0.0.0`
- `DOH_LISTEN_PORT` 기본값은 `8443`
- `DOH_DNS_UPSTREAM` 기본값은 `127.0.0.53`
- `DOH_DNS_PORT` 기본값은 `53`
- `DOH_DNS_TIMEOUT_MS` 기본값은 `3000`
- `DOH_DNS_MIN_TTL` 기본값은 `30`
- `DOH_DNS_MAX_TTL` 기본값은 `60`
- `DOH_FILTER_ENABLED` 기본값은 `true`
- `DOH_REDIS_HOST` 기본값은 `127.0.0.1`
- `DOH_REDIS_PORT` 기본값은 `6379`
- `DOH_REDIS_TIMEOUT_MS` 기본값은 `5000`
- `DOH_REDIS_REFRESH_MS` 기본값은 `1000`
- `DOH_FILTER_FAIL_OPEN` 기본값은 `false`
- `DOH_BLOCK_RESPONSE` 기본값은 `NXDOMAIN`
- `DOH_ANALYTICS_EP` 기본값은 `localhost:50051`
- `DOH_ANALYTICS_CAP` 기본값은 `4096`
- `DOH_CERT_CHAIN` 기본값은 `certs/fullchain.pem`
- `DOH_PRIVATE_KEY` 기본값은 `certs/privkey.pem`
- `DOH_LOG_LEVEL` 기본값은 `debug`
- 다른 바이너리를 쓰려면 예시처럼 실행합니다:

```sh
DOH_BINARY=./build-release/doh-forwarder sh scripts/local-up.sh
```

필터는 기본적으로 켜집니다. 끄려면 예시처럼 넘기면 됩니다:

```sh
DOH_FILTER_ENABLED=false \
sh scripts/local-up.sh
```

`uv`가 기본 PATH에 없으면:

```sh
UV_BIN=/home/<user>/.local/bin/uv sh scripts/local-up.sh
```

## 주의 사항

- `local-up.sh`는 가능하면 `sudo` 없이 실행하세요.
- `sudo`로 실행하면 셸 환경이 달라져 `uv` 경로 문제가 생길 수 있습니다.
- `sudo`로 실행한 서비스는 `local-down.sh`에서 일반 권한으로 종료되지 않을 수 있습니다.
- 포트 정리나 강제 종료가 필요할 때만 `sudo`를 쓰는 편이 안전합니다.

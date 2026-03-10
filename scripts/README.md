# Local Shell Runner

Docker 없이 로컬에서 `webserver`, `Agent`, `DoH` 3개 서비스를 동시에 실행하기 위한 스크립트입니다.

## 사용법

```sh
sh scripts/local-up.sh
sh scripts/local-status.sh
sh scripts/local-down.sh
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

## DoH 기본값

- `DOH_LISTEN_PORT` 기본값은 `8443`
- `DOH_ANALYTICS_EP` 기본값은 `localhost:50051`
- 다른 바이너리를 쓰려면 예시처럼 실행합니다:

```sh
DOH_BINARY=./build-release/doh-forwarder sh scripts/local-up.sh
```

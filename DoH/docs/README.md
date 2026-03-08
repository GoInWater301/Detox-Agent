# DoH Beast Forwarder

고성능 **DNS over HTTPS (DoH)** 포워딩 서버.
Boost.Beast + Asio 기반 비동기 I/O, Let's Encrypt TLS, gRPC 실시간 스트리밍 분석을 제공합니다.

---

## 문서 목차

| 문서 | 설명 |
|------|------|
| [architecture.md](./architecture.md) | 시스템 아키텍처 및 컴포넌트 설계 |
| [api.md](./api.md) | HTTP API 명세 (DoH RFC 8484) |
| [configuration.md](./configuration.md) | 환경변수 설정 레퍼런스 |
| [build.md](./build.md) | 빌드 및 배포 가이드 |
| [modules.md](./modules.md) | 모듈별 코드 레퍼런스 |
| [grpc-proto.md](./grpc-proto.md) | gRPC Protobuf 스키마 및 분석 서버 연동 |

---

## 빠른 시작

```bash
# 1. 빌드
cmake -B build \
  -DCMAKE_TOOLCHAIN_FILE=$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake \
  -DCMAKE_BUILD_TYPE=Release
cmake --build build -j$(nproc)

# 2. 인증서 준비 (Let's Encrypt)
ln -s /etc/letsencrypt/live/<domain>/fullchain.pem certs/fullchain.pem
ln -s /etc/letsencrypt/live/<domain>/privkey.pem   certs/privkey.pem

# 3. 실행
DOH_LISTEN_PORT=443 DOH_ANALYTICS_EP=analytics-server:50051 ./build/doh-forwarder
```

---

## 핵심 특징

- **RFC 8484 준수**: GET (base64url) / POST (binary) 양방향 DoH 지원
- **사용자 격리**: `/{user_id}/dns-query` 경로 기반 트래픽 분리
- **하이브리드 DNS**: UDP 우선 → TC=1 시 TCP 자동 재시도 (RFC 1035)
- **Stateless**: 내부 DB/캐시 없음. 모든 데이터는 분석 서버로 스트리밍
- **장애 격리**: gRPC 분석 서버 다운 시 DoH 서비스 무중단 지속
- **고성능**: `io_context` 스레드 풀 + 스트랜드 기반 Lock-free 세션 관리

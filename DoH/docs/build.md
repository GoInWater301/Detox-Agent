# 빌드 및 배포 가이드

---

## 사전 요구사항

| 도구 | 최소 버전 | 용도 |
|------|-----------|------|
| GCC 또는 Clang | GCC 13+ / Clang 16+ | C++20 (`std::jthread`, `std::stop_token`) |
| CMake | 3.20+ | 빌드 시스템 |
| vcpkg | 최신 | 패키지 관리 |
| Ninja *(선택)* | — | 빠른 빌드 |

---

## vcpkg 설치

```bash
git clone https://github.com/microsoft/vcpkg.git $HOME/vcpkg
$HOME/vcpkg/bootstrap-vcpkg.sh
export VCPKG_ROOT=$HOME/vcpkg
```

> `VCPKG_ROOT` 환경변수를 `.bashrc` / `.zshrc` 에 영구 등록하는 것을 권장합니다.

---

## 의존성 패키지

`vcpkg.json` 에 선언된 패키지가 CMake 빌드 시 자동으로 설치됩니다.

| 패키지 | 용도 |
|--------|------|
| `boost-beast` | HTTP/1.1 + SSL 스트림 (Asio 포함) |
| `openssl` | TLS 1.2/1.3 |
| `grpc` | gRPC 클라이언트 스트리밍 |
| `protobuf` | 직렬화 + protoc |
| `spdlog` | 비동기 로깅 |

---

## 빌드

### Debug 빌드

```bash
cmake -B build \
  -DCMAKE_TOOLCHAIN_FILE=$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake \
  -DCMAKE_BUILD_TYPE=Debug

cmake --build build -j$(nproc)
```

### Release 빌드

```bash
cmake -B build-release \
  -DCMAKE_TOOLCHAIN_FILE=$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON

cmake --build build-release -j$(nproc)
```

### Ninja 사용 (권장)

```bash
cmake -B build -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake \
  -DCMAKE_BUILD_TYPE=Release

ninja -C build
```

빌드 결과물:

```
build/
└── doh-forwarder          # 실행 바이너리
```

---

## Protobuf 코드 생성

CMake가 `add_custom_command` 를 통해 자동으로 처리합니다.

```
protos/analytics.proto
  └─ [cmake --build] 시 protoc 실행
       ├─ build/generated/analytics.pb.h
       ├─ build/generated/analytics.pb.cc
       ├─ build/generated/analytics.grpc.pb.h
       └─ build/generated/analytics.grpc.pb.cc
```

`analytics.proto` 수정 시 다음 CMake 빌드에서 자동으로 재생성됩니다.

---

## 인증서 설정

```bash
# certs/ 에 심볼릭 링크 (권장)
ln -s /etc/letsencrypt/live/<domain>/fullchain.pem certs/fullchain.pem
ln -s /etc/letsencrypt/live/<domain>/privkey.pem   certs/privkey.pem

# 또는 환경변수로 절대 경로 지정
export DOH_CERT_CHAIN=/etc/letsencrypt/live/<domain>/fullchain.pem
export DOH_PRIVATE_KEY=/etc/letsencrypt/live/<domain>/privkey.pem
```

### 개발용 자체 서명 인증서 (테스트 전용)

```bash
openssl req -x509 -newkey rsa:4096 -nodes \
  -keyout certs/privkey.pem \
  -out certs/fullchain.pem \
  -days 365 \
  -subj "/CN=localhost"
```

---

## 실행

```bash
# 포트 443은 루트 권한 또는 CAP_NET_BIND_SERVICE 필요
sudo ./build/doh-forwarder

# 또는 1024 이상 포트 사용
DOH_LISTEN_PORT=8443 ./build/doh-forwarder

# 전체 옵션 지정
DOH_LISTEN_PORT=443 \
DOH_DNS_UPSTREAM=1.1.1.1 \
DOH_ANALYTICS_EP=analytics.internal:50051 \
./build/doh-forwarder
```

### CAP_NET_BIND_SERVICE 부여 (루트 없이 443 포트)

```bash
sudo setcap 'cap_net_bind_service=+ep' ./build/doh-forwarder
./build/doh-forwarder  # 이제 루트 없이 443 포트 사용 가능
```

---

## 동작 확인

```bash
# GET 방식
DNS_MSG=$(python3 -c "
import base64
# 최소 DNS query (A 레코드, example.com)
b = bytes.fromhex('aabb01000001000000000000076578616d706c6503636f6d0000010001')
print(base64.urlsafe_b64encode(b).rstrip(b'=').decode())
")
curl -sk "https://localhost:8443/testuser/dns-query?dns=$DNS_MSG" | xxd | head

# POST 방식
python3 -c "
import sys, struct
b = bytes.fromhex('aabb01000001000000000000076578616d706c6503636f6d0000010001')
sys.stdout.buffer.write(b)
" | curl -sk "https://localhost:8443/testuser/dns-query" \
     -H "Content-Type: application/dns-message" \
     --data-binary @- | xxd | head
```

---

## 로그 레벨

`spdlog` 기반. 현재 환경변수로 레벨 제어는 지원하지 않습니다.
소스에서 `spdlog::set_level()` 호출 (`src/main.cpp`) 을 변경하세요.

| 레벨 | 사용 위치 |
|------|-----------|
| `info` | 서버 시작, TLS 로드, Listener 바인딩 |
| `warn` | 분석 큐 overflow, gRPC 재연결 |
| `error` | Accept 오류, gRPC Write 실패 |
| `debug` | TLS 핸드셰이크 실패, read/write 오류 (고빈도) |

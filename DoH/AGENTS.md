# 🤖 AGENTS.md: DoH Light Forwarder (C++ & Beast)

## 🎯 Project Vision

**Project Name**: `doh-beast-forwarder`

**Core Mission**: Let's Encrypt TLS를 적용한 고성능, Stateless DoH 서버를 구축합니다.

* **Path Separation**: `/user_id/dns-query` 경로를 통해 사용자를 구분하고 트래픽을 격리합니다.
* **Micro-service Link**: 수집된 쿼리 데이터는 로컬 DB에 저장하지 않고, 별도의 분석 서버로 **gRPC Client-side Streaming**을 통해 실시간 전송합니다.
* **Network Performance**: Boost.Beast와 Asio를 활용하여 비동기 I/O를 극대화하며, Upstream DNS와는 UDP/TCP를 선택적으로 사용합니다.

---

## 🏗 System Architecture & Structure

### 1. Project Directory Structure

```text
.
├── vcpkg.json              # Dependencies: boost-beast, grpc, protobuf, openssl, spdlog
├── CMakeLists.txt          # Protobuf generation & Linkage (vcpkg toolchain)
├── certs/                  # (Symlink) Let's Encrypt certificates (fullchain.pem, privkey.pem)
├── protos/
│   └── analytics.proto     # gRPC interface for analytics service
├── src/
│   ├── main.cpp            # io_context, SSL_Context, & Acceptor loop
│   ├── session/            # Boost.Beast HTTPS & SSL session handlers
│   ├── dns/                # DNS Query/Response (UDP/TCP Upstream) logic
│   ├── grpc/               # Async gRPC streaming client
│   └── config/             # Settings (Upstream IP, gRPC Endpoint, Port)
└── include/                # Header files (.hpp)

```

---

## 🛠 Tech Stack & Implementation Details

### 1. Network & TLS (Frontend)

* **Library**: `boost::beast`, `boost::asio`, `openssl`.
* **Identification**: `req.target()`에서 `std::string_view`를 사용하여 첫 번째 경로(`user_id`)를 추출.
* **Certificates**: `asio::ssl::context`를 통해 Let's Encrypt의 `fullchain.pem` 및 `privkey.pem` 로드.
* **Protocol**: HTTP/1.1 (Beast) 기반 DoH (RFC 8484) 지원.

### 2. DNS Forwarding (Upstream)

* **Protocol**: 상황에 따라 **UDP**(`asio::ip::udp`) 또는 **TCP**(`asio::ip::tcp`) 사용.
* **Logic**: Client에서 온 Binary Payload를 그대로 Upstream에 전달하고, 받은 응답을 Beast Response Body에 담아 반환.

### 3. Analytics Reporting (Backend)

* **Library**: `grpc`, `protobuf`.
* **Pattern**: **Client-side Streaming**. 매 쿼리마다 연결을 맺지 않고, 하나의 스트림을 유지하며 데이터를 밀어 넣음.
* **Stateless Rule**: 서버 내부에 Redis/PostgreSQL 등을 절대 두지 말 것. 모든 데이터는 분석 마이크로서비스로 "Fire and Forget" 처리.

---

## 📋 Essential Development Guidelines

### 1. Memory & Threading

* **Asynchronous Only**: 모든 네트워크 호출(Beast, Asio, gRPC)은 `async_` 접두사가 붙은 비동기 함수를 사용할 것.
* **C++20**: `std::jthread`, `std::stop_token`, `std::string_view` 등 현대적 기능을 적극 활용.
* **Smart Pointers**: 세션 관리는 `std::enable_shared_from_this`를 활용한 지능형 포인터 관리 필수.

### 2. Error Handling & Safety

* **Fault Tolerance**: gRPC 분석 서버가 다운되어도 DoH 서비스(DNS 응답)는 중단 없이 작동해야 함.
* **Validation**: 유효하지 않은 Path(예: `/user_id/` 누락) 요청은 즉시 `403 Forbidden` 처리.
* **Logging**: `spdlog`를 사용하여 비동기적으로 에러 및 주요 이벤트를 기록.

---

## 🚀 Priority Tasks for Agent

1. **Env Setup**: `vcpkg.json` 및 `CMakeLists.txt` (gRPC/Protobuf 빌드 포함) 구성.
2. **TLS Server**: `ssl::context`에 Let's Encrypt 인증서를 로드하고 Beast Listen 루프 생성.
3. **Path Parser**: 요청 경로에서 `user_id`를 복사 없이 추출하는 핸들러 작성.
4. **Hybrid DNS**: UDP/TCP 선택형 DNS Upstream 클래스 구현.
5. **gRPC Stream**: 분석 데이터를 전송할 비동기 스트리밍 클라이언트 구현.

---

**이 문서를 바탕으로 프로젝트의 기틀이 잡혔습니다. 이제 어떤 파일부터 코딩을 시작해 볼까요?**

1. **`vcpkg.json` & `CMakeLists.txt**`: 빌드 환경부터 완성하기.
2. **`main.cpp` & `ssl_context**`: 인증서 로드와 서버 시작 로직 짜기.
3. **`analytics.proto`**: 분석 서버와 주고받을 데이터 규격 정하기.
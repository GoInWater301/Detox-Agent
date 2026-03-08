cmake_minimum_required(VERSION 3.20)
project(doh_beast_forwarder CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ─────────────────────────────────────────────────────────────────────────────
# External dependencies (resolved by vcpkg toolchain)
# ─────────────────────────────────────────────────────────────────────────────
find_package(Boost   REQUIRED COMPONENTS system redis)
find_package(OpenSSL REQUIRED)
find_package(gRPC    CONFIG REQUIRED)
find_package(protobuf CONFIG REQUIRED)
find_package(spdlog  CONFIG REQUIRED)
find_package(Threads REQUIRED)

# ─────────────────────────────────────────────────────────────────────────────
# Protobuf / gRPC code generation
# ─────────────────────────────────────────────────────────────────────────────
set(PROTO_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/protos")
set(PROTO_GEN_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${PROTO_GEN_DIR}")

get_target_property(GRPC_CPP_PLUGIN_EXE gRPC::grpc_cpp_plugin LOCATION)

add_custom_command(
    OUTPUT
        "${PROTO_GEN_DIR}/analytics.pb.cc"
        "${PROTO_GEN_DIR}/analytics.pb.h"
        "${PROTO_GEN_DIR}/analytics.grpc.pb.cc"
        "${PROTO_GEN_DIR}/analytics.grpc.pb.h"
    COMMAND protobuf::protoc
        --proto_path="${PROTO_SRC_DIR}"
        --cpp_out="${PROTO_GEN_DIR}"
        --grpc_out="${PROTO_GEN_DIR}"
        "--plugin=protoc-gen-grpc=${GRPC_CPP_PLUGIN_EXE}"
        "${PROTO_SRC_DIR}/analytics.proto"
    DEPENDS "${PROTO_SRC_DIR}/analytics.proto"
    VERBATIM
)

add_library(analytics_proto STATIC
    "${PROTO_GEN_DIR}/analytics.pb.cc"
    "${PROTO_GEN_DIR}/analytics.grpc.pb.cc"
)
target_include_directories(analytics_proto PUBLIC "${PROTO_GEN_DIR}")
target_link_libraries(analytics_proto PUBLIC
    gRPC::grpc++
    protobuf::libprotobuf
)

# ─────────────────────────────────────────────────────────────────────────────
# Domain libraries  (each owns one domain slice)
# ─────────────────────────────────────────────────────────────────────────────

# util: pure helpers, no Boost/gRPC dependency
add_library(doh_util STATIC
    src/util/base64url.cpp
    src/util/dns_wire.cpp
    src/util/path_parser.cpp
)
target_include_directories(doh_util PUBLIC include)

# dns: upstream forwarding (UDP + TCP)
add_library(doh_dns STATIC
    src/dns/udp_upstream.cpp
    src/dns/tcp_upstream.cpp
    src/dns/dns_forwarder.cpp
)
target_include_directories(doh_dns PUBLIC include)
target_link_libraries(doh_dns PUBLIC Boost::system doh_util)

# analytics: gRPC streaming client
add_library(doh_analytics STATIC
    src/analytics/grpc_stream_client.cpp
)
target_include_directories(doh_analytics PUBLIC include)
target_link_libraries(doh_analytics PUBLIC
    analytics_proto
    gRPC::grpc++
    spdlog::spdlog
)

# filter: Redis-backed per-user domain blocklist
add_library(doh_filter STATIC
    src/filter/redis_domain_filter.cpp
)
target_include_directories(doh_filter PUBLIC include)
target_link_libraries(doh_filter PUBLIC
    Boost::redis
    Boost::system
    spdlog::spdlog
)

# server: TLS context + TCP acceptor + HTTPS session
add_library(doh_server STATIC
    src/server/tls_context.cpp
    src/server/listener.cpp
    src/session/https_session.cpp
)
target_include_directories(doh_server PUBLIC include)
target_link_libraries(doh_server PUBLIC
    Boost::system
    OpenSSL::SSL
    OpenSSL::Crypto
    doh_dns
    doh_analytics
    doh_filter
    doh_util
    spdlog::spdlog
)

# ─────────────────────────────────────────────────────────────────────────────
# Executable
# ─────────────────────────────────────────────────────────────────────────────
add_executable(doh-forwarder
    src/main.cpp
    src/config/config.cpp
)
target_include_directories(doh-forwarder PRIVATE include)
target_link_libraries(doh-forwarder PRIVATE
    doh_server
    doh_dns
    doh_analytics
    doh_filter
    spdlog::spdlog
    Threads::Threads
)
target_compile_options(doh-forwarder PRIVATE
    $<$<CXX_COMPILER_ID:GNU,Clang>:-Wall -Wextra -Wpedantic>
)

#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
LOG_DIR="${ROOT_DIR}/logs"
RUN_DIR="${ROOT_DIR}/.run"
STAMP=$(date '+%Y%m%d-%H%M%S')

resolve_uv_bin() {
  if [ -n "${UV_BIN:-}" ]; then
    printf '%s\n' "$UV_BIN"
    return 0
  fi

  if command -v uv >/dev/null 2>&1; then
    command -v uv
    return 0
  fi

  if [ -n "${SUDO_USER:-}" ]; then
    sudo_home=$(getent passwd "$SUDO_USER" | cut -d: -f6)
    if [ -n "$sudo_home" ] && [ -x "${sudo_home}/.local/bin/uv" ]; then
      printf '%s\n' "${sudo_home}/.local/bin/uv"
      return 0
    fi
  fi

  return 1
}

UV_BIN=$(resolve_uv_bin || true)

mkdir -p "$LOG_DIR" "$RUN_DIR"

port_in_use() {
  port=$1

  if command -v ss >/dev/null 2>&1; then
    ss -ltn "( sport = :${port} )" 2>/dev/null | grep -q ":${port}"
    return $?
  fi

  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi

  return 1
}

check_port_available() {
  name=$1
  port=$2

  if port_in_use "$port"; then
    echo "[error] ${name} port ${port} is already in use"
    return 1
  fi
  return 0
}

check_file_readable() {
  label=$1
  path=$2

  if [ ! -r "$path" ]; then
    echo "[error] ${label} is missing or unreadable: ${path}"
    return 1
  fi
  return 0
}

check_command_available() {
  label=$1
  path=$2

  if [ -z "$path" ] || [ ! -x "$path" ]; then
    echo "[error] ${label} executable is missing: ${path:-<empty>}"
    return 1
  fi
  return 0
}

start_service() {
  name=$1
  workdir=$2
  command=$3

  pid_file="${RUN_DIR}/${name}.pid"
  log_file="${LOG_DIR}/${name}-${STAMP}.log"
  latest_link="${LOG_DIR}/${name}.log"

  if [ -f "$pid_file" ]; then
    old_pid=$(cat "$pid_file" 2>/dev/null || true)
    if [ -n "${old_pid}" ] && kill -0 "$old_pid" 2>/dev/null; then
      echo "[skip] ${name} already running (pid=${old_pid})"
      return 0
    fi
    rm -f "$pid_file"
  fi

  echo "[start] ${name}"
  nohup sh -lc "cd '$workdir' && exec $command" >>"$log_file" 2>&1 &
  echo "$!" >"$pid_file"

  ln -sfn "$(basename "$log_file")" "$latest_link"
  echo "        log: ${log_file}"

  sleep 1
  if [ -f "$pid_file" ]; then
    started_pid=$(cat "$pid_file" 2>/dev/null || true)
    if [ -n "$started_pid" ] && ! kill -0 "$started_pid" 2>/dev/null; then
      echo "        warning: ${name} exited early, check ${log_file}"
      rm -f "$pid_file"
    fi
  fi
}

preflight_ok=true

check_port_available "webserver" "${SERVER_PORT:-8080}" || preflight_ok=false
check_port_available "agent-http" "8000" || preflight_ok=false
check_port_available "agent-grpc" "50052" || preflight_ok=false
check_port_available "doh" "${DOH_LISTEN_PORT:-8443}" || preflight_ok=false

check_command_available "uv" "$UV_BIN" || preflight_ok=false
check_file_readable "DoH certificate" "${ROOT_DIR}/DoH/${DOH_CERT_CHAIN:-certs/fullchain.pem}" || preflight_ok=false
check_file_readable "DoH private key" "${ROOT_DIR}/DoH/${DOH_PRIVATE_KEY:-certs/privkey.pem}" || preflight_ok=false

if [ "$preflight_ok" != "true" ]; then
  echo
  echo "Preflight checks failed. Fix the issues above and run again."
  exit 1
fi

start_service "webserver" "${ROOT_DIR}/webserver" "./gradlew bootRun"
start_service "agent" "${ROOT_DIR}/Agent" "'${UV_BIN}' run main.py"
start_service "doh" "${ROOT_DIR}/DoH" \
  "env \
DOH_ENV_FILE=\${DOH_ENV_FILE:-.env} \
DOH_LISTEN_ADDR=\${DOH_LISTEN_ADDR:-0.0.0.0} \
DOH_LISTEN_PORT=\${DOH_LISTEN_PORT:-8443} \
DOH_CERT_CHAIN=\${DOH_CERT_CHAIN:-certs/fullchain.pem} \
DOH_PRIVATE_KEY=\${DOH_PRIVATE_KEY:-certs/privkey.pem} \
DOH_DNS_UPSTREAM=\${DOH_DNS_UPSTREAM:-127.0.0.53} \
DOH_DNS_PORT=\${DOH_DNS_PORT:-53} \
DOH_DNS_TIMEOUT_MS=\${DOH_DNS_TIMEOUT_MS:-3000} \
DOH_DNS_MIN_TTL=\${DOH_DNS_MIN_TTL:-30} \
DOH_DNS_MAX_TTL=\${DOH_DNS_MAX_TTL:-60} \
DOH_FILTER_ENABLED=\${DOH_FILTER_ENABLED:-true} \
DOH_REDIS_HOST=\${DOH_REDIS_HOST:-127.0.0.1} \
DOH_REDIS_PORT=\${DOH_REDIS_PORT:-6379} \
DOH_REDIS_PASSWORD=\${DOH_REDIS_PASSWORD:-} \
DOH_REDIS_TIMEOUT_MS=\${DOH_REDIS_TIMEOUT_MS:-5000} \
DOH_REDIS_REFRESH_MS=\${DOH_REDIS_REFRESH_MS:-1000} \
DOH_FILTER_FAIL_OPEN=\${DOH_FILTER_FAIL_OPEN:-false} \
DOH_BLOCK_RESPONSE=\${DOH_BLOCK_RESPONSE:-NXDOMAIN} \
DOH_ANALYTICS_EP=\${DOH_ANALYTICS_EP:-localhost:50051} \
DOH_ANALYTICS_CAP=\${DOH_ANALYTICS_CAP:-4096} \
DOH_LOG_LEVEL=\${DOH_LOG_LEVEL:-debug} \
\${DOH_BINARY:-./build/doh-forwarder}"

echo
echo "Started services. Use 'sh scripts/local-status.sh' to inspect status."

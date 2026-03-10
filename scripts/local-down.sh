#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
RUN_DIR="${ROOT_DIR}/.run"

find_listening_pid() {
  port=$1

  if command -v lsof >/dev/null 2>&1; then
    lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -n 1
    return 0
  fi

  if command -v ss >/dev/null 2>&1; then
    ss -ltnp "( sport = :${port} )" 2>/dev/null \
      | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' \
      | head -n 1
    return 0
  fi

  return 1
}

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

stop_pid() {
  name=$1
  pid=$2

  if ! kill -0 "$pid" 2>/dev/null; then
    echo "[clean] ${name} stale pid=${pid}"
    return 0
  fi

  echo "[stop] ${name} (pid=${pid})"
  if ! kill "$pid" 2>/dev/null; then
    echo "       unable to stop ${name} (pid=${pid}); try: sudo kill ${pid}"
    return 1
  fi

  i=0
  while kill -0 "$pid" 2>/dev/null; do
    i=$((i + 1))
    if [ "$i" -ge 10 ]; then
      echo "       force kill ${name} (pid=${pid})"
      if ! kill -9 "$pid" 2>/dev/null; then
        echo "       unable to force kill ${name} (pid=${pid}); try: sudo kill -9 ${pid}"
        return 1
      fi
      break
    fi
    sleep 1
  done

  return 0
}

stop_service() {
  name=$1
  port=$2
  pid_file="${RUN_DIR}/${name}.pid"
  pid=""

  if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file" 2>/dev/null || true)
    if [ -z "$pid" ]; then
      rm -f "$pid_file"
      echo "[clean] ${name} pid file was empty"
    fi
  fi

  if [ -z "$pid" ]; then
    pid=$(find_listening_pid "$port" || true)
  fi

  if [ -z "$pid" ]; then
    if port_in_use "$port"; then
      echo "[warn] ${name} port ${port} is in use, but the owning PID is not visible"
      echo "       try: sudo fuser -k ${port}/tcp"
      rm -f "$pid_file"
      return 0
    fi

    echo "[skip] ${name} not running"
    rm -f "$pid_file"
    return 0
  fi

  stop_pid "$name" "$pid" || true
  rm -f "$pid_file"
}

stop_service "doh" "${DOH_LISTEN_PORT:-8443}"
stop_service "agent" "8000"
stop_service "webserver" "${SERVER_PORT:-8080}"

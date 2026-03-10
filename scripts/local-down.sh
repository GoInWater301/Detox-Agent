#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
RUN_DIR="${ROOT_DIR}/.run"

stop_service() {
  name=$1
  pid_file="${RUN_DIR}/${name}.pid"

  if [ ! -f "$pid_file" ]; then
    echo "[skip] ${name} not running"
    return 0
  fi

  pid=$(cat "$pid_file" 2>/dev/null || true)
  if [ -z "$pid" ]; then
    rm -f "$pid_file"
    echo "[clean] ${name} pid file was empty"
    return 0
  fi

  if kill -0 "$pid" 2>/dev/null; then
    echo "[stop] ${name} (pid=${pid})"
    kill "$pid" 2>/dev/null || true

    i=0
    while kill -0 "$pid" 2>/dev/null; do
      i=$((i + 1))
      if [ "$i" -ge 10 ]; then
        echo "       force kill ${name} (pid=${pid})"
        kill -9 "$pid" 2>/dev/null || true
        break
      fi
      sleep 1
    done
  else
    echo "[clean] ${name} stale pid=${pid}"
  fi

  rm -f "$pid_file"
}

stop_service "doh"
stop_service "agent"
stop_service "webserver"

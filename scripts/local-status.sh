#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
RUN_DIR="${ROOT_DIR}/.run"
LOG_DIR="${ROOT_DIR}/logs"

print_service() {
  name=$1
  pid_file="${RUN_DIR}/${name}.pid"
  log_file="${LOG_DIR}/${name}.log"

  if [ ! -f "$pid_file" ]; then
    echo "${name}: stopped"
    return 0
  fi

  pid=$(cat "$pid_file" 2>/dev/null || true)
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    echo "${name}: running (pid=${pid}) log=${log_file}"
  else
    echo "${name}: stale pid file (${pid_file})"
  fi
}

print_service "webserver"
print_service "agent"
print_service "doh"

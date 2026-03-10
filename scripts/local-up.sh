#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
LOG_DIR="${ROOT_DIR}/logs"
RUN_DIR="${ROOT_DIR}/.run"
STAMP=$(date '+%Y%m%d-%H%M%S')

mkdir -p "$LOG_DIR" "$RUN_DIR"

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
  (
    cd "$workdir"
    nohup sh -lc "$command" >>"$log_file" 2>&1 &
    echo "$!" >"$pid_file"
  )
  ln -sfn "$(basename "$log_file")" "$latest_link"
  echo "        log: ${log_file}"
}

start_service "webserver" "${ROOT_DIR}/webserver" "./gradlew bootRun"
start_service "agent" "${ROOT_DIR}/Agent" "uv run main.py"
start_service "doh" "${ROOT_DIR}/DoH" \
  "DOH_LISTEN_PORT=\${DOH_LISTEN_PORT:-8443} DOH_ANALYTICS_EP=\${DOH_ANALYTICS_EP:-localhost:50051} \${DOH_BINARY:-./build/doh-forwarder}"

echo
echo "Started services. Use 'sh scripts/local-status.sh' to inspect status."

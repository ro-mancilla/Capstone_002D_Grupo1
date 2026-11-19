#!/usr/bin/env bash
#
# demo-down.sh -- stop what demo-up.sh started. Data is kept.
#
# This is `docker compose stop`, never `down -v`: the volumes hold the Postgres data and the
# Azurite blobs, which is where every uploaded product photo lives.
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
RUN_DIR="${ECOPAN_DEMO_HOME:-$HOME/.ecopan-demo}/run"
DESKTOP_DATA="$HOME/Library/Application Support/Ecopan"

stop_pid() { # stop_pid <name>
  local f="$RUN_DIR/$1.pid"
  [ -f "$f" ] || return 0
  local pid; pid="$(cat "$f")"
  if kill -0 "$pid" 2>/dev/null; then
    pkill -P "$pid" 2>/dev/null || true   # children first: npm/gradle spawn the real process
    kill "$pid" 2>/dev/null || true
    echo "stopped $1 (pid $pid)"
  fi
  rm -f "$f"
}

stop_pid web
stop_pid desktop
stop_pid emulator

# The till itself is a JVM that gradle launched; find it by the files it holds open.
if [ -d "$DESKTOP_DATA" ]; then
  pids="$(lsof -t +D "$DESKTOP_DATA" 2>/dev/null | sort -u)"
  [ -n "$pids" ] && kill $pids 2>/dev/null && echo "stopped desktop till"
fi

command -v adb >/dev/null 2>&1 && adb emu kill >/dev/null 2>&1 && echo "stopped emulator"

docker compose stop
echo "stack stopped (data kept). Start again with scripts/demo-up.sh --no-reset"

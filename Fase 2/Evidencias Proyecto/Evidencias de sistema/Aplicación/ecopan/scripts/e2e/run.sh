#!/usr/bin/env bash
#
# End-to-end suites on the THROWAWAY acceptance stack. Never touches the developer's stack, ports or volumes.
#
#   scripts/e2e/run.sh web|till|android|ios|all [--no-build] [--keep] [-- <test runner args>]
#
# It brings up the acceptance compose project (ecopan-acc, host ports 18080/18000/15432/15673/11000,
# volumes ecopan-acc_*), seeds it with the reference seed plus the demo steps (opening stock, forecasting
# on), builds the dashboard against its API, serves it on :14173 and runs the Playwright suite in a real
# browser. On failure it keeps screenshots, traces and the service logs under $ECOPAN_E2E_ARTIFACTS
# (default ./build/e2e-artifacts). --keep leaves the stack up; --no-build reuses the images.
# Teardown removes ONLY volumes this project created. Needs docker compose, python3, curl, node.

set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

PROJECT="ecopan-acc"
API="http://localhost:18080"
DASH_PORT=14173
export ECOPAN_E2E_API="$API" ECOPAN_E2E_DASHBOARD="http://localhost:$DASH_PORT"
export ECOPAN_E2E_ARTIFACTS="${ECOPAN_E2E_ARTIFACTS:-$ROOT/build/e2e-artifacts}"
DC=(docker compose -p "$PROJECT" -f docker-compose.yml -f scripts/acceptance/compose.yml -f scripts/e2e/compose.yml)

SUITE="${1:-}"; [ -n "$SUITE" ] || { sed -n '2,/^set -E/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 2; }
case "$SUITE" in -h|--help) sed -n '2,/^set -E/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;; web|till|android|ios|all) ;; *) echo "unknown suite: $SUITE (web, till, android, ios, all)" >&2; exit 2 ;; esac
if [ "$SUITE" = all ]; then
  # Every suite in turn on its own fresh stack.
  shift; code=0
  for suite in web till android ios; do "$0" "$suite" "$@" || code=$?; done
  exit "$code"
fi
shift
BUILD=true KEEP=false PW_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) BUILD=false ;;
    --keep) KEEP=true ;;
    --) shift; PW_ARGS=("$@"); break ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

if [ -t 1 ]; then B=$'\033[1m'; G=$'\033[32m'; R=$'\033[31m'; Z=$'\033[0m'; else B= G= R= Z=; fi
step() { printf '\n%s==> %s%s\n' "$B" "$*" "$Z"; }
pass() { printf '  %s✓%s %s\n' "$G" "$Z" "$*"; }
die()  { printf '\n%s✗ %s%s\n' "$R" "$*" "$Z" >&2; exit 1; }

# ---- isolation guards ------------------------------------------------------------------------------
[ "$PROJECT" != "ecopan" ] || die "internal: the project must not be the dev project"
if [ -n "${COMPOSE_PROJECT_NAME:-}" ] && [ "$COMPOSE_PROJECT_NAME" != "$PROJECT" ]; then
  die "COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT_NAME is set; unset it (the e2e runner always uses $PROJECT)"
fi
[ -z "${ECOPAN_E2E_API_OVERRIDE:-}" ] || die "refusing an API override: the suites only ever run against the throwaway stack"
docker info >/dev/null 2>&1 || die "the docker daemon is not running"
[ -f backend/app/.env ] || { cp backend/app/.env.example backend/app/.env; echo "  created backend/app/.env from .env.example"; }
for p in 18080 18000 15432 15673 25672 11000 18999 "$DASH_PORT"; do
  if lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1 && [ -z "$("${DC[@]}" ps -q 2>/dev/null)" ]; then
    die "port $p is in use by something that is not this runner's stack"
  fi
done

PREVIEW_PID=""
teardown() {
  # Only volumes labelled with this project, and only if every one of them carries its name prefix.
  local vols; vols="$(docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT")"
  for v in $vols; do [[ "$v" == "$PROJECT"_* ]] || die "refusing to remove volume '$v': not owned by $PROJECT"; done
  "${DC[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
}
cleanup() {
  local code=$?
  [ -z "$PREVIEW_PID" ] || [ "$KEEP" = true ] || kill "$PREVIEW_PID" 2>/dev/null || true
  if [ "$code" -ne 0 ]; then
    mkdir -p "$ECOPAN_E2E_ARTIFACTS"; "${DC[@]}" logs --no-color > "$ECOPAN_E2E_ARTIFACTS/compose.log" 2>&1 || true
    printf '%s  screenshots, traces and service logs: %s%s\n' "$R" "$ECOPAN_E2E_ARTIFACTS" "$Z" >&2
  fi
  if [ "$KEEP" = true ]; then
    echo "  --keep: stack left up ($API, dashboard http://localhost:$DASH_PORT, preview pid $PREVIEW_PID). Remove: kill $PREVIEW_PID; docker compose -p $PROJECT down -v"
  else
    teardown
  fi
  exit "$code"
}
trap cleanup EXIT

# ---- the stack ----------------------------------------------------------------------------------------
step "Starting the throwaway stack ($PROJECT)"
teardown
[ "$BUILD" = false ] || "${DC[@]}" build
"${DC[@]}" up -d
for i in $(seq 1 90); do curl -fsS "$API/health" >/dev/null 2>&1 && break; sleep 4; [ "$i" -lt 90 ] || die "backend did not become healthy in 6 minutes"; done
pass "backend healthy"
python3 scripts/demo-seed.py opening-stock "$API" reference
python3 scripts/demo-seed.py forecast "$API" reference
pass "seeded: reference tenant, opening stock, forecasting on"

# ---- the desktop till suite -------------------------------------------------------------------------
if [ "$SUITE" = till ]; then
  step "Compose UI tests: the real till app against $API"
  rm -rf "$ECOPAN_E2E_ARTIFACTS/till"
  # The backend address is a build-time constant of the till (BuildKonfig), so it is passed to Gradle. The tests
  # run the app with a throwaway home directory: they never read or write the developer's till data.
  ./gradlew :composeApp:jvmTest --tests "com.f776.ecopan.e2e.*" -Pecopan.backendUrl="$API" -Pecopan.e2e=true --rerun \
    ${PW_ARGS[@]+"${PW_ARGS[@]}"}
  pass "till e2e passed"
  exit 0
fi

# ---- the baker app on an Android emulator -----------------------------------------------------------
if [ "$SUITE" = android ]; then
  ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  ADB="$ANDROID_HOME/platform-tools/adb"
  [ -x "$ADB" ] || die "adb not found under $ANDROID_HOME (set ANDROID_HOME)"
  EMULATOR_PID=""
  if ! "$ADB" devices | grep -q 'emulator-.*device$'; then
    AVD="${ECOPAN_E2E_AVD:-Medium_Phone}"
    step "Booting the emulator ($AVD)"
    "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-snapshot-save -no-audio -no-boot-anim >"$ECOPAN_E2E_ARTIFACTS/emulator.log" 2>&1 & EMULATOR_PID=$!
    "$ADB" wait-for-device
    for i in $(seq 1 90); do [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 5; [ "$i" -lt 90 ] || die "the emulator did not boot"; done
    pass "emulator booted"
  fi
  trap '"$ADB" reverse --remove tcp:8080 >/dev/null 2>&1 || true; "$ADB" reverse --remove tcp:18999 >/dev/null 2>&1 || true; [ -z "${CONTROL_PID:-}" ] || kill "$CONTROL_PID" 2>/dev/null || true; [ -z "$EMULATOR_PID" ] || [ "$KEEP" = true ] || "$ADB" emu kill >/dev/null 2>&1 || true; cleanup' EXIT
  step "Instrumented tests: the real baker app on the emulator, reaching $API over adb reverse"
  rm -rf "$ECOPAN_E2E_ARTIFACTS/android"; mkdir -p "$ECOPAN_E2E_ARTIFACTS/android"
  "$ADB" shell rm -rf /sdcard/Download/ecopan-e2e
  # A phone that kept a previous run's session (tokens of a stack that is gone) answers every push with 401: start from
  # an empty one. Only the e2e test packages, never the developer's own debug app.
  for pkg in $("$ADB" shell pm list packages | tr -d '\r' | sed -n 's/^package://p' | grep -E '\.mobile\.e2e'); do
    "$ADB" uninstall "$pkg" >/dev/null 2>&1 || true
  done
  # The debug app's backend is localhost:8080 (a build-time default); on the phone that is the throwaway stack.
  "$ADB" reverse tcp:8080 tcp:18080
  # Fault control for the journeys (stop/start the API, SQL): the emulator reaches it over adb reverse too.
  python3 scripts/e2e/control.py & CONTROL_PID=$!
  "$ADB" reverse tcp:18999 tcp:18999
  status=0
  ./gradlew :mobile:e2e:connectedAndroidDeviceTest -Pecopan.e2e=true ${PW_ARGS[@]+"${PW_ARGS[@]}"} || status=$?
  "$ADB" pull /sdcard/Download/ecopan-e2e/. "$ECOPAN_E2E_ARTIFACTS/android/" >/dev/null 2>&1 || true
  # What each failing test said (the UI hierarchy at the failure is in its message).
  [ "$status" -eq 0 ] || cp -R mobile/e2e/build/outputs/androidTest-results "$ECOPAN_E2E_ARTIFACTS/android/results" 2>/dev/null || true
  [ "$status" -eq 0 ] || die "android e2e failed (screenshots and UI hierarchy: $ECOPAN_E2E_ARTIFACTS/android)"
  pass "android e2e passed"
  exit 0
fi

# ---- the baker app on the iOS simulator ---------------------------------------------------------------
if [ "$SUITE" = ios ]; then
  # The simulator shares the Mac's network, so the stack and the control server are plain localhost.
  python3 scripts/e2e/control.py & CONTROL_PID=$!
  trap '[ -z "${CONTROL_PID:-}" ] || kill "$CONTROL_PID" 2>/dev/null || true; cleanup' EXIT
  step "Kotlin/Native tests: the real baker app on the iOS simulator, against $API"
  # The same journeys as the Android run (mobile/e2e commonTest); the app's backend is the build-time default
  # (localhost:8080), so point the build at the throwaway stack.
  scripts/e2e/ios-stubs.sh
  SIMCTL_CHILD_ECOPAN_E2E_API="$API" ./gradlew :mobile:e2e:iosSimulatorArm64Test -Pecopan.e2e=true -Pecopan.backendUrl="$API" --rerun \
    ${PW_ARGS[@]+"${PW_ARGS[@]}"}
  pass "ios e2e passed"
  exit 0
fi

# ---- the web suite ----------------------------------------------------------------------------------
step "Dashboard build against $API"
( cd webApp && [ -d node_modules ] || npm ci --silent
  VITE_API_BASE_URL="$API" VITE_DEV_QUICK_LOGIN=false npx vite build --outDir "$ROOT/build/e2e-web-dist" --emptyOutDir >/dev/null )
( cd webApp && exec node node_modules/vite/bin/vite.js preview --outDir "$ROOT/build/e2e-web-dist" --port "$DASH_PORT" --strictPort >/dev/null 2>&1 ) & PREVIEW_PID=$!
for i in $(seq 1 30); do curl -fsS "http://localhost:$DASH_PORT/" >/dev/null 2>&1 && break; sleep 1; [ "$i" -lt 30 ] || die "the dashboard did not start"; done
pass "dashboard served on :$DASH_PORT"

step "Playwright (Chromium)"
( cd e2e/web && [ -d node_modules ] || npm ci --silent
  npx playwright install chromium webkit >/dev/null
  npx playwright test ${PW_ARGS[@]+"${PW_ARGS[@]}"} )
pass "web e2e passed (report: $ECOPAN_E2E_ARTIFACTS/web/report)"

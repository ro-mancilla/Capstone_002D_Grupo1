#!/usr/bin/env bash
#
# prod-up.sh -- deploy to Azure if needed, then build the PRODUCTION clients against it and launch them.
#
#   deploy (only if nothing is deployed yet)  ->  release Android baker app (signed, R8-minified,
#   baseline profile) on the emulator  ->  release desktop till (below)
#   release desktop till (ProGuard-minified, packaged .app)            ->  launched
#
# The counterpart of scripts/demo-up.sh for a deployment: demo-up.sh runs the dev builds against the
# local docker stack, this runs the release builds against Azure.
#
# DEPLOYING: if resource group rg-ecopan already holds a healthy deployment, it is left alone ("already
# deployed") and only the clients are built. If there is none (or it is not answering), infra/deploy.sh
# runs first (~25 min: it copies the local catalog and photos, so the local `db` and `storage` containers
# are started; nothing local is modified). With --backend-url it never deploys: that backend is used as is.
#
# Usage:  scripts/prod-up.sh [flags]
#   --backend-url URL   use this backend as it is, never deploy (default: $ECOPAN_BACKEND_URL, else the
#                       web app in resource group rg-ecopan, via az)
#   --check             say whether a healthy deployment exists, then stop (exit 0 yes, 1 no)
#   --redeploy          run deploy.sh even if already deployed (it is idempotent)
#   --no-deploy         never deploy: fail if nothing is up
#   -- ARGS...          everything after `--` goes to infra/deploy.sh (e.g. -- --skip-build)
#   --no-android        do not build/install the baker app
#   --no-desktop        do not build/launch the till
#   --keep-state        keep the clients' local state (default: each enrols fresh, the old state is
#                       moved to ~/.ecopan-demo/backups/ for the desktop and cleared for the app)
#   --no-quick-login    build WITHOUT the one-tap demo sign-in (a real release; you type credentials)
#   -h | --help         this text
#
# Quick-login is ON by default here because this is the demo path: the build carries the seeded demo
# passwords, so it is for your own emulator and desk, never for distribution. Android is signed with the
# throwaway demo key (scripts/make-demo-keystore.sh); a real release goes through the release workflow.
# Environment: ECOPAN_AVD (default Medium_Phone), ANDROID_HOME, ECOPAN_DEMO_HOME, ECOPAN_RG (rg-ecopan).
#
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEMO_HOME="${ECOPAN_DEMO_HOME:-$HOME/.ecopan-demo}"
LOG_DIR="$DEMO_HOME/logs"
BACKUP_DIR="$DEMO_HOME/backups/prod-$(date +%Y%m%d-%H%M%S)"
DESKTOP_DATA="$HOME/Library/Application Support/Ecopan"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVD_NAME="${ECOPAN_AVD:-Medium_Phone}"
ANDROID_PKG="com.f776.ecopan"
RG="${ECOPAN_RG:-rg-ecopan}"
BACKEND_URL="${ECOPAN_BACKEND_URL:-}"
APK="android/app/build/outputs/apk/release/app-release.apk"
APP_BUNDLE="jvm/app/build/compose/binaries/main-release/app/EcoPan.app"

DO_ANDROID=1 DO_DESKTOP=1 KEEP_STATE=0 QUICK_LOGIN=true
CHECK_ONLY=0 REDEPLOY=0 NO_DEPLOY=0 DEPLOY_ARGS=()
EXPLICIT_URL=0; [ -n "$BACKEND_URL" ] && EXPLICIT_URL=1
while [ $# -gt 0 ]; do
  case "$1" in
    --backend-url)    BACKEND_URL="${2%/}"; EXPLICIT_URL=1; shift 2 ;;
    --check)          CHECK_ONLY=1; shift ;;
    --redeploy)       REDEPLOY=1; shift ;;
    --no-deploy)      NO_DEPLOY=1; shift ;;
    --)               shift; DEPLOY_ARGS=("$@"); break ;;
    --no-android)     DO_ANDROID=0; shift ;;
    --no-desktop)     DO_DESKTOP=0; shift ;;
    --keep-state)     KEEP_STATE=1; shift ;;
    --no-quick-login) QUICK_LOGIN=false; shift ;;
    -h|--help)        sed -n '2,/^set -E/p' "${BASH_SOURCE[0]}" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown flag: $1 (try --help)" >&2; exit 2 ;;
  esac
done
mkdir -p "$LOG_DIR"

if [ -t 1 ]; then B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; Z=$'\033[0m'; else B= G= Y= R= Z=; fi
step() { printf '\n%s==> %s%s\n' "$B" "$*" "$Z"; }
ok()   { printf '  %s✓%s %s\n' "$G" "$Z" "$*"; }
warn() { printf '  %s!%s %s\n' "$Y" "$Z" "$*"; }
die()  { printf '\n%s✗ %s%s\n' "$R" "$*" "$Z" >&2; exit 1; }
trap 'die "failed at line $LINENO (command: $BASH_COMMAND)"' ERR
have() { command -v "$1" >/dev/null 2>&1; }
wait_for() { local what="$1" timeout="$2"; shift 2; local i=0
  until "$@" >/dev/null 2>&1; do i=$((i + 2)); [ "$i" -ge "$timeout" ] && die "timed out after ${timeout}s waiting for: $what"; sleep 2; done; }

# ---- 0. the backend: deploy only if there is none ------------------------------------------
step "Backend"
resolve_url() { # sets BACKEND_URL from the web app in the resource group, if there is one
  have az || return 1
  [ "$(az group exists -n "$RG" 2>/dev/null)" = true ] || return 1
  local host; host="$(az webapp list -g "$RG" --query "[?ends_with(name, '-app')].defaultHostName | [0]" -o tsv 2>/dev/null || true)"
  [ -n "$host" ] && BACKEND_URL="https://$host"
}
is_up() { [ -n "$BACKEND_URL" ] && curl -fsS --max-time 15 "$BACKEND_URL/health" >/dev/null 2>&1; }

[ "$EXPLICIT_URL" = 1 ] || resolve_url || true
case "${BACKEND_URL:-}" in ""|https://*) ;; *) die "the backend must be https:// (got $BACKEND_URL): a release build refuses cleartext" ;; esac

if [ "$CHECK_ONLY" = 1 ]; then
  if is_up; then ok "deployed and healthy: $BACKEND_URL"; exit 0; fi
  warn "no healthy deployment${BACKEND_URL:+ at $BACKEND_URL}"; exit 1
fi

if is_up && [ "$REDEPLOY" = 0 ]; then
  ok "already deployed, skipping the deploy: $BACKEND_URL"
elif [ "$EXPLICIT_URL" = 1 ]; then
  is_up || die "$BACKEND_URL/health did not answer (an explicit --backend-url is never deployed for you)"
  ok "using $BACKEND_URL"
elif [ "$NO_DEPLOY" = 1 ]; then
  die "nothing is deployed in $RG and --no-deploy was given. Run ./infra/deploy.sh, or drop --no-deploy."
else
  [ -n "${BACKEND_URL:-}" ] && warn "a deployment exists but is not answering: running deploy.sh (idempotent)" || warn "nothing deployed in $RG: running infra/deploy.sh (about 25 minutes)"
  # The import reads the local catalog and photos from the local db and storage containers.
  have docker && docker info >/dev/null 2>&1 && docker compose up -d db storage >/dev/null 2>&1 || warn "local docker is not available: deploy.sh will refuse the import (pass -- --no-import to deploy without the local data)"
  "$ROOT/infra/deploy.sh" "${DEPLOY_ARGS[@]+"${DEPLOY_ARGS[@]}"}" || die "infra/deploy.sh failed"
  resolve_url || die "deployed, but could not find the web app in $RG"
  is_up || die "deployed, but $BACKEND_URL/health does not answer"
  ok "deployed: $BACKEND_URL"
fi
build="$(curl -s -D - -o /dev/null --max-time 15 "$BACKEND_URL/health" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-build-id"{print $2}')"
ok "backend up${build:+, build $build}"

GRADLE_FLAGS=(-Pecopan.backendUrl="$BACKEND_URL" -Pecopan.devQuickLogin="$QUICK_LOGIN")
[ "$QUICK_LOGIN" = true ] && warn "quick-login is ON: this build carries the demo sign-in. Local use only."

# ---- 1. Android ---------------------------------------------------------------------------
if [ "$DO_ANDROID" = 1 ]; then
  step "Android baker app (release)"
  have adb || die "adb not found on PATH (or pass --no-android)"
  eval "$(scripts/make-demo-keystore.sh 2>/dev/null)"
  ./gradlew --console=plain -q :android:app:assembleRelease "${GRADLE_FLAGS[@]}" -Pecopan.requireSigning=true \
    >"$LOG_DIR/prod-android-build.log" 2>&1 || { tail -25 "$LOG_DIR/prod-android-build.log"; die "assembleRelease failed (log: $LOG_DIR/prod-android-build.log)"; }
  scripts/verify-release.sh --apk "$APK" --expect-url "$BACKEND_URL" >"$LOG_DIR/prod-android-verify.log" 2>&1 \
    || { cat "$LOG_DIR/prod-android-verify.log"; die "the built APK failed scripts/verify-release.sh"; }
  ok "built and verified $APK (signed, minified, points at $BACKEND_URL)"

  serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
  if [ -z "$serial" ]; then
    emu="$ANDROID_HOME/emulator/emulator"
    [ -x "$emu" ] || die "no device attached and no emulator at $emu (set ANDROID_HOME, or pass --no-android)"
    "$emu" -list-avds | grep -qx "$AVD_NAME" || die "AVD '$AVD_NAME' not found (set ECOPAN_AVD)"
    warn "no device attached: booting emulator '$AVD_NAME'"
    nohup "$emu" -avd "$AVD_NAME" -no-boot-anim >"$LOG_DIR/emulator.log" 2>&1 &
    wait_for "emulator in adb" 180 bash -c "adb devices | awk 'NR>1 && \$2==\"device\"' | grep -q ."
    serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
    wait_for "emulator boot" 240 bash -c "[ \"\$(adb -s $serial shell getprop sys.boot_completed | tr -d '\r')\" = 1 ]"
  fi
  export ANDROID_SERIAL="$serial"
  # A different signing key (dev debug vs demo key) refuses an in-place update, and a fresh enrolment needs
  # clean state anyway.
  if [ "$KEEP_STATE" = 0 ]; then adb uninstall "$ANDROID_PKG" >/dev/null 2>&1 || true; fi
  adb install -r "$APK" >/dev/null || { adb uninstall "$ANDROID_PKG" >/dev/null 2>&1 || true; adb install "$APK" >/dev/null; }
  adb shell monkey -p "$ANDROID_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  ok "installed and launched on $serial"
fi

# ---- 2. desktop till ----------------------------------------------------------------------
if [ "$DO_DESKTOP" = 1 ]; then
  step "Desktop till (release, minified)"
  [ "$(uname -s)" = Darwin ] || die "this script launches the macOS app bundle; on other systems build packageReleaseMsi/Deb (docs/RELEASE.md)"
  ./gradlew --console=plain -q :jvm:app:createReleaseDistributable "${GRADLE_FLAGS[@]}" \
    >"$LOG_DIR/prod-desktop-build.log" 2>&1 || { tail -25 "$LOG_DIR/prod-desktop-build.log"; die "createReleaseDistributable failed (log: $LOG_DIR/prod-desktop-build.log)"; }
  [ -d "$APP_BUNDLE" ] || die "expected $APP_BUNDLE after the build"
  name="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleName' "$APP_BUNDLE/Contents/Info.plist")"
  [ "$name" = EcoPan ] || die "the bundle is named '$name', expected EcoPan"
  ok "built $APP_BUNDLE"

  pkill -f "$APP_BUNDLE" 2>/dev/null || true
  if [ -d "$DESKTOP_DATA" ] && [ "$KEEP_STATE" = 0 ]; then
    pids="$(lsof -t +D "$DESKTOP_DATA" 2>/dev/null | sort -u || true)"
    [ -z "$pids" ] || { kill $pids 2>/dev/null || true; sleep 2; }
    mkdir -p "$BACKUP_DIR" && mv "$DESKTOP_DATA" "$BACKUP_DIR/desktop-data"
    ok "previous local state moved to $BACKUP_DIR/desktop-data"
  fi
  open -n "$APP_BUNDLE"
  wait_for "the till to start" 30 pgrep -f "$APP_BUNDLE/Contents"
  sleep 12
  pgrep -f "$APP_BUNDLE/Contents" >/dev/null || die "the till exited within 12 s of launching (try: open -n $APP_BUNDLE; log in Console)"
  ok "till running"
fi

step "Ready"
cat <<EOF2
  Backend     $BACKEND_URL
  Dashboard   open the dashboard URL printed by infra/deploy.sh (login: Panadería = reference, gerente / gerente123)

  Enrol the devices once, online:
    Desktop till   cajero    / cajero123     PIN 1111
    Android baker  panadero  / panadero123   PIN 2222
EOF2

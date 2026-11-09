#!/usr/bin/env bash
#
# demo-up.sh -- bring the whole ecopan stack up on clean demo data, then launch every client.
#
#   docker stack  ->  clean transactional data  ->  web dashboard  ->  Android baker app  ->  desktop till
#
# What "clean" means here, and what it deliberately does NOT do
# --------------------------------------------------------------
# It never runs `docker compose down -v`. Product photos live in two places -- the `azurite`
# volume (the blobs) and `products.image_url` in Postgres (the pointer) -- so dropping either
# volume loses every photo a person uploaded. Instead the reset is selective:
#
#   KEPT     tenant, branches, users, ingredients, products (incl. image_url, origin, reorder
#            level), product types, formulas, role permissions, the Azurite volume (all photos),
#            and the Almacén OPENING_COUNT rows of product_stock_movements
#   CLEARED  sales, sale_lines, production_batches, stock_movements, the other product_stock_movements
#            (purchases, adjustments), forecasts, refresh_tokens, and the RabbitMQ queues
#   RESEEDED the 120-day sales history, on the next backend boot (the seed expander shifts dates
#            forward by whole weeks, so weekdays stay aligned; it ends up to 6 days before today)
#   ADDED    opening stock (one PURCHASE per ingredient per branch) so batches don't read negative
#   ALMACÉN  resold products (drinks, packaged cookies) get their stock from an OPENING_COUNT per
#            branch. Those rows are kept by the purge (so hand-made Almacén products stay tracked)
#            and the seed re-appends its own at every boot under deterministic ids, so after a reset
#            each product is back at its opening count: sales are cleared, hence nothing is sold down.
#            The script then verifies every Almacén product has a count in every branch.
#
# On the devices, local state (Room DB, tokens, device id) is wiped so each one enrols fresh:
# the desktop's ~/Library/Application Support/Ecopan is MOVED into the backup folder, and the
# Android app is `pm clear`ed.
#
# Before anything is cleared, a backup lands in  ~/.ecopan-demo/backups/<timestamp>/ :
#   db.sql          pg_dump of the whole database (restore: see README of this script's end)
#   azurite.tar     the full blob volume (photos)
#   desktop-data/   the desktop client's previous local state (if it existed)
#
# Usage:  scripts/demo-up.sh [flags]
#   --no-build      reuse existing docker images (fast; only safe if the code has not changed)
#   --no-reset      keep all data as it is (no purge, no client wipe) -- just start everything
#   --no-forecast   do not enable DEMAND_FORECASTING / prime the forecast
#   --no-web        do not start the Vite dashboard
#   --no-android    do not start the emulator / install the baker app
#   --no-desktop    do not launch the desktop till
#   -h | --help     this text
#
# Environment overrides:  ECOPAN_AVD (emulator name, default Medium_Phone), ANDROID_HOME,
#                         ECOPAN_DEMO_HOME (default ~/.ecopan-demo)
#
# Restoring a backup (undo the reset):
#   docker compose stop web worker notifier
#   docker compose exec -T db psql -U postgres -d ecopan < ~/.ecopan-demo/backups/<ts>/db.sql
#   docker compose up -d
#
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEMO_HOME="${ECOPAN_DEMO_HOME:-$HOME/.ecopan-demo}"
RUN_DIR="$DEMO_HOME/run"
LOG_DIR="$DEMO_HOME/logs"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$DEMO_HOME/backups/$STAMP"
DESKTOP_DATA="$HOME/Library/Application Support/Ecopan"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVD_NAME="${ECOPAN_AVD:-Medium_Phone}"
ANDROID_PKG="com.f776.ecopan"

API="http://localhost:8080"
WEB_URL="http://localhost:5173"
TENANT_SLUG="reference"

DO_BUILD=1 DO_RESET=1 DO_FORECAST=1 DO_WEB=1 DO_ANDROID=1 DO_DESKTOP=1
for arg in "$@"; do
  case "$arg" in
    --no-build)    DO_BUILD=0 ;;
    --no-reset)    DO_RESET=0 ;;
    --no-forecast) DO_FORECAST=0 ;;
    --no-web)      DO_WEB=0 ;;
    --no-android)  DO_ANDROID=0 ;;
    --no-desktop)  DO_DESKTOP=0 ;;
    -h|--help)     sed -n '2,/^set -E/p' "${BASH_SOURCE[0]}" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown flag: $arg (try --help)" >&2; exit 2 ;;
  esac
done

mkdir -p "$RUN_DIR" "$LOG_DIR" "$BACKUP_DIR"

# ---- output helpers -----------------------------------------------------------------------
if [ -t 1 ]; then B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; D=$'\033[2m'; Z=$'\033[0m'; else B= G= Y= R= D= Z=; fi
step() { printf '\n%s==> %s%s\n' "$B" "$*" "$Z"; }
ok()   { printf '  %s✓%s %s\n' "$G" "$Z" "$*"; }
warn() { printf '  %s!%s %s\n' "$Y" "$Z" "$*"; }
die()  { printf '\n%s✗ %s%s\n' "$R" "$*" "$Z" >&2; exit 1; }
trap 'die "failed at line $LINENO (command: $BASH_COMMAND)"' ERR

have() { command -v "$1" >/dev/null 2>&1; }
psql_db() { docker compose exec -T db psql -U postgres -d ecopan -v ON_ERROR_STOP=1 -X -q "$@"; }

wait_for() { # wait_for <description> <timeout-seconds> <command...>
  local what="$1" timeout="$2"; shift 2
  local i=0
  until "$@" >/dev/null 2>&1; do
    i=$((i + 2)); [ "$i" -ge "$timeout" ] && die "timed out after ${timeout}s waiting for: $what"
    sleep 2
  done
}

# ---- 0. preflight -------------------------------------------------------------------------
step "Preflight"
have docker || die "docker not found"
have python3 || die "python3 not found (used to parse JSON)"
[ -x ./gradlew ] || die "run from the repo (./gradlew missing)"

if [ ! -f backend/app/.env ]; then
  cp backend/app/.env.example backend/app/.env
  ok "created backend/app/.env from .env.example"
fi
if [ "$DO_WEB" = 1 ]; then
  have npm || die "npm not found (needed for the web dashboard; or pass --no-web)"
  [ -f webApp/.env ] || { cp webApp/.env.example webApp/.env; ok "created webApp/.env"; }
fi
if [ "$DO_ANDROID" = 1 ]; then
  have adb || die "adb not found on PATH (or pass --no-android)"
fi

if ! docker info >/dev/null 2>&1; then
  warn "docker daemon is not running -- starting it"
  if have orbctl; then orbctl start >/dev/null 2>&1 || true; else open -a Docker >/dev/null 2>&1 || true; fi
  wait_for "docker daemon" 120 docker info
fi
ok "docker $(docker info --format '{{.ServerVersion}}')"

# Ports the stack needs. A leftover local process on one of them fails in confusing ways later.
for p in 5173; do
  if [ "$DO_WEB" = 1 ] && lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1 && [ ! -f "$RUN_DIR/web.pid" ]; then
    die "port $p is taken by another process. The backend's CORS only allows :5173 -- free it (lsof -nP -iTCP:$p) and re-run."
  fi
done

# ---- 1. data reset (docker side) ----------------------------------------------------------
if [ "$DO_RESET" = 1 ]; then
  step "Backing up, then clearing transactional data (catalog + photos are kept)"

  docker compose up -d db storage >/dev/null
  wait_for "postgres healthy" 90 docker compose exec -T db pg_isready -U postgres
  # Stop everything that writes, so the purge cannot race a running worker or a syncing client.
  docker compose stop web worker notifier >/dev/null 2>&1 || true

  if [ "$(psql_db -Atc "select to_regclass('public.products') is not null")" = "t" ]; then
    docker compose exec -T db pg_dump -U postgres --clean --if-exists ecopan > "$BACKUP_DIR/db.sql"
    docker compose exec -T storage tar -C /data -cf - . > "$BACKUP_DIR/azurite.tar"
    ok "backup written to $BACKUP_DIR ($(du -sh "$BACKUP_DIR" | cut -f1))"

    photos_before="$(psql_db -Atc "select count(*) from products where image_url is not null")"
    products_before="$(psql_db -Atc "select count(*) from products")"

    psql_db < "$ROOT/scripts/demo-purge.sql"

    photos_after="$(psql_db -Atc "select count(*) from products where image_url is not null")"
    products_after="$(psql_db -Atc "select count(*) from products")"
    [ "$photos_before" = "$photos_after" ] && [ "$products_before" = "$products_after" ] \
      || die "catalog changed during reset ($products_before/$photos_before -> $products_after/$photos_after products/photos). Restore from $BACKUP_DIR/db.sql"
    ok "cleared sales, batches, ingredient movements, product purchases/adjustments, forecasts, sessions"
    ok "catalog intact: $products_after products, $photos_after with a photo"
  else
    ok "empty database -- nothing to clear"
  fi

  # Queued jobs from earlier sessions would replay against the fresh data. The broker keeps no
  # state worth keeping, so recreate it (removes its anonymous volume, nothing else).
  docker compose rm -sfv rabbitmq >/dev/null 2>&1 || true
  ok "message broker reset"
fi

# ---- 2. the stack -------------------------------------------------------------------------
step "Starting the docker stack (db, rabbitmq, storage, ml, web, worker x2, notifier)"
if [ "$DO_BUILD" = 1 ]; then
  warn "building images -- first time can take several minutes (skip with --no-build once built)"
  docker compose --progress quiet up -d --build
else
  docker compose up -d
fi
wait_for "backend GET /health" 300 curl -fsS "$API/health"
ok "backend healthy at $API"
wait_for "ml service" 120 curl -fsS http://localhost:8000/health
ok "ml service healthy ($(curl -fsS http://localhost:8000/health | python3 -c 'import sys,json; d=json.load(sys.stdin); print("model_loaded=%s" % d.get("model_loaded", "?"))' 2>/dev/null || echo ok))"

# The seed re-inserts the history at boot; wait until it has landed so the dashboard is not empty.
wait_for "seeded sales history" 120 bash -c "[ \"\$(docker compose exec -T db psql -U postgres -d ecopan -Atc 'select count(*) from sales')\" -gt 1000 ]"
ok "sales history seeded: $(psql_db -Atc "select count(*) from sales") sales, latest $(psql_db -Atc "select max(sold_at)::date from sales")"

# ---- 3a. opening stock --------------------------------------------------------------------
if [ "$DO_RESET" = 1 ]; then
  step "Opening stock"
  # The ledger starts at zero and is append-only, so the first batch would read as *negative*
  # stock on stage. One purchase per ingredient per branch fixes that. Movement ids are derived
  # (uuid5) from branch+ingredient, so re-running the script never double-counts.
  python3 scripts/demo-seed.py opening-stock "$API" "$TENANT_SLUG"
fi

# ---- 3a2. Almacén opening counts ---------------------------------------------------------
step "Almacén opening counts"
# Counts live in product_stock_movements and cannot be created through the API after the fact (a
# product's count is written atomically with the product), so this only verifies. A gap means an
# Almacén product that is untracked in some branch -- fix it in Catálogo -> Productos or reseed.
missing="$(psql_db -Atc "select p.name || ' / ' || b.name from products p join branches b on b.tenant_id = p.tenant_id where p.origin = 'ALMACEN' and p.active and not exists (select 1 from product_stock_movements m where m.product_id = p.id and m.branch_id = b.id and m.kind = 'OPENING_COUNT')")"
if [ -n "$missing" ]; then
  warn "Almacén products without an opening count (untracked there):"; printf '      %s\n' "$missing" >&2
else
  ok "$(psql_db -Atc "select count(*) from products where origin = 'ALMACEN' and active") Almacén products, each with an opening count in every branch"
fi

# ---- 3b. forecast -------------------------------------------------------------------------
if [ "$DO_FORECAST" = 1 ]; then
  step "Enabling DEMAND_FORECASTING and priming the forecast"
  # The reference seed ships with forecasting OFF (the entitlement demo toggles it live), so the
  # forecast page would be hidden. Turn it on here so every module is visible from the start.
  python3 scripts/demo-seed.py forecast "$API" "$TENANT_SLUG"
  # Non-fatal: the forecast page can be refreshed by hand (Pronóstico -> Actualizar) if the worker is slow.
  for _ in $(seq 1 45); do
    [ "$(psql_db -Atc 'select count(*) from forecasts')" -gt 0 ] && break
    sleep 2
  done
  if [ "$(psql_db -Atc 'select count(*) from forecasts')" -gt 0 ]; then
    ok "forecasts written: $(psql_db -Atc "select count(*) || ' rows (' || string_agg(distinct model_version, ', ') || ')' from forecasts")"
  else
    warn "no forecast rows after 90s -- check: docker compose logs worker"
  fi
fi

# ---- 4. web dashboard ---------------------------------------------------------------------
if [ "$DO_WEB" = 1 ]; then
  step "Web dashboard"
  if [ -f "$RUN_DIR/web.pid" ] && kill -0 "$(cat "$RUN_DIR/web.pid")" 2>/dev/null; then
    ok "already running (pid $(cat "$RUN_DIR/web.pid"))"
  else
    [ -d webApp/node_modules ] || { warn "installing npm dependencies"; (cd webApp && npm ci >"$LOG_DIR/npm-install.log" 2>&1); }
    (cd webApp && nohup npm run dev -- --strictPort --host localhost >"$LOG_DIR/web.log" 2>&1 & echo $! >"$RUN_DIR/web.pid")
    wait_for "vite dev server" 60 curl -fsS "$WEB_URL"
    ok "dashboard at $WEB_URL  (log: $LOG_DIR/web.log)"
  fi
  # A stale session from earlier rehearsals lives in that browser's localStorage. A private
  # window sidesteps it; otherwise the wiped refresh token just bounces you to the login page.
  open "$WEB_URL" >/dev/null 2>&1 || true
fi

# ---- 5. Android ---------------------------------------------------------------------------
if [ "$DO_ANDROID" = 1 ]; then
  step "Android baker app"
  serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
  if [ -z "$serial" ]; then
    emu="$ANDROID_HOME/emulator/emulator"
    [ -x "$emu" ] || die "no device attached and no emulator at $emu (set ANDROID_HOME, or pass --no-android)"
    "$emu" -list-avds | grep -qx "$AVD_NAME" || die "AVD '$AVD_NAME' not found. Available: $("$emu" -list-avds | tr '\n' ' ')(set ECOPAN_AVD)"
    warn "no device attached -- booting emulator '$AVD_NAME'"
    nohup "$emu" -avd "$AVD_NAME" -no-boot-anim >"$LOG_DIR/emulator.log" 2>&1 &
    echo $! >"$RUN_DIR/emulator.pid"
    wait_for "emulator to appear in adb" 180 bash -c "adb devices | awk 'NR>1 && \$2==\"device\"' | grep -q ."
    serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
    wait_for "emulator boot to finish" 240 bash -c "[ \"\$(adb -s $serial shell getprop sys.boot_completed | tr -d '\r')\" = 1 ]"
  fi
  export ANDROID_SERIAL="$serial"
  ok "device: $serial"

  # `adb reverse` does not survive an adb restart or a reconnect, so it is re-applied every run.
  adb reverse tcp:8080 tcp:8080 >/dev/null
  adb reverse tcp:10000 tcp:10000 >/dev/null   # product photos are read straight from Azurite
  ok "adb reverse 8080 (API) and 10000 (photos)"

  if [ "$DO_RESET" = 1 ]; then
    adb shell pm clear "$ANDROID_PKG" >/dev/null 2>&1 && ok "app data cleared" || ok "app not installed yet -- nothing to clear"
  fi
  ./gradlew :android:app:installDebug >"$LOG_DIR/android-install.log" 2>&1 \
    || { tail -30 "$LOG_DIR/android-install.log"; die "installDebug failed (full log: $LOG_DIR/android-install.log)"; }
  ok "installed"
  adb shell monkey -p "$ANDROID_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  ok "launched"
fi

# ---- 6. desktop till ----------------------------------------------------------------------
if [ "$DO_DESKTOP" = 1 ]; then
  step "Desktop till"
  # Stop a running till first: it holds a lock on app.db, and wiping under it corrupts the reset.
  if [ -d "$DESKTOP_DATA" ]; then
    pids="$(lsof -t +D "$DESKTOP_DATA" 2>/dev/null | sort -u || true)"
    if [ -n "$pids" ]; then
      warn "a till is already running (pid $(echo $pids | tr '\n' ' ')) -- stopping it"
      kill $pids 2>/dev/null || true; sleep 2
    fi
    if [ "$DO_RESET" = 1 ]; then
      mv "$DESKTOP_DATA" "$BACKUP_DIR/desktop-data"
      ok "previous local state moved to $BACKUP_DIR/desktop-data"
    fi
  fi
  nohup ./gradlew :jvm:app:run >"$LOG_DIR/desktop.log" 2>&1 &
  echo $! >"$RUN_DIR/desktop.pid"
  ok "till launching (first compile can take a minute; log: $LOG_DIR/desktop.log)"
fi

# ---- summary ------------------------------------------------------------------------------
step "Ready"
cat <<EOF
  Dashboard   $WEB_URL          (login: Panadería = $TENANT_SLUG, gerente / gerente123)
  Backend     $API
  RabbitMQ    http://localhost:15672  (guest / guest)
  Backup      $BACKUP_DIR

  Enrol the devices once, online:
    Desktop till   cajero    / cajero123     PIN 1111
    Android baker  panadero  / panadero123   PIN 2222

  Walkthrough:  docs/DEMO.md        Stop everything:  scripts/demo-down.sh
EOF

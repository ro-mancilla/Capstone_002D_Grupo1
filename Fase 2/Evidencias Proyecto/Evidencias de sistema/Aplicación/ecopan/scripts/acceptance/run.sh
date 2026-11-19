#!/usr/bin/env bash
#
# Acceptance suite on a THROWAWAY stack. Never touches the developer's stack, ports or volumes.
#
#   scripts/acceptance/run.sh [--no-build] [--keep] [--release-artifacts]
#
# It brings up its own compose project (ecopan-acc, host ports 18080/18000/15432/15673/11000, volumes
# ecopan-acc_*), seeds it with the same steps the dev and Azure stacks use, then checks:
#
#   1. the verifier FAILS, naming the right checks, on a stack that has not had its demo steps yet;
#   2. after the demo steps, infra/verify.sh passes in full, including the SSE end-to-end path
#      (this is the test that records a real batch -- here it lands in the throwaway database);
#   3. the verifier FAILS, naming the right check, when: the URL is wrong, the ML service is down,
#      an own-storage photo is broken, the notifier is down (no SSE event);
#   4. with --release-artifacts: builds a demo-signed release APK and checks scripts/verify-release.sh
#      accepts it, and rejects a debug build, a stripped signature and a wrong backend URL.
#
# Then it removes the throwaway stack and ONLY its own volumes. On failure it saves the service logs to
# $ECOPAN_ACCEPTANCE_LOGS (default ./build/acceptance-logs) before removing anything; --keep leaves the
# stack up for inspection (remove it with: docker compose -p ecopan-acc down -v).
#
# Needs: docker compose, python3, curl. --release-artifacts also needs the Android SDK (ANDROID_HOME) and a JDK.

set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

PROJECT="ecopan-acc"
API="http://localhost:18080"
ML="http://localhost:18000"
BLOB_MARK=":11000/"
LOGS="${ECOPAN_ACCEPTANCE_LOGS:-$ROOT/build/acceptance-logs}"
DC=(docker compose -p "$PROJECT" -f docker-compose.yml -f scripts/acceptance/compose.yml)

BUILD=true KEEP=false RELEASE=false
for a in "$@"; do
  case "$a" in
    --no-build) BUILD=false ;;
    --keep) KEEP=true ;;
    --release-artifacts) RELEASE=true ;;
    -h|--help) sed -n '2,/^set -E/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $a" >&2; exit 2 ;;
  esac
done

if [ -t 1 ]; then B=$'\033[1m'; G=$'\033[32m'; R=$'\033[31m'; Z=$'\033[0m'; else B= G= R= Z=; fi
step() { printf '\n%s==> %s%s\n' "$B" "$*" "$Z"; }
pass() { printf '  %s✓%s %s\n' "$G" "$Z" "$*"; }
die()  { printf '\n%s✗ %s%s\n' "$R" "$*" "$Z" >&2; exit 1; }

# ---- isolation guards: refuse anything that could reach the dev stack ----------------------------
[ "$PROJECT" != "ecopan" ] || die "internal: the project must not be the dev project"
if [ -n "${COMPOSE_PROJECT_NAME:-}" ] && [ "$COMPOSE_PROJECT_NAME" != "$PROJECT" ]; then
  die "COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT_NAME is set; unset it (the suite always uses $PROJECT)"
fi
docker info >/dev/null 2>&1 || die "the docker daemon is not running"
[ -f backend/app/.env ] || { cp backend/app/.env.example backend/app/.env; echo "  created backend/app/.env from .env.example"; }
for p in 18080 18000 15432 15673 25672 11000; do
  if lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1 && [ -z "$("${DC[@]}" ps -q 2>/dev/null)" ]; then
    die "port $p is in use by something that is not this suite's stack"
  fi
done

teardown() {
  # Only volumes labelled with this project, and only if every one of them carries its name prefix.
  local vols; vols="$(docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT")"
  for v in $vols; do [[ "$v" == "$PROJECT"_* ]] || die "refusing to remove volume '$v': not owned by $PROJECT"; done
  "${DC[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
}

cleanup() {
  local code=$?
  if [ "$code" -ne 0 ]; then
    mkdir -p "$LOGS"; "${DC[@]}" logs --no-color > "$LOGS/compose.log" 2>&1 || true
    printf '%s  service logs saved to %s/compose.log%s\n' "$R" "$LOGS" "$Z" >&2
  fi
  if [ "$KEEP" = true ]; then
    echo "  --keep: stack left up ($API). Remove: docker compose -p $PROJECT down -v"
  else
    teardown
  fi
  exit "$code"
}
trap cleanup EXIT

# expect_fail <description> <regex the output must match> <command...>
expect_fail() {
  local what="$1" pattern="$2"; shift 2
  local out code=0
  out="$("$@" 2>&1)" || code=$?
  [ "$code" -ne 0 ] || { printf '%s\n' "$out" | tail -20; die "expected a FAILURE ($what) but the command passed"; }
  grep -Eq "$pattern" <<<"$out" || { printf '%s\n' "$out" | tail -20; die "failed ($what), but the output does not match /$pattern/"; }
  pass "$what: fails as it should"
}

psql_db() { "${DC[@]}" exec -T db psql -U postgres -d ecopan -v ON_ERROR_STOP=1 -X -q "$@"; }
VERIFY=(infra/verify.sh "$API" --tenant reference --own-storage "$BLOB_MARK" --forecast-wait 150)

# ---- 1. bring the throwaway stack up ------------------------------------------------------------
step "Starting the throwaway stack ($PROJECT)"
teardown   # a previous failed run's leftovers; owned volumes only
if [ "$BUILD" = true ]; then
  if [ -n "${ACCEPTANCE_SEQUENTIAL_BUILD:-}" ]; then
    # Each backend image runs a cold, daemonless Gradle build of the whole monorepo. Compose builds them
    # in parallel, which starves a 2-CPU CI runner (COMPOSE_PARALLEL_LIMIT only applies with Buildx >= 0.38),
    # so build one at a time and say how long each takes.
    for svc in web worker notifier ml; do
      t0=$SECONDS; "${DC[@]}" build "$svc"; echo "  built $svc in $((SECONDS - t0))s"
    done
  else
    "${DC[@]}" build
  fi
fi
"${DC[@]}" up -d
for i in $(seq 1 90); do curl -fsS "$API/health" >/dev/null 2>&1 && break; sleep 4; [ "$i" -lt 90 ] || die "backend did not become healthy in 6 minutes"; done
pass "backend healthy"

# ---- 2. the verifier must notice a stack without its demo steps ---------------------------------
step "Verifier: a stack without its demo steps"
out="$("${VERIFY[@]}" --no-sse --forecast-wait 1 2>&1 || true)"
grep -q "FAIL  opening stock" <<<"$out" || die "the verifier did not report 'opening stock'"
grep -q "FAIL  DEMAND_FORECASTING enabled" <<<"$out" || die "the verifier did not report 'DEMAND_FORECASTING enabled'"
pass "both missing demo steps are named"
"${VERIFY[@]}" --no-sse --forecast-wait 1 >/dev/null 2>&1 && die "the verifier passed on a stack without its demo steps"

# ---- 3. demo steps, then the full verification --------------------------------------------------
step "Demo steps, then the full verification (including SSE)"
python3 scripts/demo-seed.py opening-stock "$API" reference
python3 scripts/demo-seed.py forecast "$API" reference
"${VERIFY[@]}" --ml-url "$ML" --expect-ml-model any
pass "full verification passes"

# ---- 3b. hardening: login rate limit, logs without secrets, dashboard headers --------------------
step "Hardening: login rate limit, request log, dashboard headers"
login_status() { # <X-Forwarded-For value> -> HTTP status of a wrong-password login
  curl -s -o /dev/null -D "$HDRS" -w '%{http_code}' -X POST "$API/auth/login" -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: $1" -d '{"tenantSlug":"reference","username":"nobody","password":"wrong"}'
}
HDRS="$(mktemp)"
limited=""
# Bracketed IPv6 with a fresh port per attempt: each one used to get its own bucket (#129).
for i in $(seq 1 40); do
  code="$(login_status "[2001:db8::50]:$((4000 + i))")"
  if [ "$code" = 429 ]; then limited="$i"; break; fi
  [ "$code" = 401 ] || die "a wrong-password login answered HTTP $code, expected 401"
done
[ -n "$limited" ] || die "40 wrong-password logins from one IPv6 client on changing ports were never throttled"
wait_s="$(grep -i '^retry-after:' "$HDRS" | tr -dc '0-9')"
[ -n "$wait_s" ] || die "the 429 carries no Retry-After header"
pass "attempt $limited from one IPv6 client (a new port each time) got 429 with Retry-After ${wait_s}s"
[ "$(login_status "[2001:db8::51]:4000")" = 429 ] || die "another address in the same IPv6 /64 was not throttled"
pass "the rest of its /64 is throttled with it"
[ "$(login_status "198.51.100.77:4000")" = 401 ] || die "another address was throttled by the first one's attempts"
pass "a different address is not affected"
sleep "$((wait_s + 1))"
login_ok="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -H "X-Forwarded-For: [2001:db8::50]:4999" \
  -d '{"tenantSlug":"reference","username":"gerente","password":"gerente123","deviceId":"acceptance","deviceName":"acceptance"}')"
[ "$login_ok" = 200 ] || die "a correct login from the throttled address after Retry-After answered HTTP $login_ok"
pass "after Retry-After, a correct login from the throttled address works"

LEAK="LEAKCHECK$RANDOM$RANDOM"
curl -s -o /dev/null --max-time 3 "$API/events/sse?access_token=$LEAK" || true
logs="$("${DC[@]}" logs --no-color web 2>&1)"
grep -Eq 'POST /auth/login -> 429 \([0-9]+ ms\)' <<<"$logs" || die "the backend log has no 'POST /auth/login -> 429 (N ms)' line"
grep -qF 'auth rate limit hit by 2001:db8:0:0::/64' <<<"$logs" || die "the backend did not log the rate-limit hit"
pass "the request log has method, path, status and duration, and names the throttled address"
grep -q 'GET /events/sse ->' <<<"$logs" || die "the SSE request is not in the log"
! grep -q "$LEAK" <<<"$logs" || die "the SSE access token (query string) leaked into the backend log"
! grep -Eq 'gerente123|"password"' <<<"$logs" || die "a password reached the log"
rm -f "$HDRS"
pass "no query string, token or password in the log"

# A branch manager reads only their own branch (#127). Branch ids are the reference seed's.
OWN_BRANCH="d7026124-76e6-4907-aaee-d497a08f908e" OTHER_BRANCH="415d1773-e834-46dd-87ed-c42ad7f555da"
bm_token="$(curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"reference","username":"jefelocal","password":"jefelocal123","deviceId":"acceptance-bm","deviceName":"acceptance"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')"
range="from=0&to=$(($(date +%s) * 1000))"
for path in "analytics/revenue?$range" "analytics/sales-by-day?$range" "forecasts?$range"; do
  code="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $bm_token" "$API/$path&branchId=$OTHER_BRANCH")"
  [ "$code" = 403 ] || die "a branch manager read another branch's /$path (HTTP $code, expected 403)"
  [ "$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $bm_token" "$API/$path&branchId=$OWN_BRANCH")" = 200 ] \
    || die "a branch manager could not read their own branch's /$path"
done
pass "a branch manager is refused another branch's analytics and forecasts, and reads their own"

# The dashboard's headers are generated at deploy time from the real origins; check the generator on a stand-in build.
FIX="$(mktemp -d)"
cat > "$FIX/index.html" <<'HTML'
<!doctype html><html><head><script>window.theme = "dark"</script></head><body><script type="module" src="/assets/app.js"></script></body></html>
HTML
echo '{"navigationFallback":{"rewrite":"/index.html"}}' > "$FIX/staticwebapp.config.json"
python3 -I infra/web-headers.py --dist "$FIX" --api https://api.example.net --images https://photos.example.net
cfg="$(cat "$FIX/staticwebapp.config.json")"
want_hash="'sha256-$(printf '%s' 'window.theme = "dark"' | openssl dgst -sha256 -binary | openssl base64)'"
for needle in "frame-ancestors 'none'" "X-Frame-Options" "nosniff" "connect-src 'self' https://api.example.net" \
              "img-src 'self' data: blob: https://photos.example.net https:" "$want_hash" "navigationFallback"; do
  grep -qF -- "$needle" <<<"$cfg" || { echo "$cfg"; die "dashboard config lacks: $needle"; }
done
! grep -q "unsafe-eval\|script-src[^;]*unsafe-inline" <<<"$cfg" || die "the policy allows eval or inline scripts"
rm -rf "$FIX"
pass "dashboard headers: no framing, scripts by hash only, API and photo origins named"

# ---- 3c. a batch pushed while the broker is down is still decremented and announced, once (#142) --
step "Outbox: a batch pushed while the broker is down"
login_json() { curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"tenantSlug\":\"reference\",\"username\":\"$1\",\"password\":\"$2\",\"deviceId\":\"acceptance-$1\",\"deviceName\":\"acceptance\"}"; }
field() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
BAKER_JSON="$(login_json panadero panadero123)"; MGR_TOKEN="$(login_json gerente gerente123 | field 'd["accessToken"]')"
BAKER_TOKEN="$(field 'd["accessToken"]' <<<"$BAKER_JSON")"; BAKER_ID="$(field 'd["user"]["id"]' <<<"$BAKER_JSON")"
BAKER_BRANCH="$(field 'd["user"]["branchId"]' <<<"$BAKER_JSON")"
PRODUCT_ID="$(curl -fsS "$API/catalog/products" -H "Authorization: Bearer $MGR_TOKEN" | field "next(p['id'] for p in d if p['name'] == 'Marraqueta')")"
BATCH_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"; MUTATION_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
push_batch() {
  local now; now="$(python3 -c 'import time; print(int(time.time()*1000))')"
  curl -s -w '\n%{http_code}' -X POST "$API/sync/push" -H "Authorization: Bearer $BAKER_TOKEN" -H 'Content-Type: application/json' -d "{
    \"deviceId\": \"acceptance-outbox\", \"mutations\": [{\"mutationId\": \"$MUTATION_ID\", \"entityType\": \"PRODUCTION_BATCH\",
    \"opType\": \"CREATE\", \"clientTimestamp\": $now, \"payload\": {\"id\": \"$BATCH_ID\", \"branchId\": \"$BAKER_BRANCH\",
    \"bakerId\": \"$BAKER_ID\", \"productId\": \"$PRODUCT_ID\", \"mixWeightGrams\": 10000, \"batchAt\": $now, \"movementsMinted\": false}}]}"
}

"${DC[@]}" stop rabbitmq >/dev/null
out="$(push_batch)"
[ "$(tail -1 <<<"$out")" = 200 ] || die "with the broker down, the batch push answered HTTP $(tail -1 <<<"$out"): $(head -1 <<<"$out")"
grep -q '"APPLIED"' <<<"$out" || die "with the broker down, the batch was not APPLIED: $out"
pass "with the broker stopped, the pushed batch is accepted (APPLIED)"
[ "$(psql_db -tA -c "select count(*) from outbox_messages where sent_at is null and payload like '%$BATCH_ID%'")" = 1 ] \
  || die "the accepted batch has no pending outbox row"
grep -q '"DUPLICATE"' <<<"$(push_batch)" || die "a retried push of the same batch was not a DUPLICATE"
[ "$(psql_db -tA -c "select count(*) from outbox_messages where payload like '%$BATCH_ID%'")" = 1 ] \
  || die "a retried push enqueued the batch a second time"
pass "a retried push is a DUPLICATE and enqueues nothing more"

STREAM="$(mktemp)"
curl -sN --max-time 240 "$API/events/sse?access_token=$MGR_TOKEN" > "$STREAM" 2>/dev/null & SSE_PID=$!
"${DC[@]}" start rabbitmq >/dev/null
for i in $(seq 1 90); do
  [ "$(psql_db -tA -c "select decrement_status from production_batches where id = '$BATCH_ID'")" = APPLIED ] && break
  sleep 2; [ "$i" -lt 90 ] || die "after the broker came back, the batch was never decremented"
done
[ "$(psql_db -tA -c "select count(*) from stock_movements where source_batch_id = '$BATCH_ID'")" -gt 0 ] \
  || die "the batch is APPLIED but no ingredient movements reference it"
pass "after the broker restarts, the batch is decremented"
for i in $(seq 1 60); do grep -A1 '^event: batch-ready' "$STREAM" | grep -q "$BATCH_ID" && break; sleep 2
  [ "$i" -lt 60 ] || die "the batch was never announced (batch-ready) on the dashboard stream"; done
sleep 10   # long enough for a second relay pass or a redelivery to show up
announced="$(grep -A1 '^event: batch-ready' "$STREAM" | grep -c "$BATCH_ID" || true)"
kill "$SSE_PID" 2>/dev/null || true; rm -f "$STREAM"
[ "$announced" = 1 ] || die "the batch was announced $announced times, expected exactly once"
pass "... and announced over SSE exactly once"

# ---- 4. the verifier must fail loudly on each broken thing --------------------------------------
step "Verifier: broken stacks"
expect_fail "wrong backend URL" "FAIL  backend /health" infra/verify.sh "http://localhost:18999" --no-sse --forecast-wait 1

"${DC[@]}" stop ml >/dev/null
expect_fail "ML service down" "FAIL  ML service /health" "${VERIFY[@]}" --ml-url "$ML" --no-sse --forecast-wait 1
"${DC[@]}" start ml >/dev/null

psql_db -c "update products set image_url = 'http://localhost:11000/devstoreaccount1/product-images/does-not-exist.png' where id = (select id from products order by name limit 1)"
expect_fail "broken own-storage photo" "FAIL  product photos resolve" "${VERIFY[@]}" --no-sse --forecast-wait 1

"${DC[@]}" stop notifier >/dev/null
expect_fail "notifier down (no SSE event)" "FAIL" env TIMEOUT=20 scripts/verify-sse.sh "$API"
"${DC[@]}" start notifier >/dev/null

# ---- 5. release artifacts (opt-in: needs the Android SDK, takes minutes) ------------------------
if [ "$RELEASE" = true ]; then
  step "Release artifacts"
  scripts/acceptance/release-artifacts.sh
fi

step "Acceptance suite passed"

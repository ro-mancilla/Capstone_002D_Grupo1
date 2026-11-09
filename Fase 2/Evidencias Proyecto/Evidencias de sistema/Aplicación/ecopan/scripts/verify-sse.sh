#!/usr/bin/env bash
#
# verify-sse.sh -- prove that a baker's batch becomes a live event on the dashboard's SSE stream.
#
#   baker pushes a batch -> API -> broker -> worker (formula decrement) -> notifier -> API -> SSE
#
# This is the INFRA-6 acceptance test (plan 10): "an SSE event is visible in the web dashboard from
# a synced baker batch". Run it against the local stack, and again against the deployed one, where
# the path also crosses the platform's ingress -- the hop that can buffer or time out a stream:
#
#   scripts/verify-sse.sh                              # local stack, http://localhost:8080
#   scripts/verify-sse.sh https://<app>.azurewebsites.net
#
# It also checks the stream is not being buffered: a heartbeat (every 15 s) must arrive on its own,
# before any batch, which a buffering proxy would hold back.
#
# Side effect: it records one real 10 kg batch (the seeded baker's first product, by default
# "Marraqueta"), so ingredient stock goes down by that batch, exactly as in the README's demo.
#
# Environment: TENANT (reference), MANAGER (gerente) + MANAGER_PASSWORD (gerente123),
#              BAKER (panadero) + BAKER_PASSWORD (panadero123), PRODUCT (Marraqueta),
#              TIMEOUT seconds to wait for the event (60).
#
set -Eeuo pipefail

BASE="${1:-http://localhost:8080}"
TENANT="${TENANT:-reference}"
MANAGER="${MANAGER:-gerente}"; MANAGER_PASSWORD="${MANAGER_PASSWORD:-gerente123}"
BAKER="${BAKER:-panadero}";    BAKER_PASSWORD="${BAKER_PASSWORD:-panadero123}"
PRODUCT="${PRODUCT:-Marraqueta}"
TIMEOUT="${TIMEOUT:-60}"

WORK="$(mktemp -d)"
STREAM="$WORK/stream.txt"
trap 'kill "${CURL_PID:-0}" 2>/dev/null || true; wait 2>/dev/null || true; rm -rf "$WORK"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
json() { python3 -c "import sys, json; d = json.load(sys.stdin); print($1)"; }

login() { # user password -> login response json
  curl -fsS -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d "{\"tenantSlug\":\"$TENANT\",\"username\":\"$1\",\"password\":\"$2\"}" \
    || fail "login as $1 against $BASE"
}

echo "1/5 signing in as $MANAGER (dashboard) and $BAKER (baker) on $BASE"
MGR_JSON="$(login "$MANAGER" "$MANAGER_PASSWORD")"
BAKER_JSON="$(login "$BAKER" "$BAKER_PASSWORD")"
MGR_TOKEN="$(echo "$MGR_JSON" | json 'd["accessToken"]')"
BAKER_TOKEN="$(echo "$BAKER_JSON" | json 'd["accessToken"]')"
BAKER_ID="$(echo "$BAKER_JSON" | json 'd["user"]["id"]')"
BRANCH_ID="$(echo "$BAKER_JSON" | json 'd["user"]["branchId"]')"
[ "$BRANCH_ID" != "None" ] || fail "$BAKER has no branch; the batch needs one"

PRODUCT_ID="$(curl -fsS "$BASE/catalog/products" -H "Authorization: Bearer $MGR_TOKEN" \
  | json "next((p['id'] for p in d if p['name'] == '$PRODUCT'), '')")"
[ -n "$PRODUCT_ID" ] || fail "no product named '$PRODUCT' in the catalog"

echo "2/5 opening the dashboard's event stream"
curl -sN --max-time $((TIMEOUT + 30)) "$BASE/events/sse?access_token=$MGR_TOKEN" > "$STREAM" 2>/dev/null &
CURL_PID=$!
sleep 1
kill -0 "$CURL_PID" 2>/dev/null || fail "the event stream did not stay open"

echo "3/5 waiting for a heartbeat on the idle stream (proves nothing is buffering it)"
waited=0
until grep -q '^event: heartbeat' "$STREAM"; do
  sleep 1; waited=$((waited + 1))
  [ "$waited" -le 25 ] || fail "no heartbeat within 25 s: something between here and the API is holding the stream back"
done
echo "    heartbeat after ${waited}s"

BATCH_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
MUTATION_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
NOW_MS="$(python3 -c 'import time; print(int(time.time()*1000))')"

echo "4/5 the baker pushes a 10 kg batch of $PRODUCT ($BATCH_ID)"
RESULT="$(curl -fsS -X POST "$BASE/sync/push" -H "Authorization: Bearer $BAKER_TOKEN" -H 'Content-Type: application/json' -d "{
  \"deviceId\": \"verify-sse\",
  \"mutations\": [{
    \"mutationId\": \"$MUTATION_ID\", \"entityType\": \"PRODUCTION_BATCH\", \"opType\": \"CREATE\", \"clientTimestamp\": $NOW_MS,
    \"payload\": {\"id\": \"$BATCH_ID\", \"branchId\": \"$BRANCH_ID\", \"bakerId\": \"$BAKER_ID\", \"productId\": \"$PRODUCT_ID\",
                  \"mixWeightGrams\": 10000, \"batchAt\": $NOW_MS, \"movementsMinted\": false}
  }]}")" || fail "the push was refused"
STATUS="$(echo "$RESULT" | json 'd["results"][0]["status"]')"
case "$STATUS" in APPLIED|DUPLICATE) ;; *) fail "the server answered $STATUS: $RESULT" ;; esac

echo "5/5 waiting up to ${TIMEOUT}s for the event about that batch"
start=$SECONDS
until grep -q "$BATCH_ID" "$STREAM"; do
  sleep 1
  [ $((SECONDS - start)) -lt "$TIMEOUT" ] || fail "no event for $BATCH_ID within ${TIMEOUT}s (is the worker/notifier running?)"
done
echo "PASS: the dashboard stream carried the batch event $((SECONDS - start))s after the push"
grep -B1 -A2 "$BATCH_ID" "$STREAM" | head -6 | sed 's/^/    /'

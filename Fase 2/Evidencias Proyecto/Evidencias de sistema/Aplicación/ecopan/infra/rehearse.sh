#!/usr/bin/env bash
#
# Rehearse the whole Azure lifecycle, unattended: deploy -> verify -> tear down -> verify it is gone.
#
#   ./infra/rehearse.sh                    # ~40 min, a dollar or so of credit
#   ./infra/rehearse.sh --keep             # stop after verify; leave the stack up (you tear it down)
#   ./infra/rehearse.sh --keep-on-failure  # on a failed phase, do NOT tear down (to debug the stack)
#   ./infra/rehearse.sh -- --ml-baseline-only      # everything after -- goes to deploy.sh
#
# Safe by construction: it uses its OWN resource group and name prefix (rg-ecopan-rehearsal / ecoreh),
# so it can never delete the real deployment, and it refuses to start if that group already exists.
# Override with ECOPAN_RG / ECOPAN_PREFIX if you must; the guard still applies.
#
# What "verified" means here is infra/verify.sh: the deployed stack holds the same data as the local
# demo stack -- the product count and photo count are read from the LOCAL database first and
# asserted against Azure. Needs local Docker (the data import and that read), az (logged in),
# python3. The local stack's volumes are only read, never modified.
#
# Exit status: 0 only if every phase passed; otherwise the first failing phase's code. Teardown runs
# after a failure too (unless --keep-on-failure), so a broken rehearsal does not keep billing.

set -u -o pipefail   # no -e on purpose: each phase's status is recorded, not fatal

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$REPO_ROOT/infra"
export ECOPAN_RG="${ECOPAN_RG:-rg-ecopan-rehearsal}"
export ECOPAN_PREFIX="${ECOPAN_PREFIX:-ecoreh}"
RG="$ECOPAN_RG"
PREFIX="$ECOPAN_PREFIX"

KEEP=false KEEP_ON_FAILURE=false DEPLOY_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep) KEEP=true; shift ;;
    --keep-on-failure) KEEP_ON_FAILURE=true; shift ;;
    --) shift; DEPLOY_ARGS=("$@"); break ;;
    -h|--help) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $1 (arguments for deploy.sh go after --)" >&2; exit 2 ;;
  esac
done

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die()  { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 2; }

PHASES=()   # "name|status|seconds"
FIRST_FAIL=0
run_phase() { # run_phase <name> <command...>
  local name="$1"; shift
  log "Phase: $name"
  local t0 rc t1
  t0=$(date +%s)
  "$@"; rc=$?
  t1=$(date +%s)
  PHASES+=("$name|$rc|$((t1 - t0))")
  [[ $rc -ne 0 && $FIRST_FAIL -eq 0 ]] && FIRST_FAIL=$rc
  return "$rc"
}

# ---------------------------------------------------------------------------------------------
# Preflight -- everything that would waste a 20-minute deploy if it were wrong
# ---------------------------------------------------------------------------------------------
log "Preflight"
command -v az >/dev/null      || die "az CLI not found"
az account show >/dev/null 2>&1 || die "not logged in. Run: az login"
command -v python3 >/dev/null || die "python3 not found"

# Checked before anything else: the rehearsal deletes its group at the end.
if [[ "$(az group exists -n "$RG")" == true ]]; then
  die "resource group $RG already exists. The rehearsal deletes its group, so it only runs against a group it creates. Delete it first (./infra/teardown.sh with ECOPAN_RG=$RG) or pick another ECOPAN_RG."
fi
command -v docker >/dev/null && docker info >/dev/null 2>&1 || die "local Docker is not running (needed to read the local demo data)"

# What Azure must end up holding: read from the local database, the source of the import.
(cd "$REPO_ROOT" && docker compose up -d db >/dev/null 2>&1)
for _ in $(seq 1 45); do
  (cd "$REPO_ROOT" && docker compose exec -T db pg_isready -U postgres >/dev/null 2>&1) && break
  sleep 2
done
local_q() { (cd "$REPO_ROOT" && docker compose exec -T db psql -U postgres -d ecopan -Atc "$1" 2>/dev/null); }
[[ "$(local_q "select to_regclass('public.products') is not null")" == t ]] \
  || die "the local database has no catalog. Run scripts/demo-up.sh once first: the rehearsal asserts Azure matches it."
EXPECT_PRODUCTS="$(local_q "select count(*) from products")"
EXPECT_PHOTOS="$(local_q "select count(*) from products where image_url is not null")"
info "subscription : $(az account show --query name -o tsv)"
info "group/prefix : $RG / $PREFIX"
info "local data   : $EXPECT_PRODUCTS products, $EXPECT_PHOTOS with a photo (Azure must match)"
(cd "$REPO_ROOT" && docker compose stop db >/dev/null 2>&1) || true

# ---------------------------------------------------------------------------------------------
# Phases
# ---------------------------------------------------------------------------------------------
BACKEND_URL="" WEB_URL=""

deploy_phase() {
  "$INFRA_DIR/deploy.sh" ${DEPLOY_ARGS[@]+"${DEPLOY_ARGS[@]}"} || return $?
  local fqdn swa
  fqdn=$(az webapp show -g "$RG" -n "$PREFIX-app" --query defaultHostName -o tsv 2>/dev/null) || return 1
  swa=$(az staticwebapp list -g "$RG" --query "[0].defaultHostname" -o tsv 2>/dev/null)
  BACKEND_URL="https://$fqdn"
  [[ -n "$swa" ]] && WEB_URL="https://$swa"
  info "backend $BACKEND_URL   dashboard ${WEB_URL:-<none>}"
}

verify_phase() {
  [[ -n "$BACKEND_URL" ]] || { echo "no backend URL (deploy failed?)" >&2; return 1; }
  # The ML service is IP-restricted to the plan, so its model is checked by deploy.sh (which fails the
  # deploy if /health is not as expected); everything reachable from here is checked below.
  "$INFRA_DIR/verify.sh" "$BACKEND_URL" \
    ${WEB_URL:+--web-url "$WEB_URL"} \
    --expect-products "$EXPECT_PRODUCTS" --expect-photos "$EXPECT_PHOTOS" --forecast-wait 180
}

teardown_phase() {
  "$INFRA_DIR/teardown.sh" --yes || return $?
  # teardown.sh deletes with --no-wait; "gone" has to be observed, not assumed.
  local deadline=$(( $(date +%s) + 1500 ))
  while [[ "$(az group exists -n "$RG")" == true ]]; do
    [[ $(date +%s) -lt $deadline ]] || { echo "resource group $RG still exists after 25 min" >&2; return 1; }
    sleep 20
  done
}

gone_phase() {
  local rc=0
  [[ "$(az group exists -n "$RG")" == false ]] || { echo "group $RG still exists" >&2; rc=1; }
  # Nothing of ours may linger outside the group either (a stray registry/storage/SWA with our prefix).
  local stray
  stray=$(az resource list --query "[?starts_with(name, '$PREFIX')].{n:name,t:type,g:resourceGroup}" -o tsv 2>/dev/null)
  [[ -z "$stray" ]] || { echo "resources with prefix '$PREFIX' still exist:" >&2; echo "$stray" >&2; rc=1; }
  return $rc
}

run_phase deploy deploy_phase
deploy_rc=$?
verify_rc=-1
if [[ $deploy_rc -eq 0 ]]; then
  run_phase verify verify_phase; verify_rc=$?
else
  info "skipping verify: deploy failed"
fi

if [[ "$KEEP" == true ]]; then
  log "Keeping the stack (--keep). Tear it down with: ECOPAN_RG=$RG ./infra/teardown.sh"
elif [[ $FIRST_FAIL -ne 0 && "$KEEP_ON_FAILURE" == true ]]; then
  log "A phase failed and --keep-on-failure is set: leaving $RG up for debugging. It is billing."
  info "tear down: ECOPAN_RG=$RG ./infra/teardown.sh --yes"
else
  run_phase teardown teardown_phase
  run_phase verify-gone gone_phase
fi

# ---------------------------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------------------------
log "Rehearsal report"
total=0
for row in ${PHASES[@]+"${PHASES[@]}"}; do
  IFS='|' read -r name rc secs <<<"$row"
  total=$((total + secs))
  printf '    %-12s %-6s %3dm%02ds\n' "$name" "$([[ $rc -eq 0 ]] && echo PASS || echo "FAIL($rc)")" $((secs / 60)) $((secs % 60))
done
printf '    %-12s %-6s %3dm%02ds\n' "total" "" $((total / 60)) $((total % 60))
if [[ $FIRST_FAIL -eq 0 ]]; then
  printf '\n\033[1;32mREHEARSAL PASSED\033[0m\n'
else
  printf '\n\033[1;31mREHEARSAL FAILED\033[0m (first failing status %d)\n' "$FIRST_FAIL" >&2
fi
exit "$FIRST_FAIL"

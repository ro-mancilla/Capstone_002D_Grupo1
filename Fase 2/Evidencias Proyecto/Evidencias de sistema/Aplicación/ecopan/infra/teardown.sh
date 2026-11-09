#!/usr/bin/env bash
#
# Delete the entire ecopan deployment.
#
#   ./infra/teardown.sh              # prompts, then deletes the resource group
#   ./infra/teardown.sh --yes        # no prompt
#   ./infra/teardown.sh --stop-only  # stop Postgres + stop the web apps, keep everything
#
# Everything lives in one resource group, so teardown is one delete with nothing stranded. The
# generated secrets in infra/.env.local are kept unless --purge-secrets is passed: re-running
# deploy.sh then rebuilds the same stack with the same credentials.

set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESOURCE_GROUP="${ECOPAN_RG:-rg-ecopan}"
ASSUME_YES=false
STOP_ONLY=false
PURGE_SECRETS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes|-y) ASSUME_YES=true; shift ;;
    --resource-group) RESOURCE_GROUP="$2"; shift 2 ;;
    --stop-only) STOP_ONLY=true; shift ;;
    --purge-secrets) PURGE_SECRETS=true; shift ;;
    -h|--help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

az account show >/dev/null 2>&1 || die "not logged in. Run: az login"
az group show -n "$RESOURCE_GROUP" >/dev/null 2>&1 || { echo "resource group $RESOURCE_GROUP does not exist — nothing to do"; exit 0; }

# ---------------------------------------------------------------------------------------------
# Stop-only: the between-sessions mode
# ---------------------------------------------------------------------------------------------
# Most of the idle bill is PostgreSQL, which bills for compute whether or not anything connects.
# A stopped burstable server bills only for storage (~$4/mo at 32 GB) and can stay stopped for up
# to 7 days before Azure restarts it automatically.
#
# Stopping the web apps, however, saves nothing: an App Service Plan bills for its instance
# whether the apps on it run or not. Only deleting or downscaling the *plan* moves that number,
# so this mode drops the plan to the cheapest Basic size rather than pretending stopping helps.
# Service Bus Standard is a flat $10/month with no idle discount either. Keeps the data and the
# URLs, so resuming is seconds rather than a 20-minute rebuild.
if [[ "$STOP_ONLY" == true ]]; then
  log "Stopping (keeping all resources and data)"

  PG_NAME=$(az postgres flexible-server list -g "$RESOURCE_GROUP" --query "[0].name" -o tsv 2>/dev/null || echo "")
  if [[ -n "$PG_NAME" ]]; then
    echo "    stopping postgres $PG_NAME"
    az postgres flexible-server stop -g "$RESOURCE_GROUP" -n "$PG_NAME" -o none 2>/dev/null || true
  fi

  for app in $(az webapp list -g "$RESOURCE_GROUP" --query "[].name" -o tsv); do
    echo "    stopping $app"
    az webapp stop -g "$RESOURCE_GROUP" -n "$app" -o none
  done

  PLAN_NAME=$(az appservice plan list -g "$RESOURCE_GROUP" --query "[0].name" -o tsv 2>/dev/null || echo "")
  if [[ -n "$PLAN_NAME" ]]; then
    echo "    downscaling plan $PLAN_NAME to B1"
    az appservice plan update -g "$RESOURCE_GROUP" -n "$PLAN_NAME" --sku B1 -o none
  fi

  cat <<EOF

  Stopped. Still billing: App Service Plan B1 (~\$15/mo), Service Bus Standard (\$10/mo),
  registry (~\$5/mo) and database storage (~\$4/mo) — roughly \$34/mo. Postgres compute does not.
  NOTE: Azure auto-restarts a stopped burstable server after 7 days.

  For a real pause, delete the group: the credit is better spent on a fresh 20-minute rebuild
  than on \$34/mo of idle managed services.

  Resume:  az postgres flexible-server start -g $RESOURCE_GROUP -n $PG_NAME
           ./infra/deploy.sh --apps-only --skip-build

EOF
  exit 0
fi

# ---------------------------------------------------------------------------------------------
# Full delete
# ---------------------------------------------------------------------------------------------
log "About to DELETE resource group: $RESOURCE_GROUP"
az resource list -g "$RESOURCE_GROUP" --query "[].{name:name, type:type}" -o table 2>/dev/null || true

if [[ "$ASSUME_YES" == false ]]; then
  printf '\nThis destroys the database and all demo data. Type the resource group name to confirm: '
  read -r confirmation
  [[ "$confirmation" == "$RESOURCE_GROUP" ]] || die "confirmation did not match — nothing deleted"
fi

log "Deleting (runs in the background, ~5 min)"
az group delete -n "$RESOURCE_GROUP" --yes --no-wait
echo "    delete started; track with: az group show -n $RESOURCE_GROUP"

if [[ "$PURGE_SECRETS" == true ]]; then
  rm -f "$REPO_ROOT/infra/.env.local"
  echo "    removed infra/.env.local — the next deploy generates fresh credentials"
else
  echo "    kept infra/.env.local (pass --purge-secrets to rotate on next deploy)"
fi

#!/usr/bin/env bash
#
# Deploy the whole ecopan stack to Azure, from nothing to a working public URL.
#
#   ./infra/deploy.sh                      # full deploy into the default region
#   ./infra/deploy.sh --location brazilsouth
#   ./infra/deploy.sh --skip-build         # redeploy config without rebuilding images
#   ./infra/deploy.sh --apps-only          # skip the foundation (registry/db/env already exist)
#   ./infra/deploy.sh --ml-baseline-only   # deliberately ship the ML image WITHOUT a trained model
#   ./infra/deploy.sh --ml-skip-build      # rebuild everything except ml-service (what CI uses)
#   ./infra/deploy.sh --no-import          # skip copying the local demo data (plain reference seed)
#   ./infra/deploy.sh --reimport           # replace the Azure database with the local one again
#   ./infra/deploy.sh --launch-clients    # afterwards, build + launch the release Android app and desktop till
#                                          # against this deployment (scripts/prod-up.sh)
#
# DEMO DATA: by default the local stack's catalog and uploaded photos are copied into Azure
# (infra/import-local.sh, needs local Docker), then the same opening-stock and forecast steps as
# scripts/demo-up.sh run against the deployed API -- so Azure ends up in the state demo-up.sh
# leaves the local stack in. The import only runs while the Azure database is empty; --reimport
# overwrites it.
#
# The ML image bakes in the trained model from ml/data/artifacts on THIS machine, so build on a
# machine that has run `make train` (or copied the artifact in). Without it the build is refused
# unless --ml-baseline-only is given. --ml-skip-build leaves ecopan/ml-service:latest untouched and
# re-tags it for this deploy (a runner has no artifact; it must not replace a model image with a
# baseline one). --skip-build skips ALL builds and deploys tag $ECOPAN_IMAGE_TAG (default: the git
# SHA), which must already exist in the registry.
# After rollout the script checks the ML service's /health: it must report the model when this run
# built it with one, baseline-only when asked for that, and is only reported otherwise.
#
# Idempotent: safe to re-run. Secrets are generated on first run and cached in infra/.env.local
# (gitignored) so a re-run does not rotate the database password out from under a running server.
#
# Requires: az CLI (logged in), and a repo checkout. Does NOT require local Docker — images are
# built server-side with `az acr build`, which also sidesteps the arm64-Mac/amd64-cloud
# architecture mismatch that silently produces images App Service cannot start.

set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$REPO_ROOT/infra"
ENV_FILE="$INFRA_DIR/.env.local"

# Brazil South. Chile Central would be the better fit on every technical axis -- Santiago latency
# for a Chilean client, and PostgreSQL B1ms at $0.0238/h vs $0.035/h -- but this tenant enforces an
# "Allowed locations" policy at the management-group level that excludes it, so the deployment is
# denied. Discovered by validating against the live API, not by reading docs.
LOCATION="${ECOPAN_LOCATION:-brazilsouth}"
RESOURCE_GROUP="${ECOPAN_RG:-rg-ecopan}"
NAME_PREFIX="${ECOPAN_PREFIX:-ecopan}"
SEED_PROFILE="${ECOPAN_SEED_PROFILE:-reference}"

SKIP_BUILD=false
APPS_ONLY=false
SKIP_WEB=false
ML_BASELINE_ONLY=false
ML_SKIP_BUILD=false
IMPORT_LOCAL=true
REIMPORT=false
LAUNCH_CLIENTS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --location) LOCATION="$2"; shift 2 ;;
    --resource-group) RESOURCE_GROUP="$2"; shift 2 ;;
    --prefix) NAME_PREFIX="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    --apps-only) APPS_ONLY=true; shift ;;
    --skip-web) SKIP_WEB=true; shift ;;
    --ml-baseline-only) ML_BASELINE_ONLY=true; shift ;;
    --ml-skip-build) ML_SKIP_BUILD=true; shift ;;
    --no-import) IMPORT_LOCAL=false; shift ;;
    --reimport) REIMPORT=true; shift ;;
    --launch-clients) LAUNCH_CLIENTS=true; shift ;;
    -h|--help) sed -n '2,34p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die()  { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# Report which step failed. Without this a mid-script failure prints only the az error, and with
# ~10 steps that is a poor starting point for debugging.
trap 'die "failed at line $LINENO. Nothing is rolled back — re-run to continue, or ./infra/teardown.sh to start over."' ERR

# ---------------------------------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------------------------------
log "Preflight"

command -v az >/dev/null || die "az CLI not found — https://aka.ms/azcli"
az account show >/dev/null 2>&1 || die "not logged in. Run: az login"

SUBSCRIPTION_NAME=$(az account show --query name -o tsv)
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
info "subscription : $SUBSCRIPTION_NAME ($SUBSCRIPTION_ID)"
info "region       : $LOCATION"
info "resource grp : $RESOURCE_GROUP"

# These are not all registered by default on a fresh subscription, and the failure that produces
# ("MissingSubscriptionRegistration") is opaque if you have not seen it before. Registering is
# idempotent and takes a minute or two the first time.
for provider in Microsoft.Web Microsoft.ServiceBus Microsoft.ContainerRegistry Microsoft.DBforPostgreSQL Microsoft.Storage; do
  state=$(az provider show -n "$provider" --query registrationState -o tsv 2>/dev/null || echo "NotRegistered")
  if [[ "$state" != "Registered" ]]; then
    info "registering $provider (first run only, may take ~2 min)"
    az provider register -n "$provider" --wait
  fi
done
info "providers    : registered"

# Fail before any cloud work if the model the ML image is supposed to carry is not on this machine.
# (The Dockerfile enforces the same rule server-side, but only after the foundation has deployed.)
[[ "$ML_SKIP_BUILD" == true && "$ML_BASELINE_ONLY" == true ]] && die "--ml-skip-build and --ml-baseline-only contradict each other"

# What /health must say after rollout: "model" when this run builds the ML image with a model,
# "baseline" when asked for a baseline-only image, otherwise whatever is already deployed (reported).
ML_EXPECT=report
if [[ "$SKIP_BUILD" == false && "$ML_SKIP_BUILD" == false ]]; then
  [[ "$ML_BASELINE_ONLY" == true ]] && ML_EXPECT=baseline || ML_EXPECT=model
fi

if [[ "$ML_EXPECT" == model ]]; then
  for f in model.txt metadata.json encoding.json; do
    [[ -f "$REPO_ROOT/ml/data/artifacts/$f" ]] || die "ml/data/artifacts/$f not found. The ML image ships the trained model: run 'make train' in ml/ (or copy the artifact here), or pass --ml-baseline-only to deploy without it."
  done
  info "ml model     : ml/data/artifacts present"
fi

# ---------------------------------------------------------------------------------------------
# Secrets — generated once, then reused
# ---------------------------------------------------------------------------------------------
log "Secrets"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  info "reusing generated secrets from infra/.env.local"
else
  info "generating secrets -> infra/.env.local (gitignored, never commit)"
  # openssl rather than $RANDOM: these guard a public database endpoint and sign JWTs, and $RANDOM
  # is a 15-bit LCG. `tr -dc` strips characters Postgres rejects in a password.
  PG_PASSWORD="$(openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | head -c 28)Aa1"
  JWT_SECRET="$(openssl rand -hex 32)"
  ML_API_KEY="$(openssl rand -hex 24)"
  umask 077
  cat > "$ENV_FILE" <<EOF
# Generated by infra/deploy.sh. Gitignored. Delete to rotate every secret on the next deploy
# (which also requires re-deploying the apps, since the database password changes).
PG_PASSWORD='$PG_PASSWORD'
JWT_SECRET='$JWT_SECRET'
ML_API_KEY='$ML_API_KEY'
EOF
fi

# ---------------------------------------------------------------------------------------------
# Resource group + foundation
# ---------------------------------------------------------------------------------------------
if [[ "$APPS_ONLY" == false ]]; then
  log "Resource group"
  az group create -n "$RESOURCE_GROUP" -l "$LOCATION" -o none
  info "$RESOURCE_GROUP in $LOCATION"

  log "Foundation (registry, storage, Service Bus, PostgreSQL)"
  info "PostgreSQL takes 5-10 minutes on first create — this is the slow step"
  az deployment group create \
    --resource-group "$RESOURCE_GROUP" \
    --name "foundation-$(date +%Y%m%d%H%M%S)" \
    --template-file "$INFRA_DIR/foundation.bicep" \
    --parameters \
      location="$LOCATION" \
      namePrefix="$NAME_PREFIX" \
      postgresAdminPassword="$PG_PASSWORD" \
    -o none
fi

# Read outputs back from the deployed resources rather than from the deployment record, so
# --apps-only works without a prior deployment in this shell.
log "Discovering foundation resources"
REGISTRY_NAME=$(az acr list -g "$RESOURCE_GROUP" --query "[0].name" -o tsv)
[[ -n "$REGISTRY_NAME" ]] || die "no container registry in $RESOURCE_GROUP — run without --apps-only first"
REGISTRY_SERVER=$(az acr show -n "$REGISTRY_NAME" --query loginServer -o tsv)
PG_NAME=$(az postgres flexible-server list -g "$RESOURCE_GROUP" --query "[0].name" -o tsv)
PG_FQDN=$(az postgres flexible-server show -g "$RESOURCE_GROUP" -n "$PG_NAME" --query fullyQualifiedDomainName -o tsv)
STORAGE_ACCOUNT=$(az storage account list -g "$RESOURCE_GROUP" --query "[?starts_with(name, '${NAME_PREFIX}st')].name | [0]" -o tsv)
[[ -n "$STORAGE_ACCOUNT" ]] || die "no storage account in $RESOURCE_GROUP — run without --apps-only first"
SB_NAMESPACE=$(az servicebus namespace list -g "$RESOURCE_GROUP" --query "[0].name" -o tsv)
[[ -n "$SB_NAMESPACE" ]] || die "no Service Bus namespace in $RESOURCE_GROUP — run without --apps-only first"
# Read the connection string here rather than passing it out of the foundation deployment, so
# --apps-only works in a fresh shell with no deployment record to read.
SB_CONNECTION=$(az servicebus namespace authorization-rule keys list \
  -g "$RESOURCE_GROUP" --namespace-name "$SB_NAMESPACE" \
  -n RootManageSharedAccessKey --query primaryConnectionString -o tsv)
info "registry     : $REGISTRY_SERVER"
info "storage      : $STORAGE_ACCOUNT (product photos)"
info "service bus  : $SB_NAMESPACE"
info "postgres     : $PG_FQDN"

# Let this machine reach the database (psql, ad-hoc inspection). Separate from the
# allow-Azure-services rule, which only covers traffic originating inside Azure.
MY_IP=$(curl -fsS https://api.ipify.org 2>/dev/null || echo "")
if [[ -n "$MY_IP" ]]; then
  # --server-name/-s names the server and -n the rule (older az releases had it the other way round).
  # Errors are NOT swallowed: a missing rule used to pass silently here and then hang the import.
  az postgres flexible-server firewall-rule create \
    -g "$RESOURCE_GROUP" -s "$PG_NAME" -n "deployer-$(echo "$MY_IP" | tr '.' '-')" \
    --start-ip-address "$MY_IP" --end-ip-address "$MY_IP" --only-show-errors -o none \
    || die "could not add the firewall rule for $MY_IP"
  info "firewall     : allowed $MY_IP"
fi

# ---------------------------------------------------------------------------------------------
# Demo data -- the local catalog + photos, copied in BEFORE any app has touched the database
# ---------------------------------------------------------------------------------------------
# psql against the Azure server, run in a container so no local Postgres client is needed.
azure_psql() {
  docker run --rm -i -e PGPASSWORD="$PG_PASSWORD" postgres:17-alpine \
    psql "host=$PG_FQDN port=5432 dbname=ecopan user=ecopanadmin sslmode=require connect_timeout=10" -v ON_ERROR_STOP=1 -X -q "$@"
}

if [[ "$IMPORT_LOCAL" == true ]]; then
  log "Local demo data"
  command -v docker >/dev/null && docker info >/dev/null 2>&1 || die "local Docker is not running. Start it, or deploy with --no-import."
  has_catalog=""
  for _ in $(seq 1 12); do   # the deployer firewall rule above takes a few seconds to apply
    has_catalog=$(azure_psql -Atc "select to_regclass('public.products') is not null" 2>/dev/null || true)
    [[ -n "$has_catalog" ]] && break
    sleep 5
  done
  [[ -n "$has_catalog" ]] || die "cannot reach the Azure database from this machine (firewall rule for $MY_IP not applied?)"
  if [[ "$has_catalog" == t && "$REIMPORT" == false ]]; then
    info "Azure database already has a catalog -- keeping it (use --reimport to replace it with the local one)"
  else
    PG_FQDN="$PG_FQDN" PG_PASSWORD="$PG_PASSWORD" STORAGE_ACCOUNT="$STORAGE_ACCOUNT" \
      ECOPAN_RG="$RESOURCE_GROUP" "$INFRA_DIR/import-local.sh"
  fi
fi

# ---------------------------------------------------------------------------------------------
# Images — built in the cloud
# ---------------------------------------------------------------------------------------------
IMAGE_TAG="${ECOPAN_IMAGE_TAG:-$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo latest)}"

if [[ "$SKIP_BUILD" == false ]]; then
  log "Building images (tag: $IMAGE_TAG)"
  info "server-side via 'az acr build' — no local Docker, and the result is linux/amd64"

  # The images are independent, so they build at the same time: each `az acr build` runs on the
  # registry's agents, not here. Each logs to its own file (interleaved streams are unreadable) and
  # is waited on by pid -- no `wait -n`, the stock macOS bash is 3.2. A failure is reported by name
  # with the tail of its log once every build has finished, rather than abandoning the others
  # mid-upload. (ACR Basic queues runs beyond its agent capacity, which is slower but still correct.)
  BUILD_LOG_DIR="$(mktemp -d)"
  build_pids=()
  build_names=()
  build_async() { # build_async <name> <command...>
    local name="$1"; shift
    "$@" > "$BUILD_LOG_DIR/$name.log" 2>&1 &
    build_pids+=("$!")
    build_names+=("$name")
    info "started $name"
  }

  # The three JVM images share a build context: settings.gradle.kts includes every module, so
  # Gradle cannot configure :backend:app without the whole project graph present. That makes the
  # context large; .dockerignore trims what it can.
  for service in app worker notifier; do
    build_async "backend-$service" az acr build \
      --registry "$REGISTRY_NAME" \
      --image "ecopan/backend-$service:$IMAGE_TAG" \
      --image "ecopan/backend-$service:latest" \
      --file "backend/$service/Dockerfile" \
      "$REPO_ROOT" \
      --only-show-errors -o none
  done

  if [[ "$ML_SKIP_BUILD" == true ]]; then
    # Re-tag the existing image instead of rebuilding: the apps template points every service at
    # $IMAGE_TAG, and `latest` (the image with the model, built elsewhere) must stay untouched.
    info "ml-service: not rebuilt (--ml-skip-build); re-tagging ecopan/ml-service:latest as $IMAGE_TAG"
    build_async "ml-service" az acr import --name "$REGISTRY_NAME" --force \
      --source "$REGISTRY_SERVER/ecopan/ml-service:latest" \
      --image "ecopan/ml-service:$IMAGE_TAG" --only-show-errors -o none
  else
    info "ml-service model: $([[ "$ML_BASELINE_ONLY" == true ]] && echo 'NONE, baseline-only on request' || echo 'ml/data/artifacts')"
    # The trained model rides in the build context (ml/.dockerignore allowlists the three files the
    # service loads, ~24 MB). The Dockerfile fails the build if they are absent or do not load, and
    # with ALLOW_BASELINE_ONLY=true -- which only --ml-baseline-only sets -- it copies none.
    # `az acr build` resolves --file against the CURRENT directory, not the source directory
    # ("Unable to find 'Dockerfile'" from the repo root), so this one builds from inside ml/. The backend
    # images get away with it because their --file is a repo-root-relative path.
    acr_build_ml() {
      (cd "$REPO_ROOT/ml" && az acr build \
        --registry "$REGISTRY_NAME" \
        --image "ecopan/ml-service:$IMAGE_TAG" \
        --image "ecopan/ml-service:latest" \
        --file Dockerfile \
        --build-arg "ALLOW_BASELINE_ONLY=$ML_BASELINE_ONLY" \
        . --only-show-errors -o none)
    }
    build_async "ml-service" acr_build_ml
  fi

  info "waiting for ${#build_pids[@]} builds ..."
  build_failed=false
  for i in "${!build_pids[@]}"; do
    if wait "${build_pids[$i]}"; then
      info "built   ${build_names[$i]}"
    else
      build_failed=true
      printf '\n\033[1;31m--- %s FAILED (last 40 lines of %s) ---\033[0m\n' \
        "${build_names[$i]}" "$BUILD_LOG_DIR/${build_names[$i]}.log" >&2
      tail -40 "$BUILD_LOG_DIR/${build_names[$i]}.log" >&2
    fi
  done
  [[ "$build_failed" == false ]] || die "image build failed -- see above. Full logs: $BUILD_LOG_DIR. For ml-service with --ml-skip-build the likely cause is no ecopan/ml-service:latest in $REGISTRY_NAME yet: build it once from a machine that has ml/data/artifacts (or pass --ml-baseline-only)."
else
  log "Skipping image build (--skip-build); deploying tag $IMAGE_TAG"
fi

# ---------------------------------------------------------------------------------------------
# Web apps
# ---------------------------------------------------------------------------------------------
log "Deploying web apps"

# CORS: on the first run the Static Web App does not exist yet, so the backend deploys with an
# empty allowlist and is patched further down once the hostname is known. On later runs the
# existing hostname is reused, so the app comes up already correct.
EXISTING_SWA_HOST=$(az staticwebapp list -g "$RESOURCE_GROUP" --query "[0].defaultHostname" -o tsv 2>/dev/null || echo "")
CORS_ORIGINS=""
[[ -n "$EXISTING_SWA_HOST" ]] && CORS_ORIGINS="https://$EXISTING_SWA_HOST"

az deployment group create \
  --resource-group "$RESOURCE_GROUP" \
  --name "apps-$(date +%Y%m%d%H%M%S)" \
  --template-file "$INFRA_DIR/apps.bicep" \
  --parameters \
    location="$LOCATION" \
    namePrefix="$NAME_PREFIX" \
    registryLoginServer="$REGISTRY_SERVER" \
    registryName="$REGISTRY_NAME" \
    imageTag="$IMAGE_TAG" \
    postgresFqdn="$PG_FQDN" \
    postgresDatabaseName="ecopan" \
    postgresAdminUser="ecopanadmin" \
    postgresAdminPassword="$PG_PASSWORD" \
    jwtSecret="$JWT_SECRET" \
    serviceBusConnectionString="$SB_CONNECTION" \
    mlApiKey="$ML_API_KEY" \
    corsAllowedOrigins="$CORS_ORIGINS" \
    storageAccountName="$STORAGE_ACCOUNT" \
    storageCorsAllowedOrigins="$CORS_ORIGINS" \
    seedProfile="$SEED_PROFILE" \
  -o none

BACKEND_FQDN=$(az webapp show -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-app" --query defaultHostName -o tsv)
BACKEND_URL="https://$BACKEND_FQDN"
info "backend      : $BACKEND_URL"

# ---------------------------------------------------------------------------------------------
# Lock the ML service down to the plan's own outbound IPs
# ---------------------------------------------------------------------------------------------
# On Container Apps the ML service had `external: false` and was simply unreachable from outside
# the environment. App Service has no equivalent below Standard tier plus VNet integration, so
# every app here gets a public *.azurewebsites.net hostname whether it wants one or not.
#
# The stand-in: every app on a plan shares that plan's outbound IP set, so an allowlist of those
# IPs means "only apps on this plan may call the ML service" — which is exactly the rule ACA gave
# for free. It cannot be expressed in the template because the IPs are assigned at plan creation,
# hence a post-deploy patch. Adding any Allow rule makes the implicit default Deny, so the two
# commands below are the complete policy.
#
# This is defence in depth, not the only defence: the ML service also requires the bearer key.
log "Restricting ML service to the plan's outbound IPs"
# The outbound IPs are a property of the web apps (`az webapp show`), not of the plan: `az appservice plan
# show` has no such field, so this query used to come back empty and the allowlist below silently never
# applied. Every app on the plan shares the set, so any one app will do.
PLAN_OUTBOUND_IPS=$(az webapp show -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-app" \
  --query possibleOutboundIpAddresses -o tsv 2>/dev/null || echo "")

if [[ -n "$PLAN_OUTBOUND_IPS" ]]; then
  # Wipe any rules from a previous run first, so re-running does not accumulate duplicates or
  # leave a stale IP allowed after Azure reassigns the plan's outbound addresses.
  while read -r rule; do
    [[ -n "$rule" ]] && az webapp config access-restriction remove \
      -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-ml" --rule-name "$rule" -o none 2>/dev/null || true
  done < <(az webapp config access-restriction show -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-ml" \
    --query "ipSecurityRestrictions[?name!='Allow all'].name" -o tsv 2>/dev/null || true)

  priority=100
  IFS=',' read -ra ips <<< "$PLAN_OUTBOUND_IPS"
  for ip in "${ips[@]}"; do
    az webapp config access-restriction add \
      -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-ml" \
      --rule-name "plan-$priority" --action Allow --priority "$priority" \
      --ip-address "$ip/32" -o none 2>/dev/null || true
    priority=$((priority + 1))
  done
  info "ml service   : allowlisted ${#ips[@]} plan outbound IPs (everything else denied)"
else
  info "ml service   : could not read plan outbound IPs; left open (API key still required)"
fi

# ---------------------------------------------------------------------------------------------
# Web dashboard — Static Web Apps (Free)
# ---------------------------------------------------------------------------------------------
if [[ "$SKIP_WEB" == false ]]; then
  log "Web dashboard (Static Web Apps, Free tier)"

  # SWA is not offered in chilecentral or brazilsouth — its control plane lives in a short list of
  # regions. This is cosmetic: content is served from a global CDN regardless, so a Chilean visitor
  # is not fetching from Virginia. eastus2 is simply where the resource record lives.
  SWA_LOCATION="${ECOPAN_SWA_LOCATION:-eastus2}"
  SWA_NAME="$NAME_PREFIX-web"

  az staticwebapp create \
    -g "$RESOURCE_GROUP" -n "$SWA_NAME" -l "$SWA_LOCATION" --sku Free -o none 2>/dev/null || true

  SWA_HOST=$(az staticwebapp show -g "$RESOURCE_GROUP" -n "$SWA_NAME" --query defaultHostname -o tsv)
  info "static web app: https://$SWA_HOST"

  if command -v npm >/dev/null; then
    info "building webApp with VITE_API_BASE_URL=$BACKEND_URL"
    (
      cd "$REPO_ROOT/webApp"
      npm ci --silent
      VITE_API_BASE_URL="$BACKEND_URL" npm run build --silent
    )
    # Framing + Content-Security-Policy, built from the real backend and photo-storage origins (and the
    # hash of the inline theme script), so they cannot drift from what this deploy actually serves.
    python3 -I "$REPO_ROOT/infra/web-headers.py" --dist "$REPO_ROOT/webApp/dist" \
      --api "$BACKEND_URL" --images "https://${STORAGE_ACCOUNT}.blob.core.windows.net"

    # The token goes in the environment (SWA_CLI_DEPLOYMENT_TOKEN), not on the command line, where `ps` shows it.
    SWA_CLI_DEPLOYMENT_TOKEN=$(az staticwebapp secrets list -g "$RESOURCE_GROUP" -n "$SWA_NAME" --query "properties.apiKey" -o tsv)
    export SWA_CLI_DEPLOYMENT_TOKEN
    info "uploading dist/"
    if [[ "$(uname -s)" == Darwin && "$(uname -m)" == arm64 ]] && ! arch -x86_64 /usr/bin/true >/dev/null 2>&1; then
      # The CLI's upload helper (StaticSitesClient) is an x86-64 binary: on Apple Silicon without Rosetta it dies
      # with "spawn Unknown system error -86" (bad CPU type). So run the CLI in an amd64 Linux container instead,
      # which needs: libssl (the helper dlopens it: "No usable version of libssl was found"), and a WRITABLE copy
      # of dist (it writes next to it), hence the cp.
      command -v docker >/dev/null && docker info >/dev/null 2>&1 \
        || die "Apple Silicon without Rosetta: uploading the dashboard needs local Docker (or: softwareupdate --install-rosetta)"
      info "(no Rosetta: uploading from an amd64 container)"
      docker run --rm --platform linux/amd64 -e SWA_CLI_DEPLOYMENT_TOKEN -e DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 \
        -v "$REPO_ROOT/webApp/dist:/in:ro" node:22-bookworm-slim bash -c \
        'apt-get update -qq >/dev/null && apt-get install -y -qq --no-install-recommends libssl3 ca-certificates >/dev/null 2>&1 \
         && cp -r /in /dist && npx --yes @azure/static-web-apps-cli@latest deploy /dist --env production --no-use-keychain' \
        >/dev/null
    else
      npx --yes @azure/static-web-apps-cli@latest deploy "$REPO_ROOT/webApp/dist" \
        --env production --no-use-keychain >/dev/null
    fi
    unset SWA_CLI_DEPLOYMENT_TOKEN
  else
    info "npm not found — skipping dashboard build. Deploy it later with --skip-build."
  fi

  # Now that the hostname exists, allow it through CORS. Patching the app setting rather than
  # re-running the whole template: it restarts just the backend app, which is seconds rather than
  # minutes.
  if [[ "https://$SWA_HOST" != "$CORS_ORIGINS" ]]; then
    info "patching backend CORS allowlist -> https://$SWA_HOST"
    az webapp config appsettings set -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-app" \
      --settings "CORS_ALLOWED_ORIGINS=https://$SWA_HOST" -o none
    # The dashboard also uploads product photos straight to the storage account, so the account's
    # own CORS rules must name the same origin. Management plane (`az resource update`), because
    # shared-key access is off and the data-plane `az storage cors` path would need a key.
    info "patching storage CORS allowlist -> https://$SWA_HOST"
    # --ids, not --resource-type/--name: for a child resource those build the URL without its parent
    # ("storageAccounts/X/default" -> HttpResourceNotFound), and this branch only runs on a first deploy.
    az resource update \
      --ids "$(az storage account show -g "$RESOURCE_GROUP" -n "$STORAGE_ACCOUNT" --query id -o tsv)/blobServices/default" \
      --set "properties.cors.corsRules=[{\"allowedOrigins\":[\"https://$SWA_HOST\"],\"allowedMethods\":[\"GET\",\"HEAD\",\"PUT\",\"OPTIONS\"],\"allowedHeaders\":[\"*\"],\"exposedHeaders\":[\"*\"],\"maxAgeInSeconds\":3600}]" \
      -o none
  fi

  # Changing an app setting restarts the app, and the old process keeps answering until the new one is
  # up -- so the dashboard's CORS rule is only real once a preflight from its origin is allowed. Wait for
  # that, and say what the backend is actually configured with if it never happens.
  info "waiting for the backend to allow https://$SWA_HOST ..."
  allowed=""
  for attempt in $(seq 1 36); do
    allowed=$(curl -s -D - -o /dev/null --max-time 10 -X OPTIONS "$BACKEND_URL/auth/login" \
      -H "Origin: https://$SWA_HOST" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: content-type" \
      | tr -d '\r' | awk -F': ' 'tolower($1)=="access-control-allow-origin"{print $2}') || true
    [[ "$allowed" == "https://$SWA_HOST" || "$allowed" == "*" ]] && { info "CORS allows the dashboard (after ~$((attempt * 10))s)"; break; }
    [[ $attempt -eq 36 ]] && die "the backend never allowed https://$SWA_HOST (Access-Control-Allow-Origin: '${allowed:-<none>}'). App setting now:
  $(az webapp config appsettings list -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-app" --query "[?name=='CORS_ALLOWED_ORIGINS'].value | [0]" -o tsv)"
    sleep 10
  done
fi

# ---------------------------------------------------------------------------------------------
# Verify
# ---------------------------------------------------------------------------------------------
log "Verifying"

info "waiting for backend /health to report build $IMAGE_TAG ..."
# During a rollout the PREVIOUS container keeps serving until the new one is ready, so a 200 alone can
# be the old version. /health names the image tag it was deployed from (X-Build-Id); wait for ours. A new
# container that crash-loops therefore fails here, with what the old one still reports, instead of passing.
seen=""
for attempt in $(seq 1 60); do
  seen=$(curl -s -D - -o /dev/null --max-time 10 "$BACKEND_URL/health" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-build-id"{print $2}') || true
  if [[ "$seen" == "$IMAGE_TAG" ]]; then
    info "backend healthy, running build $IMAGE_TAG (after ~$((attempt * 10))s)"
    break
  fi
  [[ $attempt -eq 60 ]] && die "backend never reported build $IMAGE_TAG (last seen: '${seen:-<none>}'). The previous container may still be serving. Logs:
  az webapp log tail -g $RESOURCE_GROUP -n $NAME_PREFIX-app"
  sleep 10
done

# The image can build and the container can start while still serving baselines only (that is how
# this stack ran before the model was baked in), so "the ML app is up" proves nothing. Ask the
# service what it loaded.
log "Verifying ML model"
ML_FQDN=$(az webapp show -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-ml" --query defaultHostName -o tsv)
ML_URL="https://$ML_FQDN"
ML_PROBE_RULE="deployer-health-check"
ML_PROBE_RULE_ADDED=false

ml_probe_rule_remove() {
  [[ "$ML_PROBE_RULE_ADDED" == true ]] || return 0
  ML_PROBE_RULE_ADDED=false
  if ! az webapp config access-restriction remove -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-ml" \
    --rule-name "$ML_PROBE_RULE" -o none 2>/dev/null; then
    printf '\n\033[1;33mWARNING: could not remove the temporary ML allow rule "%s" (%s/32).\033[0m\n' \
      "$ML_PROBE_RULE" "$MY_IP" >&2
    printf '  Remove it by hand:\n  az webapp config access-restriction remove -g %s -n %s-ml --rule-name %s\n' \
      "$RESOURCE_GROUP" "$NAME_PREFIX" "$ML_PROBE_RULE" >&2
  fi
}

# The allowlist above denies this machine, so open it to our IP for the length of the probe only.
# Only when restrictions exist: adding an Allow rule to an unrestricted app would make it Deny-all.
ML_CAN_PROBE=true
if [[ -n "$PLAN_OUTBOUND_IPS" ]]; then
  if [[ -n "$MY_IP" ]]; then
    # Flag and trap BEFORE the add: `add` can create the rule and still exit non-zero. The trap is
    # chained onto any existing EXIT trap rather than replacing it, and covers error, Ctrl-C and
    # success alike.
    ML_PROBE_RULE_ADDED=true
    existing_exit_trap=$(trap -p EXIT | sed -E "s/^trap -- '(.*)' EXIT$/\1/")
    # shellcheck disable=SC2064
    trap "${existing_exit_trap:+$existing_exit_trap; }ml_probe_rule_remove" EXIT
    az webapp config access-restriction add -g "$RESOURCE_GROUP" -n "$NAME_PREFIX-ml" \
      --rule-name "$ML_PROBE_RULE" --action Allow --priority 90 --ip-address "$MY_IP/32" -o none
  else
    ML_CAN_PROBE=false
  fi
fi

if [[ "$ML_CAN_PROBE" == false ]]; then
  # The deploy itself has rolled out; failing it now would only hide that. Degrade loudly.
  printf '\n\033[1;33mWARNING: ML model NOT verified.\033[0m The service is IP-allowlisted and this machine'"'"'s IP\n' >&2
  printf '  could not be determined. From an allowed address, check:  curl %s/health\n' "$ML_URL" >&2
  printf '  It should report model_loaded:true%s.\n' "$([[ "$ML_EXPECT" == baseline ]] && echo ' (false: baseline-only was requested)')" >&2
else
  ML_BODY=""
  for attempt in $(seq 1 30); do
    ML_BODY=$(curl -fsS --max-time 10 "$ML_URL/health" 2>/dev/null || true)
    if [[ "$ML_EXPECT" == model ]] && grep -Eq '"model_loaded": ?true' <<<"$ML_BODY"; then break; fi
    if [[ "$ML_EXPECT" == baseline ]] && grep -Eq '"model_loaded": ?false' <<<"$ML_BODY"; then break; fi
    if [[ "$ML_EXPECT" == report && -n "$ML_BODY" ]]; then break; fi
    if [[ $attempt -eq 30 ]]; then
      die "ML service /health is not as expected (wanted: $ML_EXPECT). Last response: '${ML_BODY:-<none>}'
  If it says baseline-only, the image was built without ml/data/artifacts; rebuild on a machine that has it.
  az webapp log tail -g $RESOURCE_GROUP -n $NAME_PREFIX-ml"
    fi
    sleep 10
  done
  info "ml service   : $ML_BODY"
  if [[ "$ML_EXPECT" == report ]] && grep -Eq '"model_loaded": ?false' <<<"$ML_BODY"; then
    printf '\n\033[1;33mWARNING: the deployed ML image is baseline-only (this run did not build it).\033[0m\n' >&2
  fi
fi
ml_probe_rule_remove

# Same steps scripts/demo-up.sh runs after the stack is up. Both are idempotent.
if [[ "$IMPORT_LOCAL" == true ]]; then
  log "Demo data (same steps as scripts/demo-up.sh)"
  info "waiting for the backend to reseed the sales history ..."
  for attempt in $(seq 1 60); do
    n=$(azure_psql -Atc "select count(*) from sales" 2>/dev/null || echo 0)
    [[ "${n:-0}" -gt 1000 ]] && break
    [[ $attempt -eq 60 ]] && die "sales history was not seeded after 10 min. Logs: az webapp log tail -g $RESOURCE_GROUP -n $NAME_PREFIX-app"
    sleep 10
  done
  info "sales history: $n sales"
  python3 "$REPO_ROOT/scripts/demo-seed.py" opening-stock "$BACKEND_URL" reference
  python3 "$REPO_ROOT/scripts/demo-seed.py" forecast "$BACKEND_URL" reference
  for _ in $(seq 1 45); do
    [[ "$(azure_psql -Atc 'select count(*) from forecasts' 2>/dev/null || echo 0)" -gt 0 ]] && break
    sleep 2
  done
  info "forecasts: $(azure_psql -Atc "select count(*) || ' rows (' || coalesce(string_agg(distinct model_version, ', '), '-') || ')' from forecasts" 2>/dev/null || echo unknown)"
fi

log "Done"
cat <<EOF

  Backend API     $BACKEND_URL
  Dashboard       ${SWA_HOST:+https://$SWA_HOST}
  Image tag       $IMAGE_TAG
  Region          $LOCATION

  Demo sign-in (seeded by SEED_PROFILE=$SEED_PROFILE):
    tenant   reference
    web      gerente / gerente123      (manager roles only — see webApp/README.md)
    till     cajero  / cajero123
    baker    panadero / panadero123

  Point the clients at it (builds the RELEASE Android app and desktop till, then launches them):
    scripts/prod-up.sh --backend-url $BACKEND_URL        (or deploy with --launch-clients)
    Desktop till   cajero    / cajero123     PIN 1111
    Android baker  panadero  / panadero123   PIN 2222

  Logs      az webapp log tail -g $RESOURCE_GROUP -n $NAME_PREFIX-app
  Reset     az webapp restart -g $RESOURCE_GROUP -n $NAME_PREFIX-app  (re-seeds)
  Queues    az servicebus queue list -g $RESOURCE_GROUP --namespace-name $SB_NAMESPACE -o table
  Pause \$\$  az postgres flexible-server stop -g $RESOURCE_GROUP -n $PG_NAME
  Teardown  ./infra/teardown.sh

EOF

if [[ "$LAUNCH_CLIENTS" == true ]]; then
  log "Launching the clients against this deployment"
  "$REPO_ROOT/scripts/prod-up.sh" --backend-url "$BACKEND_URL"
fi

#!/usr/bin/env bash
#
# Copy the LOCAL demo data (the docker-compose stack's catalog, users, formulas and uploaded product
# photos) into the Azure deployment, leaving it in the same state scripts/demo-up.sh leaves the local
# stack in: catalog + photos kept, transactional data cleared, the sales history reseeded by the
# backend's own boot.
#
#   PG_FQDN=... PG_PASSWORD=... STORAGE_ACCOUNT=... ./infra/import-local.sh
#
# Called by deploy.sh between the foundation and the apps, i.e. BEFORE the backend has ever touched
# the Azure database. The local stack is only read from: its volumes are never modified or removed.
# Needs local Docker (pg_dump/psql run in containers, so no Postgres client is required).
#
# Photos: the dump stores absolute URLs into Azurite; they are rewritten to the storage account's
# public blob URL, and each blob is fetched from the local Azurite and uploaded with the deployer's
# own identity (shared-key access is off on the account).

set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${PG_FQDN:?}" "${PG_PASSWORD:?}" "${STORAGE_ACCOUNT:?}"
RESOURCE_GROUP="${ECOPAN_RG:-rg-ecopan}"
PG_USER="${PG_USER:-ecopanadmin}"
PG_IMAGE="postgres:17-alpine"
LOCAL_BLOB="http://localhost:10000/devstoreaccount1"
CONTAINER="product-images"

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die()  { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

command -v docker >/dev/null && docker info >/dev/null 2>&1 || die "local Docker is not running (needed to read the local data). Start it, or deploy with --no-import."

# psql against a target: "local" is the compose db, anything else is the Azure server.
azure_psql() {
  docker run --rm -i -e PGPASSWORD="$PG_PASSWORD" "$PG_IMAGE" \
    psql "host=$PG_FQDN port=5432 dbname=ecopan user=$PG_USER sslmode=require connect_timeout=10" -v ON_ERROR_STOP=1 -X -q "$@"
}
local_psql() { (cd "$REPO_ROOT" && docker compose exec -T db psql -U postgres -d ecopan -v ON_ERROR_STOP=1 -X -q "$@"); }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

log "Reading the local stack"
(cd "$REPO_ROOT" && docker compose up -d db storage >/dev/null)
for _ in $(seq 1 45); do
  (cd "$REPO_ROOT" && docker compose exec -T db pg_isready -U postgres >/dev/null 2>&1) && break
  sleep 2
done
[[ "$(local_psql -Atc "select to_regclass('public.products') is not null")" == t ]] \
  || die "the local database has no catalog yet. Run scripts/demo-up.sh once (or deploy with --no-import to use the plain reference seed)."
products=$(local_psql -Atc "select count(*) from products")
photos=$(local_psql -Atc "select count(*) from products where image_url is not null")
info "local catalog: $products products, $photos with a photo"

# --no-owner/--no-privileges: the local owner is `postgres`, a role that does not exist on a
# Flexible Server. --clean makes the restore replace whatever is there (nothing, on a first deploy).
(cd "$REPO_ROOT" && docker compose exec -T db pg_dump -U postgres --no-owner --no-privileges --clean --if-exists ecopan) > "$WORK/db.sql"
BLOB_BASE="https://$STORAGE_ACCOUNT.blob.core.windows.net"
sed "s#$LOCAL_BLOB/#$BLOB_BASE/#g" "$WORK/db.sql" > "$WORK/db.azure.sql"
# Only the photos people uploaded live in Azurite; seeded ones are absolute external URLs that need no copy.
local_psql -Atc "select image_url from products where image_url like '$LOCAL_BLOB/%'" > "$WORK/urls.txt"
info "dump: $(du -h "$WORK/db.sql" | cut -f1)"

log "Restoring into Azure PostgreSQL"
azure_psql < "$WORK/db.azure.sql" >/dev/null
info "restored"

log "Clearing transactional data (same SQL as scripts/demo-up.sh)"
azure_psql < "$REPO_ROOT/scripts/demo-purge.sql"
a_products=$(azure_psql -Atc "select count(*) from products")
a_photos=$(azure_psql -Atc "select count(*) from products where image_url is not null")
[[ "$a_products/$a_photos" == "$products/$photos" ]] || die "catalog differs after restore (local $products/$photos, azure $a_products/$a_photos)"
info "catalog intact: $a_products products, $a_photos with a photo"

if [[ -s "$WORK/urls.txt" ]]; then
  log "Copying product photos to $STORAGE_ACCOUNT/$CONTAINER"
  # Shared-key access is off, so uploading is a data-plane operation under the deployer's own login,
  # and Owner/Contributor carry no data actions. Grant the role (needs Owner/User Access
  # Administrator -- the same requirement apps.bicep's role assignments already have) and retry
  # while it propagates.
  scope=$(az storage account show -g "$RESOURCE_GROUP" -n "$STORAGE_ACCOUNT" --query id -o tsv)
  me=$(az ad signed-in-user show --query id -o tsv)
  az role assignment create --assignee-object-id "$me" --assignee-principal-type User \
    --role "Storage Blob Data Contributor" --scope "$scope" -o none 2>/dev/null || true

  while IFS= read -r url; do
    [[ -n "$url" ]] || continue
    key="${url#*/$CONTAINER/}"
    file="$WORK/blob.$$"
    curl -fsS "$LOCAL_BLOB/$CONTAINER/$key" -o "$file" \
      || die "could not read $key from the local Azurite (is the local storage container up on :10000?)"
    case "${key##*.}" in
      png) ctype=image/png ;; jpg|jpeg) ctype=image/jpeg ;; webp) ctype=image/webp ;; *) ctype=application/octet-stream ;;
    esac
    for attempt in $(seq 1 20); do
      if az storage blob upload --account-name "$STORAGE_ACCOUNT" --container-name "$CONTAINER" \
           --name "$key" --file "$file" --content-type "$ctype" --auth-mode login --overwrite \
           --only-show-errors -o none 2>/dev/null; then
        info "uploaded $key"
        break
      fi
      [[ $attempt -eq 20 ]] && die "could not upload $key: the Storage Blob Data Contributor role has not taken effect (or you cannot assign it). Re-run deploy.sh once it has."
      sleep 15
    done
  done < "$WORK/urls.txt"
else
  info "no product photos to copy"
fi

log "Import done -- the backend's first boot will reseed the sales history"

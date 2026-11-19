#!/usr/bin/env bash
#
# evidence-pack.sh -- assemble the defense evidence pack (plan 11, DEMO-3) into docs/defense/evidence/.
#
# What it produces, from the repository as it is *now* (nothing is typed by hand):
#   test-names.txt   the "test-name slide": the names of the tests that pin the claims worth defending
#   config-diff.txt  the genericity proof: the two seeded bakeries differ in configuration, not code
#   results.md       copy of ml/RESULTS.md, the pre-registered evaluation record
#   mocks.txt        which drivers stand in for hardware that is not validated on a physical device
#   test-run.txt     (with --run) a fresh run of the JVM test suites behind those names
#
# What it cannot produce, and the checklist in docs/defense/README.md owns: the screen recording
# and the timed rehearsals. Those need a person.
#
#   scripts/evidence-pack.sh          # collect (seconds)
#   scripts/evidence-pack.sh --run    # also run the suites and record the outcome (minutes)
#
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="docs/defense/evidence"
mkdir -p "$OUT"

# 1. Test names: backtick-quoted Kotlin test names and Vitest/pytest titles, grouped by claim.
{
  echo "# Tests that pin the claims worth defending ($(date +%F))"
  section() { # title, files...
    local title="$1"; shift
    echo; echo "## $title"
    for f in "$@"; do
      [ -f "$f" ] || { echo "(missing: $f)"; continue; }
      echo "- $(basename "$f")"
      { grep -E '^\s*fun `' "$f" || true; } | sed -E 's/^\s*fun `([^`]*)`.*/    · \1/'
    done
  }
  section "Offline-first sync: conflicts, idempotence, recovery" \
    data/sync/impl/src/jvmTest/kotlin/com/f776/ecopan/data/sync/unittest/ConflictPolicyTest.kt \
    data/sync/impl/src/jvmTest/kotlin/com/f776/ecopan/data/sync/unittest/PushWorkerTest.kt \
    data/sync/impl/src/jvmTest/kotlin/com/f776/ecopan/data/sync/unittest/EndToEndBatchSyncTest.kt \
    data/sync/impl/src/jvmTest/kotlin/com/f776/ecopan/data/sync/unittest/OutboxAtomicityTest.kt
  section "Rejected operations: review, retry, discard with record" \
    data/sync/impl/src/commonTest/kotlin/com/f776/ecopan/data/sync/unittest/RejectedOperationsImplTest.kt
  section "Hardware behind mocks and manual entry" \
    $(find data/hardware -path '*Test*' -name '*.kt' -not -path '*/build/*' | sort)
  section "Waste estimate (produced minus sold)" \
    backend/app/src/test/kotlin/com/f776/ecopan/api/WasteEstimateTest.kt
  section "Inventory ledger and reorder levels" \
    backend/app/src/test/kotlin/com/f776/ecopan/api/InventoryRouterTest.kt
  section "Almacén product stock (opening count, computed stock, never blocks a sale)" \
    backend/app/src/test/kotlin/com/f776/ecopan/api/ProductStockRouterTest.kt \
    backend/data/src/test/kotlin/com/f776/ecopan/data/ProductStockRepositoryTest.kt
  section "Seed profiles (every Almacén product counted in every branch)" \
    backend/app/src/test/kotlin/com/f776/ecopan/api/seed/SeedLoaderTest.kt
} > "$OUT/test-names.txt"

# 2. Genericity proof: same code, two bakeries. The diff is the whole difference between them.
{
  echo "# Seed profiles: reference vs unipan ($(date +%F))"
  echo "# A second bakery is a configuration file; the application code is identical."
  echo
  diff -u backend/app/src/main/resources/seed/reference.json backend/app/src/main/resources/seed/unipan.json || true
} > "$OUT/config-diff.txt"

# 3. Evaluation record.
cp ml/RESULTS.md "$OUT/results.md"

# 4. Mocks: the drivers that stand in for hardware, read from the registry rather than described.
{
  echo "# Drivers available per device interface ($(date +%F))"
  echo "# Real drivers exist for the scale, printer and drawer but are NOT validated on a physical device."
  echo "# Mock and manual drivers are what the demo runs on. See docs/hardware/supported-devices.md."
  echo
  grep -hE 'id *= *"|DriverDescriptor\(' data/hardware/impl/src/commonMain/kotlin/com/f776/ecopan/data/hardware/CommonDriverFactories.kt \
    data/hardware/impl/src/jvmMain/kotlin/com/f776/ecopan/data/hardware/jvm/JvmDriverFactories.kt || true
} > "$OUT/mocks.txt"

# 5. Optional fresh run.
if [ "${1:-}" = "--run" ]; then
  {
    echo "# Test run $(date '+%F %T')"
    ./gradlew :data:sync:impl:jvmTest :data:hardware:impl:jvmTest :jvm:feature:pos:impl:jvmTest :feature:settings:impl:jvmTest 2>&1 | tail -5
    (cd webApp && npm test 2>&1 | tail -6)
    ./gradlew :backend:data:test :backend:worker:test :backend:notifier:test 2>&1 | tail -5
  } > "$OUT/test-run.txt"
fi

echo "Evidence pack written to $OUT:"; ls -1 "$OUT"

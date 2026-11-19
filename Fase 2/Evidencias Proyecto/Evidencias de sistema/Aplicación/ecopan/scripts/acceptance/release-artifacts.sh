#!/usr/bin/env bash
#
# Release-artifact acceptance: build a demo-signed release APK against a placeholder backend and check
# scripts/verify-release.sh on it. It never needs a real signing key or a real backend, and it also
# proves the verifier rejects what it should: a debug build, an APK with its signature stripped, and an
# APK that points at a different backend. Run by scripts/acceptance/run.sh --release-artifacts.

set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

URL="https://acceptance.invalid"
RELEASE_APK="android/app/build/outputs/apk/release/app-release.apk"
DEBUG_APK="android/app/build/outputs/apk/debug/app-debug.apk"
WORK="$(mktemp -d)"; trap 'rm -rf "${WORK:?}"' EXIT

ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
die()  { printf '\n\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }
expect_fail() { # description, regex, command...
  local what="$1" pattern="$2"; shift 2; local out code=0
  out="$("$@" 2>&1)" || code=$?
  [ "$code" -ne 0 ] || { echo "$out" | tail -15; die "expected a FAILURE ($what) but it passed"; }
  grep -Eq "$pattern" <<<"$out" || { echo "$out" | tail -15; die "$what failed, but the output does not match /$pattern/"; }
  ok "$what: rejected as it should"
}

eval "$(scripts/make-demo-keystore.sh)"
./gradlew --console=plain -q :android:app:assembleRelease :android:app:assembleDebug \
  -Pecopan.backendUrl="$URL" -Pecopan.devQuickLogin=false -Pecopan.requireSigning=true
scripts/verify-release.sh --apk "$RELEASE_APK" --expect-url "$URL"
ok "demo-signed release APK passes"

expect_fail "a debug-signed APK" "FAIL" scripts/verify-release.sh --apk "$DEBUG_APK" --expect-url "$URL"

cp "$RELEASE_APK" "$WORK/stripped.apk"
zip -q -d "$WORK/stripped.apk" 'META-INF/*' || true
expect_fail "an APK with its signature stripped" "FAIL" scripts/verify-release.sh --apk "$WORK/stripped.apk" --expect-url "$URL"

expect_fail "an APK that points at another backend" "FAIL" scripts/verify-release.sh --apk "$RELEASE_APK" --expect-url "https://other.invalid"

# The data-extraction rules the manifest points at, removed: Android 12+ would then back up and transfer everything.
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
rules_ref="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$RELEASE_APK" | sed -n -E 's/.*:dataExtractionRules\([^)]*\)=@(0x[0-9a-f]+).*/\1/p')"
rules_file="$("$AAPT2" dump resources "$RELEASE_APK" | grep -A1 "resource $rules_ref " | sed -n -E 's/.*\(file\) ([^ ]+) .*/\1/p')"
[ -n "$rules_file" ] || die "the release APK has no data-extraction rules file to remove"
cp "$RELEASE_APK" "$WORK/no-rules.apk"
zip -q -d "$WORK/no-rules.apk" "$rules_file"
expect_fail "an APK without its data-extraction rules" "FAIL.*data-extraction rules" \
  scripts/verify-release.sh --apk "$WORK/no-rules.apk" --expect-url "$URL"

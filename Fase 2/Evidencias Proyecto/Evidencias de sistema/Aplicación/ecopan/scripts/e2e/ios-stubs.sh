#!/usr/bin/env bash
# Empty stand-ins for the Firebase frameworks gitlive's Crashlytics klib asks the linker for (only the iosApp host
# provides the real ones). The baker journeys never call Crashlytics; see mobile/e2e/build.gradle.kts.
set -Eeuo pipefail
OUT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/build/ios-stubs"
SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"
rm -rf "$OUT"; mkdir -p "$OUT"; echo "" > "$OUT/empty.c"
for f in FirebaseCore FirebaseCrashlytics FirebaseInstallations FirebaseCoreInternal GoogleUtilities FBLPromises; do
  mkdir -p "$OUT/$f.framework"
  xcrun clang -dynamiclib -arch arm64 -isysroot "$SDK" -mios-simulator-version-min=15.0 \
    -o "$OUT/$f.framework/$f" "$OUT/empty.c" -install_name "$OUT/$f.framework/$f"
done

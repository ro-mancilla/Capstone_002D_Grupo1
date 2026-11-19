#!/usr/bin/env bash
#
# A THROWAWAY Android signing key, for rehearsing the release pipeline. Not a release key: its password
# is public (it is in this file), so an APK signed with it proves the pipeline works and nothing else.
# Store uploads need a real key, supplied through the same environment variables as CI secrets.
#
#   eval "$(scripts/make-demo-keystore.sh)"      # creates the key once, exports the four variables
#   ./gradlew :android:app:assembleRelease -Pecopan.backendUrl=... -Pecopan.devQuickLogin=false -Pecopan.requireSigning=true
#
# The keystore lives outside the repository (default ~/.ecopan-demo/ecopan-demo.jks, ECOPAN_DEMO_HOME
# overrides), so there is nothing to ignore and nothing to commit. Idempotent.

set -Eeuo pipefail

DIR="${ECOPAN_DEMO_HOME:-$HOME/.ecopan-demo}"
KS="$DIR/ecopan-demo.jks"
PASS="ecopan-demo"
ALIAS="ecopan-demo"

command -v keytool >/dev/null || { echo "keytool not found (it ships with the JDK)" >&2; exit 1; }
mkdir -p "$DIR"
if [[ ! -f "$KS" ]]; then
  keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=EcoPan demo (not a release key), O=GreatTusk" >&2
  echo "created $KS" >&2
else
  echo "using $KS" >&2
fi

# stdout is meant for `eval`; everything above went to stderr.
cat <<ENV
export ECOPAN_KEYSTORE_FILE='$KS'
export ECOPAN_KEYSTORE_PASSWORD='$PASS'
export ECOPAN_KEY_ALIAS='$ALIAS'
export ECOPAN_KEY_PASSWORD='$PASS'
ENV

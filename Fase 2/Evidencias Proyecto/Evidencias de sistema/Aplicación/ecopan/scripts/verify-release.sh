#!/usr/bin/env bash
#
# Acceptance check on a BUILT release artifact -- what was produced, not how it was built.
#
#   scripts/verify-release.sh --apk android/app/build/outputs/apk/release/app-release.apk \
#       --expect-url https://ecopan-app.azurewebsites.net
#   scripts/verify-release.sh --dmg jvm/app/build/compose/binaries/main-release/dmg/EcoPan-1.0.33.dmg \
#       --expect-url https://ecopan-app.azurewebsites.net
#
# Every check runs; the exit status is non-zero if any failed.
#
#   APK  signed with a non-debug certificate; backup disabled and data-extraction rules excluding everything from
#        cloud backup and device transfer (the sign-in tokens must not be copied off the device); the mapping file exists and obfuscation is on (>= 90% of
#        classes renamed); the requested backend URL is baked in; the development default
#        (http://localhost:8080) is not; the version matches version.properties.
#   DMG  the app is named EcoPan with id com.f776.ecopan; the version matches version.properties; the
#        requested backend URL is baked in; the development default is not.
#
# Flags: --expect-url URL   the backend the artifact must point at
#        --allow-localhost  a deliberate local rehearsal: the development default is allowed (and URL optional)
#        --mapping PATH     default android/app/build/outputs/mapping/release/mapping.txt
#
# Needs: unzip, strings, python3; apksigner and aapt2 from the Android SDK build-tools for an APK
# (ANDROID_HOME, default ~/Library/Android/sdk); hdiutil and plutil for a DMG (macOS).

set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="" DMG="" EXPECT_URL="" ALLOW_LOCAL=false MAPPING="$ROOT/android/app/build/outputs/mapping/release/mapping.txt"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) APK="$2"; shift 2 ;;
    --dmg) DMG="$2"; shift 2 ;;
    --expect-url) EXPECT_URL="$2"; shift 2 ;;
    --allow-localhost) ALLOW_LOCAL=true; shift ;;
    --mapping) MAPPING="$2"; shift 2 ;;
    -h|--help) sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$APK" || -n "$DMG" ]] || { echo "pass --apk or --dmg (see --help)" >&2; exit 2; }
[[ -n "$EXPECT_URL" || "$ALLOW_LOCAL" == true ]] || { echo "pass --expect-url URL (or --allow-localhost for a local rehearsal)" >&2; exit 2; }

FAILED=0
pass() { printf '  PASS  %s\n' "$*"; }
fail() { printf '  FAIL  %s\n' "$*"; FAILED=$((FAILED + 1)); }
VERSION="$(sed -n 's/^versionMajor=//p' "$ROOT/version.properties").$(sed -n 's/^versionMinor=//p' "$ROOT/version.properties").$(sed -n 's/^versionPatch=//p' "$ROOT/version.properties")"
DEV_DEFAULT="http://localhost:8080"

# check_url reads the artifact's text on stdin. Call it with a here-string, NEVER at the end of a pipe:
# a pipeline's last command runs in a subshell and the failure count would be lost.
check_url() {
  local text; text="$(cat)"
  if [[ -n "$EXPECT_URL" ]]; then
    grep -qF "$EXPECT_URL" <<<"$text" && pass "the backend URL $EXPECT_URL is baked in" || fail "the backend URL $EXPECT_URL is not in the artifact"
  fi
  if [[ "$ALLOW_LOCAL" == false ]]; then
    grep -qF "$DEV_DEFAULT" <<<"$text" && fail "the development default $DEV_DEFAULT is baked in" || pass "no development default ($DEV_DEFAULT) baked in"
  fi
}

# Reads aapt2's xmltree of the data-extraction rules on stdin; prints what is wrong, exits 1 if anything is.
IFS= read -r -d '' RULES_CHECK <<'PY' || true
import re, sys
# aapt2's xmltree: "E: <element>" and "A: domain=\"root\"" lines, nested by indentation.
section, found = None, {}
for line in sys.stdin:
    if m := re.match(r"\s*E: (cloud-backup|device-transfer)\b", line):
        section = m.group(1); found.setdefault(section, {"exclude": set(), "include": False})
    elif m := re.match(r"\s*E: (cross-platform-transfer|data-extraction-rules)\b", line):
        section = None
    elif section and re.match(r"\s*E: include\b", line):
        found[section]["include"] = True
    elif section and (m := re.search(r'A: domain="([a-z_]+)"', line)):
        found[section]["exclude"].add(m.group(1))
need = {"root", "device_root", "external"}
problems = []
for name in ("cloud-backup", "device-transfer"):
    s = found.get(name)
    if s is None: problems.append(f"no <{name}> section")
    elif s["include"]: problems.append(f"<{name}> includes something")
    elif not need <= s["exclude"]: problems.append(f"<{name}> does not exclude {', '.join(sorted(need - s['exclude']))}")
print("; ".join(problems))
sys.exit(1 if problems else 0)
PY

if [[ -n "$APK" ]]; then
  echo "== APK: $APK"
  [[ -f "$APK" ]] || { fail "no such file"; exit 1; }
  SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
  AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"

  if [[ -z "$APKSIGNER" ]]; then fail "apksigner not found under $SDK/build-tools"
  elif certs="$("$APKSIGNER" verify --print-certs "$APK" 2>&1)"; then
    dn="$(sed -n -E 's/^(V[0-9]+ Signer: certificate DN|Signer #1 certificate DN): //p' <<<"$certs" | head -1)"
    if [[ -z "$dn" ]]; then fail "signature verifies but no certificate could be read from apksigner's output (cannot tell the debug key from a real one)"
    elif grep -qi "Android Debug" <<<"$dn"; then fail "signed with the DEBUG key ($dn)"
    else pass "signature verifies, certificate: $dn"; fi
  else
    fail "not signed / signature does not verify: $(grep -vE '^WARNING' <<<"$certs" | grep -v '^$' | head -1)"
  fi

  if [[ -f "$MAPPING" ]]; then
    python3 -I - "$MAPPING" <<'PY' && pass "obfuscation is on" || fail "obfuscation is (mostly) off"
import sys
total = renamed = 0
for line in open(sys.argv[1]):
    if line.startswith(("#", " ")) or "->" not in line:
        continue
    cls, _, new = line.rstrip(":\n").partition(" -> ")
    total += 1
    renamed += cls != new
ratio = 100 * renamed / max(total, 1)
print(f"        {renamed}/{total} classes renamed ({ratio:.0f}%)")
sys.exit(0 if ratio >= 90 else 1)
PY
  else
    fail "no mapping file at $MAPPING (was this built with R8?)"
  fi

  dex_text="$(unzip -p "$APK" 'classes*.dex' | strings -a)"
  check_url <<<"$dex_text"

  if [[ -n "$AAPT2" ]]; then
    got="$("$AAPT2" dump badging "$APK" 2>/dev/null | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
    [[ "$got" == "$VERSION" ]] && pass "versionName $got matches version.properties" || fail "versionName '$got' != version.properties $VERSION"
  else
    fail "aapt2 not found: cannot read the APK's version"
  fi

  # allowBackup defaults to true when the attribute is absent, so absence is a failure too.
  if [[ -n "$AAPT2" ]]; then
    backup="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null | sed -n -E 's/.*:allowBackup\([^)]*\)=//p' | head -1)"
    case "$backup" in
      false|"(type 0x12)0x0") pass "backup is disabled (allowBackup=false)" ;;
      "")        fail "the manifest does not set allowBackup, which defaults to true: tokens would be backed up" ;;
      *)         fail "allowBackup is on ($backup): the sign-in tokens would be copied by device backup" ;;
    esac
  fi

  # Android 12+ ignores allowBackup for device-to-device transfer: only data-extraction rules that
  # exclude everything keep the device session on this phone (#130).
  if [[ -z "$AAPT2" ]]; then
    fail "aapt2 not found: cannot check backup or data-extraction rules"
  else
    rules_ref="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null | sed -n -E 's/.*:dataExtractionRules\([^)]*\)=@(0x[0-9a-f]+).*/\1/p' | head -1)"
    if [[ -z "$rules_ref" ]]; then
      fail "the manifest sets no dataExtractionRules: Android 12+ would copy the sign-in tokens by cloud backup or device transfer"
    else
      rules_file="$("$AAPT2" dump resources "$APK" 2>/dev/null | grep -A1 "resource $rules_ref " | sed -n -E 's/.*\(file\) ([^ ]+) .*/\1/p' | head -1)"
      rules_xml="$([[ -n "$rules_file" ]] && "$AAPT2" dump xmltree --file "$rules_file" "$APK" 2>/dev/null || true)"
      if verdict="$(python3 -I -c "$RULES_CHECK" <<<"$rules_xml")"; then
        pass "data-extraction rules exclude everything from cloud backup and device transfer"
      else
        fail "the data-extraction rules ${rules_file:-($rules_ref)} do not exclude everything: ${verdict:-file unreadable}"
      fi
    fi
  fi
fi

if [[ -n "$DMG" ]]; then
  echo "== DMG: $DMG"
  [[ -f "$DMG" ]] || { fail "no such file"; exit 1; }
  WORK="$(mktemp -d)"; MNT="$WORK/mnt"; mkdir -p "$MNT"
  cleanup() { hdiutil detach "$MNT" >/dev/null 2>&1 || true; rm -rf "${WORK:?}"; }
  trap cleanup EXIT
  hdiutil attach -nobrowse -readonly -mountpoint "$MNT" "$DMG" >/dev/null 2>&1 || { fail "cannot mount the image"; exit 1; }
  APP="$(ls -d "$MNT"/*.app 2>/dev/null | head -1)"
  [[ -n "$APP" ]] || { fail "no .app inside the image"; exit 1; }
  plist="$APP/Contents/Info.plist"
  name="$(plutil -extract CFBundleName raw "$plist" 2>/dev/null || true)"
  id="$(plutil -extract CFBundleIdentifier raw "$plist" 2>/dev/null || true)"
  ver="$(plutil -extract CFBundleShortVersionString raw "$plist" 2>/dev/null || true)"
  [[ "$name" == "EcoPan" ]] && pass "app name is $name" || fail "app name is '$name', expected EcoPan"
  [[ "$id" == "com.f776.ecopan" ]] && pass "bundle id is $id" || fail "bundle id is '$id'"
  [[ "$ver" == "$VERSION" ]] && pass "version $ver matches version.properties" || fail "version '$ver' != version.properties $VERSION"
  jar_text="$(for jar in "$APP"/Contents/app/*.jar; do unzip -p "$jar" 2>/dev/null; done | strings -a)"
  check_url <<<"$jar_text"
fi

echo
if [[ $FAILED -eq 0 ]]; then echo "== release artifact OK"; else echo "== $FAILED check(s) FAILED"; fi
exit $(( FAILED > 0 ))

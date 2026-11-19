#!/usr/bin/env bash
#
# Regenerate every EcoPan app icon from branding/icon/bakery_dining.svg (Google's Material
# "bakery_dining", Apache-2.0) in the design system's crust brown and cream.
#
#   branding/icon/build-icons.sh
#
# Writes into place, overwriting:
#   android/app/src/main/res/drawable/ic_launcher_foreground.xml   adaptive icon foreground (also the monochrome layer)
#   android/app/src/main/res/values/*.xml                          ic_launcher_background / _foreground colours
#   android/app/src/main/res/mipmap-*dpi/ic_launcher{,_round}.webp  icons for Android before 8.0
#   android/app/src/main/ic_launcher-playstore.png                  Play Store 512 px (also the Linux package icon)
#   iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/ic_launcher-appstore.png   1024 px, opaque (App Store forbids alpha)
#   jvm/app/ic_launcher-playstore.icns                              macOS app icon
#   composeApp/ic_launcher-playstore.ico                            Windows app icon
#   webApp/public/favicon.svg                                       dashboard favicon
#
# Needs: node + npm (the renderer, @resvg/resvg-js, is installed into a temporary directory, never
# the repo), python3 with Pillow (opaque iOS PNG, .ico), cwebp (Android .webp), and on macOS sips +
# iconutil (.icns; skipped elsewhere with a note).
#
# To change the colours, edit BG / FG in build-icons.mjs (they mirror `crust` / `crustSoft` in
# core/designsystem's Color.kt). To change the glyph, replace bakery_dining.svg with another
# Material icon: the placement constants in build-icons.mjs describe its bounding box.

set -Eeuo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"

for tool in node npm python3 cwebp; do
  command -v "$tool" >/dev/null || { echo "missing: $tool" >&2; exit 1; }
done
python3 -c "import PIL" 2>/dev/null || { echo "missing: python3 Pillow (pip install pillow)" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# ESM resolves imports from the script's own directory, so the script runs from the temp dir where
# the renderer is installed.
cp "$HERE/build-icons.mjs" "$HERE/bakery_dining.svg" "$WORK/"
(cd "$WORK" && npm init -y >/dev/null && npm install --silent --no-audit --no-fund @resvg/resvg-js@2.6.2 >/dev/null)
(cd "$WORK" && node build-icons.mjs "$WORK/png")
P="$WORK/png"

# --- Android ----------------------------------------------------------------------------------
RES=android/app/src/main/res
cp "$P/android-foreground.xml" "$RES/drawable/ic_launcher_foreground.xml"
COLORS="$(grep -l 'ic_launcher_background' "$RES"/values/*.xml | head -1)"
sed -i '' -E 's|(<color name="ic_launcher_background">)[^<]*|\1#7A4A22|; s|(<color name="ic_launcher_foreground">)[^<]*|\1#FBF1E8|' "$COLORS"
for density in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  d="${density%%:*}"; px="${density##*:}"
  cwebp -quiet -q 92 "$P/legacy-$px.png" -o "$RES/mipmap-$d/ic_launcher.webp"
  cwebp -quiet -q 92 "$P/legacy-round-$px.png" -o "$RES/mipmap-$d/ic_launcher_round.webp"
done
cp "$P/full-512.png" android/app/src/main/ic_launcher-playstore.png

# --- iOS: opaque ------------------------------------------------------------------------------
python3 - "$P/full-1024.png" "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/ic_launcher-appstore.png" <<'PY'
import sys
from PIL import Image
Image.open(sys.argv[1]).convert("RGB").save(sys.argv[2])
PY

# --- Desktop ----------------------------------------------------------------------------------
python3 - "$P/rounded-256.png" composeApp/ic_launcher-playstore.ico <<'PY'
import sys
from PIL import Image
Image.open(sys.argv[1]).save(sys.argv[2], format="ICO", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
PY
if command -v iconutil >/dev/null; then
  SET="$WORK/EcoPan.iconset"; mkdir -p "$SET"
  cp "$P/rounded-16.png"   "$SET/icon_16x16.png";      cp "$P/rounded-32.png"   "$SET/icon_16x16@2x.png"
  cp "$P/rounded-32.png"   "$SET/icon_32x32.png";      cp "$P/rounded-64.png"   "$SET/icon_32x32@2x.png"
  cp "$P/rounded-128.png"  "$SET/icon_128x128.png";    cp "$P/rounded-256.png"  "$SET/icon_128x128@2x.png"
  cp "$P/rounded-256.png"  "$SET/icon_256x256.png";    cp "$P/rounded-512.png"  "$SET/icon_256x256@2x.png"
  cp "$P/rounded-512.png"  "$SET/icon_512x512.png";    cp "$P/rounded-1024.png" "$SET/icon_512x512@2x.png"
  iconutil -c icns "$SET" -o jvm/app/ic_launcher-playstore.icns
else
  echo "note: iconutil not found (not macOS) -- jvm/app/ic_launcher-playstore.icns left as it was" >&2
fi

# --- Web --------------------------------------------------------------------------------------
cp "$P/favicon.svg" webApp/public/favicon.svg

echo "icons regenerated. Review with: git status --short android iosApp jvm composeApp webApp"

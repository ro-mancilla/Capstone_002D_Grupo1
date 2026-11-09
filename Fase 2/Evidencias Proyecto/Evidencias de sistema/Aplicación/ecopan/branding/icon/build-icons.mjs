// Renders every EcoPan app icon from one source: Google's Material "bakery_dining" glyph
// (bakery_dining.svg, Apache-2.0, https://github.com/google/material-design-icons) in the design
// system's cream on its crust brown. Run through build-icons.sh, which installs the renderer into a
// temporary directory and then converts these PNGs into .icns / .ico / .webp.
//
//   node build-icons.mjs <output-dir>
//
// Writes, into <output-dir>:
//   full-<size>.png        opaque full-bleed square: Play Store, iOS (alpha is stripped afterwards)
//   rounded-<size>.png     inset rounded square, the macOS / Windows / Linux look
//   legacy-<size>.png      rounded launcher shape for Android < 8, dpi sizes
//   legacy-round-<size>.png  the round variant
//   android-foreground.xml the adaptive-icon foreground vector (the glyph, centred in the safe zone)
//   favicon.svg            the dashboard's favicon
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Resvg } from '@resvg/resvg-js'

const here = dirname(fileURLToPath(import.meta.url))
const out = process.argv[2]
if (!out) throw new Error('usage: node build-icons.mjs <output-dir>')
mkdirSync(out, { recursive: true })

// Colours: core/designsystem theme `crust` (background) and `crustSoft` (glyph).
const BG = '#7A4A22'
const FG = '#FBF1E8'

// The glyph occupies roughly x 3..21, y 7..16.5 of its 24 unit box: 18 wide, centred at (12, 11.75).
const GLYPH_WIDTH = 18
const CENTER = { x: 12, y: 11.75 }
const source = readFileSync(join(here, 'bakery_dining.svg'), 'utf8')
// Every filled path except the invisible 24x24 bounding rect that Material ships beside it.
const pathData = [...source.matchAll(/<path[^>]*\sd="([^"]+)"/g)].map((m) => m[1])
if (pathData.length === 0) throw new Error('no glyph path found in bakery_dining.svg')
const glyphPaths = pathData.map((d) => `<path d="${d}" fill-rule="evenodd"/>`).join('')

/** The glyph centred in a `size` square, `fraction` of the square wide. */
const glyph = (size, fraction, fill = FG) => {
  const k = (fraction * size) / GLYPH_WIDTH
  return `<g fill="${fill}" transform="translate(${size / 2} ${size / 2}) scale(${k}) translate(${-CENTER.x} ${-CENTER.y})">${glyphPaths}</g>`
}
const svg = (size, body) =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">${body}</svg>`
const png = (markup, size) =>
  new Resvg(markup, { fitTo: { mode: 'width', value: size } }).render().asPng()

for (const size of [16, 32, 48, 64, 72, 96, 128, 144, 192, 256, 512, 1024]) {
  // Full bleed: the platform draws the mask (Play Store, iOS), so the glyph keeps clear of the corners.
  writeFileSync(join(out, `full-${size}.png`), png(svg(size, `<rect width="${size}" height="${size}" fill="${BG}"/>${glyph(size, 0.56)}`), size))

  // macOS-style: a rounded square inset to 80.5% of the canvas, which is how Big Sur icons sit in the Dock.
  const inset = size * 0.0975
  const side = size * 0.805
  writeFileSync(
    join(out, `rounded-${size}.png`),
    png(svg(size, `<rect x="${inset}" y="${inset}" width="${side}" height="${side}" rx="${side * 0.225}" fill="${BG}"/>${glyph(size, 0.46)}`), size),
  )

  // Android before 8.0 has no adaptive icons: the launcher shows this image as is.
  writeFileSync(
    join(out, `legacy-${size}.png`),
    png(svg(size, `<rect width="${size}" height="${size}" rx="${size * 0.18}" fill="${BG}"/>${glyph(size, 0.6)}`), size),
  )
  writeFileSync(
    join(out, `legacy-round-${size}.png`),
    png(svg(size, `<circle cx="${size / 2}" cy="${size / 2}" r="${size / 2}" fill="${BG}"/>${glyph(size, 0.56)}`), size),
  )
}

// Adaptive icon foreground: 108dp layer, only the centre 66dp is guaranteed visible, so the glyph is
// 54dp wide. Group transform = scale, then translate, putting the glyph centre on (12, 12).
const dp = 108
const viewport = 24
const s = 54 / (GLYPH_WIDTH * (dp / viewport))
const tx = 12 - CENTER.x * s
const ty = 12 - CENTER.y * s
writeFileSync(
  join(out, 'android-foreground.xml'),
  `<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${dp}dp"
    android:height="${dp}dp"
    android:viewportWidth="${viewport}"
    android:viewportHeight="${viewport}">
  <group android:scaleX="${s.toFixed(4)}"
         android:scaleY="${s.toFixed(4)}"
         android:translateX="${tx.toFixed(4)}"
         android:translateY="${ty.toFixed(4)}">
${pathData
  .map(
    (d) => `    <path
        android:fillColor="@color/ic_launcher_foreground"
        android:fillType="evenOdd"
        android:pathData="${d.replace(/\s+/g, ' ')}"/>`,
  )
  .join('\n')}
  </group>
</vector>
`,
)

// Favicon: the same mark, as a vector so it stays sharp on any tab.
writeFileSync(
  join(out, 'favicon.svg'),
  `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><rect width="32" height="32" rx="7" fill="${BG}"/>${glyph(32, 0.66)}</svg>\n`,
)
console.log(`wrote icons to ${out}`)

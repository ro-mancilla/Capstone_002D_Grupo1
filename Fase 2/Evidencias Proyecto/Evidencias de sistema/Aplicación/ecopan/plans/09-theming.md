# 09 — Theming: the web token set → Compose Material3

Single visual identity across all clients (01 §9). **`webApp/src/index.css` is the canonical token set** (see §7 — the original `spec/index.css` is gitignored scratch and cannot serve as one); this document maps it onto Material3 for `:core:ui`. See §7.1 for what is shared across clients and what deliberately is not. Zero production code here — implementation agents verify every API via context7 before writing (`lightColorScheme`/`darkColorScheme` param names and the Compose resources `Font` API were verified against Compose MP 1.11.1 / M3 1.11.0-alpha07 while drafting this doc; re-verify on implementation).

**Current state (what gets replaced):** `core/ui/src/commonMain/kotlin/com/f776/core/ui/theme/` — `Color.kt` + `Theme.kt` hold a MaterialKolor-*generated* purple seed scheme (seed `0xFF904EEC`, static pasted values, no runtime MaterialKolor dependency in `:core:ui`). `Type.kt` bundles Figtree + Roboto via Compose resources. `EcopanTheme.kt` is `expect`, with `androidMain` (dynamic color + edge-to-edge side effects), `iosMain`, `jvmMain` actuals all calling `MaterialExpressiveTheme(colorScheme, appTypography(), content)` — no custom `Shapes` today. `AppTheme` (`data/preferences/api/.../AppTheme.kt`) is already `LIGHT | DARK | SYSTEM`; `ThemeSelectionDialog` (feature/settings) already offers exactly those three.

---

## 1. Color conversion route

`index.css` colors are `oklch(L C H)`. Compose needs sRGB `Color(0xFFRRGGBB)`.

- **Route:** oklch → OKLab → linear sRGB → gamma-encoded sRGB, clamp to gamut, round to 8-bit. Reference math: Björn Ottosson's OKLab spec (the same math CSS Color 4 mandates).
- **Verification tool for the implementing agent:** re-check every hex below with the `culori` npm package (`formatHex(oklch(...))`), or oklch.com, or the Python snippet kept at the drafting session's method: OKLab matrices + `12.92x / 1.055x^(1/2.4)−0.055` transfer. Do **not** eyeball-convert or trust a random converter that clips differently — all values below are in-gamut except none (no clipping occurred), so any correct converter must reproduce them exactly (±1/255 rounding).
- Values below were computed with that script; treat them as authoritative once re-verified, then hardcode in `Color.kt` with a comment carrying the source oklch triplet per value.

## 2. Token mapping table (the registry)

Legend: **css** = `index.css` variable; **derived** = not in css, tone derived in the stated hue at the stated L/C (oklch triplet given) — these must also be re-converted, not invented again.

### 2.1 Direct css tokens, converted

| css variable | light hex | dark hex |
|---|---|---|
| `--background` | `#F5F1E6` | `#2D2621` |
| `--foreground` | `#4A3F35` | `#ECE5D8` (= light `--muted` value) |
| `--card` / `--popover` | `#FFFCF5` | `#3A322C` |
| `--card-foreground` / `--popover-foreground` | `#4A3F35` | `#ECE5D8` |
| `--primary` / `--ring` | `#A67C52` | `#C0A080` (= `--chart-5`) |
| `--primary-foreground` | `#FFFFFF` | `#2D2621` |
| `--secondary` | `#E2D8C3` | `#4A4039` |
| `--secondary-foreground` | `#5C4D3F` | `#ECE5D8` |
| `--muted` | `#ECE5D8` | `#312B26` |
| `--muted-foreground` | `#7D6B56` | `#C5BCAC` |
| `--accent` | `#D4C8AA` | `#59493E` |
| `--accent-foreground` | `#4A3F35` | `#ECE5D8` |
| `--destructive` | `#B54A35` | `#B54A35` (css keeps it identical) |
| `--destructive-foreground` | `#FFFFFF` | `#FFFFFF` |
| `--border` / `--input` | `#DBD0BA` | `#4A4039` |
| `--chart-1` | `#A67C52` | `#C0A080` |
| `--chart-2` | `#8D6E4C` | `#B3906F` |
| `--chart-3` | `#735A3A` | `#A67C52` |
| `--chart-4` | `#B3906F` | `#8D6E4C` |
| `--chart-5` | `#C0A080` | `#735A3A` |
| `--sidebar*` | *(not mapped — web-only shadcn concept; Compose nav surfaces use the surfaceContainer ladder below)* | — |

### 2.2 css → Material3 `ColorScheme` slot mapping

| M3 slot | light | dark | source / rationale |
|---|---|---|---|
| `background` | `#F5F1E6` | `#2D2621` | css `--background` |
| `onBackground` | `#4A3F35` | `#ECE5D8` | css `--foreground` |
| `surface` | `#F5F1E6` | `#2D2621` | = background (M3 convention; css page bg) |
| `onSurface` | `#4A3F35` | `#ECE5D8` | css `--foreground` |
| `primary` | `#A67C52` | `#C0A080` | css `--primary` (dark css primary = chart-5 tone) |
| `onPrimary` | `#FFFFFF` | `#2D2621` | css `--primary-foreground` |
| `primaryContainer` | `#EFD2B5` | `#6C4E30` | **derived**: oklch(0.88 0.05 66) / oklch(0.45 0.06 66) — primary hue, container tones |
| `onPrimaryContainer` | `#472C0C` | `#EFD2B5` | **derived**: oklch(0.32 0.06 66) / reuse light container tone |
| `inversePrimary` | `#C0A080` | `#A67C52` | the other mode's primary |
| `secondary` | `#5C4D3F` | `#C5BCAC` | css `--secondary-foreground` (light) / `--muted-foreground` (dark) — see §2.3 |
| `onSecondary` | `#FFFFFF` | `#2D2621` | contrast pick |
| `secondaryContainer` | `#E2D8C3` | `#4A4039` | css `--secondary` — shadcn "secondary" *is* a container (light chip + dark text) |
| `onSecondaryContainer` | `#5C4D3F` | `#ECE5D8` | css `--secondary-foreground` |
| `tertiary` | `#7D7050` | `#C9BD9D` | **derived**: oklch(0.55 0.05 88.8) / oklch(0.80 0.045 88.8) — accent hue at accent-strength tone |
| `onTertiary` | `#FFFFFF` | `#2D2621` | contrast pick |
| `tertiaryContainer` | `#D4C8AA` | `#59493E` | css `--accent` — the accent *is* a container tone |
| `onTertiaryContainer` | `#4A3F35` | `#ECE5D8` | css `--accent-foreground` |
| `error` | `#B54A35` | `#B54A35` | css `--destructive` (css deliberately keeps it in dark; honor it — do not brighten) |
| `onError` | `#FFFFFF` | `#FFFFFF` | css `--destructive-foreground` |
| `errorContainer` | `#FFD0C5` | `#642114` | **derived**: oklch(0.90 0.06 33) / oklch(0.35 0.10 33) |
| `onErrorContainer` | `#5C0400` | `#FFD9D0` | **derived**: oklch(0.30 0.12 33) / oklch(0.92 0.05 33) |
| `surfaceVariant` | `#ECE5D8` | `#312B26` | css `--muted` |
| `onSurfaceVariant` | `#7D6B56` | `#C5BCAC` | css `--muted-foreground` |
| `outline` | `#7D6B56` | `#7B6F66` | light: css `--muted-foreground` (M3 outline needs ~3:1 for text-field borders; `--border` is too faint); dark: **derived** oklch(0.55 0.02 57) |
| `outlineVariant` | `#DBD0BA` | `#4A4039` | css `--border`/`--input` — hairlines/dividers, matches shadcn border weight |
| `surfaceTint` | `#A67C52` | `#C0A080` | = primary |
| `scrim` | `#000000` | `#000000` | M3 default |
| `inverseSurface` | `#2D2621` | `#F5F1E6` | other mode's background |
| `inverseOnSurface` | `#ECE5D8` | `#4A3F35` | other mode's foreground |
| `surfaceDim` | `#DBD0BA` | `#2D2621` | light: css `--border` tone as dimmest neutral; dark: = surface |
| `surfaceBright` | `#FFFCF5` | `#625246` | light: css `--card`; dark: **derived** oklch(0.45 0.028 56.34) |
| `surfaceContainerLowest` | `#FFFFFF` | `#261F1A` | light: pure white; dark: **derived** oklch(0.245 0.0139 57.65) (below bg) |
| `surfaceContainerLow` | `#FFFCF5` | `#312B26` | light: css `--card`; dark: css `--muted` |
| `surfaceContainer` | `#F5F1E6` | `#3A322C` | light: css `--background`; dark: css `--card` |
| `surfaceContainerHigh` | `#ECE5D8` | `#4A4039` | light: css `--muted`; dark: css `--secondary` |
| `surfaceContainerHighest` | `#E2D8C3` | `#59493E` | light: css `--secondary`; dark: css `--accent` |
| `primaryFixed` / `primaryFixedDim` | `#EFD2B5` / `#C0A080` | same (fixed = mode-invariant) | light container tone / chart-5 tone |
| `onPrimaryFixed` / `onPrimaryFixedVariant` | `#472C0C` / `#634628` | same | derived oklch(0.32/0.42 0.06 66) |
| `secondaryFixed` / `secondaryFixedDim` | `#E2D8C3` / `#CDC3AF` | same | css `--secondary` / derived oklch(0.82 0.03 85.57) |
| `onSecondaryFixed` / `onSecondaryFixedVariant` | `#5C4D3F` / `#706052` | same | css `--secondary-foreground` / derived oklch(0.50 0.03 64.93) |
| `tertiaryFixed` / `tertiaryFixedDim` | `#D4C8AA` / `#C2B799` | same | css `--accent` / derived oklch(0.78 0.0426 88.8) |
| `onTertiaryFixed` / `onTertiaryFixedVariant` | `#4A3F35` / `#635548` | same | css `--accent-foreground` / derived oklch(0.46 0.028 64.34) |

### 2.3 Reasoning worth keeping (defense material)

- **background/surface:** shadcn draws everything on `--background`; M3 splits background/surface but modern M3 treats them as equal — set both to `--background`. `onBackground = onSurface = --foreground`.
- **card → surfaceContainer ladder:** shadcn's card (`#FFFCF5`) is *lighter* than the page (`#F5F1E6`) — a raised cream card on warm paper. M3's light ladder normally darkens with elevation; here the brand inverts the bottom of the ladder instead. Derivation rule (light, descending L): `Lowest = white → Low = --card → Container = --background → High = --muted → Highest = --secondary` — five steps built entirely from css neighbors, monotone in L (0.9914 → 0.9582 → 0.9239 → 0.8846). Consequence: M3 `Card` (elevated, uses `surfaceContainerLow`) renders *exactly* the shadcn card color on the shadcn page background. Dark ladder ascends the same way: `derived-below-bg → --muted → --card → --secondary → --accent` (0.245 → 0.2939 → 0.3237 → 0.3795 → 0.4186). Only two derived values in the whole neutral ladder (dark Lowest, dark Bright).
- **secondary:** shadcn `--secondary` is a light chip with dark text — that is an M3 *container*, not an M3 secondary. So `secondaryContainer = --secondary`, `onSecondaryContainer = --secondary-foreground`, and the M3 `secondary` slot (needs to carry white text on FilledTonal-adjacent roles) takes the `--secondary-foreground` tone itself. Same logic for accent: `tertiaryContainer = --accent`, `tertiary` = derived accent-hue mid-tone.
- **muted → surfaceVariant/onSurfaceVariant** is exact (both modes, straight copy).
- **ring → focus:** css `--ring` equals `--primary` in both modes; Compose has no ring token — focus indication comes from M3 defaults which already derive from `primary`. No work needed; record the equivalence so nobody adds a bespoke focus color.
- **destructive:** css keeps the same red in dark mode with white text (contrast ~4.6:1 on dark bg — acceptable). Fidelity beats M3 habit (M3 normally brightens error in dark); do not "fix".
- **chart-1..5:** not `ColorScheme` slots. Expose as a small `EcopanChartColors` (5 `Color`s, light+dark) via a `CompositionLocal` in `:core:ui` for any Compose charting (dashboard is web, but POS/mobile may sparkline). Registry rule §7 applies if the list ever grows.

## 3. Changes in `:core:ui`

1. **`Color.kt`** — delete the purple seed values wholesale; replace with the §2 palette (named `BakeryPrimaryLight = Color(0xFFA67C52)` style, oklch source in a trailing comment per line). Delete `Seed`.
2. **`Theme.kt`** — keep the exact structure (it already fills the full M3 surface incl. `surfaceContainer*` + fixed roles — constructor param names verified against M3 1.11.0-alpha07); swap value references to the new names. Drop the MaterialKolor generator header comment. The unused `libs.material-kolor` catalog entry can be removed if nothing else references it (check first).
3. **`EcopanTheme.android.kt`** — keep `dynamicColor: Boolean = false` parameter and the branch, default stays **OFF** (already is, in `EcopanTheme.kt` expect default): brand identity wins over Material You; parameter retained for demo/comparison only. Keep edge-to-edge `SideEffect`. iOS/desktop actuals unchanged apart from picking up new schemes transitively.
4. **Shapes** — new `appShapes()` in the theme package, passed to all three `MaterialExpressiveTheme` calls (today shapes are default — this is an addition, verify `MaterialExpressiveTheme` shape param name via context7). See §5.
5. **Typography** — `Type.kt` rewritten per §4; Figtree/Roboto `.ttf` files removed from `core/ui/src/commonMain/composeResources/font/`.
6. **ThemePreview** (`core/ui/.../preview/ThemePreview.kt`) — keep `@PreviewLightDark`; **drop `@PreviewDynamicColors`** (dynamic color is off-brand; previews of it are noise).

### Theme selection: keep, don't extend

`AppTheme` is already exactly `LIGHT | DARK | SYSTEM` and `ThemeSelectionDialog` already offers only those three — the template has no user-selectable *palette*, so the recommendation "brand-fixed palette, light/dark/system only" is satisfied by **doing nothing** in `feature/settings` and `data/preferences`. Binding rule going forward: no palette-picker is ever added; the only theme preference is the `AppTheme` mode. `App.kt`'s `dynamicColor: Boolean = false` pass-through stays as-is.

## 4. Typography

Fonts (Google Fonts, all OFL 1.1 — **copy each family's `OFL.txt` into the same resources tree or a `core/ui/fonts-licenses/` dir, and mention in the repo README/about screen**):

- **Libre Baskerville** — `Regular`, `Bold`, `Italic` (family ships only 400/700/400-italic).
- **Lora** — `Regular`, `Medium`, `SemiBold`, `Italic` (variable-font static instances; Lora has full 400–700 + italics).
- **IBM Plex Mono** — `Regular`, `Medium` (used sparingly; see POS note).

Placement: `core/ui/src/commonMain/composeResources/font/*.ttf` (any file in `font/` is accepted; accessor names derive from the filename — verified against Compose resources source). Build `FontFamily` per family with `org.jetbrains.compose.resources.Font(Res.font.X, weight = ..., style = ...)` exactly as the current `Type.kt` does for Figtree.

Scale mapping (mirrors css: `--font-sans: Libre Baskerville` is the *heading/UI* face in this design, `--font-serif: Lora` the reading face):

| M3 slots | family/weight |
|---|---|
| `display*`, `headline*`, `title*` | Libre Baskerville (Regular; Bold where M3 default weight ≥ Medium — Libre Baskerville has no Medium, snap Medium→Bold) |
| `body*` | Lora Regular |
| `label*` | Lora Medium |
| monospace (not an M3 slot) | IBM Plex Mono via dedicated `EcopanMonoFamily` accessor for explicit use |

**POS legibility caveat (binding on `05-pos-desktop.md`):** serifs degrade at small sizes on data-dense screens. Rules: (a) no serif below 12sp — `labelSmall` gets a floor of 12sp/Lora Medium; (b) the live **weight and price readouts** on the weigh-and-sell screen use **IBM Plex Mono** at large sizes (≥ 28sp for the live scale figure, ≥ 20sp for line totals) — Plex Mono's fixed-pitch digits give the tabular alignment serif faces lack (Lora/Libre Baskerville tabular-figure support is not guaranteed across static instances; don't rely on `FontFeature "tnum"`); (c) receipt-preview text is Plex Mono by definition.

## 5. Shapes

css: `--radius: 0.25rem` with the shadcn calc chain `sm = r−4px (→0)`, `md = r−2px (2px)`, `lg = r (4px)`, `xl = r+4px (8px)`. 1 css px ≈ 1dp. Proposed M3 `Shapes`:

| M3 shape | dp | css analogue |
|---|---|---|
| `extraSmall` | 2 | `--radius-md` |
| `small` | 4 | `--radius-lg` (the base radius) |
| `medium` | 6 | interpolated (base+2) |
| `large` | 8 | `--radius-xl` |
| `extraLarge` | 12 | base+8, keeps M3's monotone ladder without going pill-shaped |

Sharp, near-square corners are the brand. Do not let M3 defaults (up to 28dp) leak: pass `appShapes()` in **all three** platform actuals.

## 6. Elevation & shadows

css defines a warm brown shadow set (`hsl(28 13% 20%)` at 0.06–0.30 alpha, offset 2px/3px). Compose M3 elevation is tonal + neutral drop shadows; there is no cross-platform tinted-shadow API worth the cost. **Decision: do not chase css shadow parity.**

- Depth is expressed primarily via the §2.3 surfaceContainer ladder (which already encodes the warm neutrals).
- Cards/dialogs keep small default shadow elevation (1–3dp) for edge definition; never exceed 3dp on POS surfaces.
- The warm tint of the css shadows is visually approximated because every surface underneath is itself warm — accepted difference, note it in the verification checklist so nobody "fixes" it per-screen.

## 7. Web side & the registry rule

- **`webApp/src/index.css` is the registry.** The original text named `spec/index.css`, which does
  not work: `/spec/` is gitignored as local scratch ("not part of this project"), so that file is
  not version-controlled and a rule anchored to it cannot be enforced or even reviewed. The live
  Tailwind entry point is tracked, is what actually ships, and cannot drift from itself. A local
  `spec/index.css` may still exist as a scratch copy; it is not authoritative.
- **The only cross-client rule:** any *new semantic color* introduced later (e.g. a "warning", "success", or sync-status color) must be added in **both** places in the same change: a css variable in `webApp/src/index.css` (light + dark, `@theme inline` registration) **and** a row in the §2 tables of this document with its oklch triplet, converted hex, and M3/Compose slot or CompositionLocal. A color that exists in one client and not the mapping table is a defect.

### 7.1 What is shared, and what is deliberately not (amended)

The original text assumed one visual system across all three clients. Two revisions since then
split that, on purpose. Recording it here because an undocumented divergence is indistinguishable
from a bug.

**Colour is shared.** `--primary`/`--ring`/`--sidebar-primary` are the ember
`oklch(0.5700 0.1484 44.75)` light / `oklch(0.7050 0.1350 48.00)` dark on both web and Compose
(`PrimaryLight`/`PrimaryDark` in `core/ui`). The former tan was too desaturated to read as an
action. The light step sits one notch darker than `--chart-1`, which shares its hue, because
OnPrimary-on-Primary must clear 4.5:1 and at chart-1's lightness it lands on 4.39.

**Superseded:** typography is now shared. Both clients run Outfit (one family) + IBM Plex Mono, per
the approved drift recommendation (entregables §4) and `core/designsystem/README.md`. What follows is
the historical reasoning for the earlier split. Web then ran a separate display/interface pairing; Compose kept
Libre Baskerville + Lora + IBM Plex Mono per §4. The reason is §4's own POS legibility caveat — no
serif below 12sp, and IBM Plex Mono for the live weight and price readouts — which was reasoned
against *those* faces. A dashboard read at a desk and a till read at arm's length over a scale are
different legibility problems, and re-opening the till's is not worth typographic uniformity.
Revisit only with a fresh legibility pass, not as a consistency cleanup.

**Chart colours are web-only.** `--chart-1..5` have no Compose counterpart because the native
clients draw no charts. They are a validated categorical palette, not a general-purpose ramp, and
must not be reused as accents.

## 8. Preview & verification plan

1. Build a throwaway web sample page (plain HTML + `index.css`, shadcn-style card/button/input/list — can live under `spec/` or the scratchpad, not shipped) rendered in light and dark.
2. Compose side: a `ThemeGallery` debug composable in `:core:ui` (or reuse existing preview screens) showing: page bg + elevated card + primary/secondary/tertiary buttons + text field + error banner + the typography scale + the mono weight readout sample. Annotate with `@ThemePreview` (light+dark).
3. Screenshot checklist (Android Studio previews or desktop run, both modes):
   - [ ] page background vs card: card reads lighter (light) / lighter-warmer (dark) than page, same relationship as web
   - [ ] primary button: same caramel `#A67C52` / `#C0A080`, white/dark text matches
   - [ ] secondary chip & accent surfaces match web `--secondary`/`--accent`
   - [ ] destructive red identical in both modes on both stacks
   - [ ] borders/dividers: same faint warm hairline weight
   - [ ] headings serif (Libre Baskerville), body Lora, weight readout Plex Mono
   - [ ] corner radii visibly square-ish (≈4dp), no pill buttons
   - [ ] known accepted differences: shadow tint (§6), focus ring rendering
4. **Acceptance:** side-by-side screenshots (Compose light/dark vs web light/dark) show the *same perceived palette* — a reviewer cannot tell which product a cropped card came from by color alone. Exact-pixel parity is not the bar; the checklist items are.

## 9. Work items (ordered)

1. **Convert + verify palette.** Re-run the §1 conversion for every oklch triplet in §2 (including deriveds); diff against the table; fix the table if any hex is off (then re-verify web side unaffected — css is untouched). *Accept:* independent tool reproduces all hexes ±1/255.
2. **Replace `Color.kt` / `Theme.kt`** with the bakery schemes (§3.1–3.2). *Accept:* `:core:ui` compiles for Android/iOS/Jvm; no reference to old purple values or `Seed` remains; full token surface still filled (constructor calls unchanged shape).
3. **Fonts.** Download OFL font files + licenses, drop into `composeResources/font/`, rewrite `Type.kt` (§4), delete Figtree/Roboto. *Accept:* all targets compile; preview shows the three families; `OFL.txt` files present.
4. **Shapes.** Add `appShapes()` (§5) and pass it in the three `EcopanTheme` actuals. *Accept:* dialog/card corners at 4–8dp in previews.
5. **Preview cleanup.** Drop `@PreviewDynamicColors` from `ThemePreview`; add/extend the `ThemeGallery` composable. *Accept:* light+dark previews render.
6. **Verification pass** (§8): produce the web sample, capture side-by-side screenshots, walk the checklist, file diffs as issues. *Accept:* checklist fully checked or deviations recorded here.
7. **Housekeeping:** remove `material-kolor` from `gradle/libs.versions.toml` if unreferenced; run root `./gradlew test` + `:core:ui:jvmTest`.

Sequencing: items 1–5 are one PR-sized change landing with the first real screen (00-overview phasing); item 6 recurs whenever either side's tokens change.

## Open questions

- **Lora vs Libre Baskerville for `title*`:** titles in dense POS lists might read better in Lora SemiBold than Libre Baskerville. Decide during §8 verification; if switched, update §4 table here first.
- **Dark-mode error tone:** we honor css (`#B54A35` unchanged in dark). If verification shows it muddy on `#2D2621`, the fix goes to *both* `index.css` and this table (registry rule §7) — not a Compose-only brighten.
- **`--sidebar*` tokens:** currently unmapped (web-only). If the Compose nav rail/drawer ends up needing a distinct tone from `surfaceContainer`, map `--sidebar` (`= --muted` light / `= --background` dark) explicitly here.
- **Chart colors in Compose:** confirmed needed only if POS/mobile grow sparklines; the `EcopanChartColors` local (§2.3) is deferred until a first consumer exists.
- **Expressive theme:** actuals use `MaterialExpressiveTheme` (experimental). Keeping it is fine, but if M3 1.11 stable changes the API, falling back to `MaterialTheme` is acceptable — colors/typography/shapes are theme-engine-agnostic.

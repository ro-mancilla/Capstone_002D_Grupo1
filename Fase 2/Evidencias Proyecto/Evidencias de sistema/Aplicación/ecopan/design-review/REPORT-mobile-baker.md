# Baker Mobile Client — Android Audit, 2026-09-06

Captured from the `:android:app` debug build via the `android` CLI, against the live local backend
(`docker compose` stack, seed data, `adb reverse tcp:8080`). Screenshots in
`screenshots/mobile-baker/`, referenced below by filename.

**Devices** — both emulators, no hardware. Gestures, refresh rate and real performance are therefore
*not* covered by this pass.

| | Serial | Size | Density | dp viewport |
|---|---|---|---|---|
| Phone | `emulator-5554` (Medium_Phone) | 1080×2400 | 420 dpi | 411×914 dp |
| Tablet | `emulator-5556` (Pixel_Tablet) | 2560×1440 | 320 dpi | 1280×720 dp |

Scope: `mobile/feature/production` (Producción, Stock), `feature/settings` (Configuración),
`feature/auth` (device registration), `composeApp` navigation, and `core/ui` theme. The desktop POS
till is covered separately in `REPORT-desktop-pos.md`.

---

## Audit Health Score

| # | Dimension | Score | Key Finding |
|---|-----------|-------|-------------|
| 1 | Accessibility | 1 | Selected nav label measures 1.3:1; keypad key outlines 1.1:1 |
| 2 | Performance | 3 | No issues observed; not measured on hardware |
| 3 | Appearance & Theming | 2 | `secondary` and `outline` carry surface-weight tones, so every M3 component that uses them as a foreground disappears |
| 4 | Platform Conformance | 3 | Material 3 throughout, correct rail/bar switch; snackbar occludes the row it reports |
| 5 | Adaptivity | 1 | Keypad has no max width — unusable in landscape and on tablet |
| **Total** | | **10/20** | **Acceptable — significant work needed** |

## Platform Conformance Verdict

**Pass.** This reads as a native Android app, not a ported website. Navigation is a real
`NavigationSuiteScaffold` that correctly becomes a rail at expanded width
(`19-tablet-produccion-final.png`); components are genuine Material 3 (`OutlinedButton`, `Switch`,
`Snackbar`, Material dialogs); the system Back gesture works and the notification permission goes
through the real system dialog (`08-permiso-notificaciones.png`). Colour is expressed through M3
role tokens rather than raw hex at the call sites.

The conformance defects are narrow rather than structural:

- The snackbar is anchored so it **covers the history row it just created**
  (`06-lote-registrado.png`, `13-lote-sin-formula.png`) — the user cannot see the result of the
  action the snackbar is confirming.
- Phone landscape keeps the **bottom navigation bar** rather than switching to a rail, spending
  ~100 dp of an already 411 dp-tall viewport (`15-produccion-landscape.png`).
- Two typefaces (a serif and a monospace) are mixed within single rows with no evident rule — the
  same observation the desktop report makes. On mobile it is most visible in Configuración
  (`07-configuracion-top.png`) and the history row.

---

## Executive Summary

- Audit Health Score: **10/20** (Acceptable — significant work needed)
- **14 issues**: 2 P0, 5 P1, 5 P2, 2 P3
- Top issues:
  1. **[P0]** Batches are labelled with the product *category*, not the product — "Marraqueta" is
     logged and displayed as "Pan". Cross-client: the same field drives the desktop till's banner.
  2. **[P0]** The weight keypad has no maximum width, so it is unusable in phone landscape and on
     tablets.
  3. **[P1]** The *selected* navigation item is rendered at **1.30–1.36:1** contrast — lower than
     the unselected one. The active tab looks inactive in both themes.
  4. **[P1]** Keypad key outlines measure **1.07–1.11:1**; the primary input control of the app has
     effectively invisible boundaries.
  5. **[P1]** `Stock` shows five "necesita conciliación" badges with no way to act on any of them.
- Recommended next steps: fix the two P0s, then run a theming pass over `secondary`/`outline`, which
  is the single root cause behind three separate contrast findings.

---

## Detailed Findings by Severity

### [P0] Production batches are labelled with the product category, not the product

- **Location**: `data/production/impl/.../ProductionRepositoryImpl.kt:83`; surfaces at
  `mobile/feature/production/impl/.../BatchEntryViewModel.kt:253` and
  `jvm/feature/pos/impl/.../PosViewModel.kt:143`
- **Category**: Correctness (surfaced by the UI audit)
- **Impact**: Logging a **Marraqueta** batch produces a history row reading "18:47 **Pan** 12.5 kg",
  while the confirming snackbar on the same screen reads "Lote registrado: **Marraqueta** 12.5 kg"
  (`06-lote-registrado.png`). Logging a **Berlín** batch produces "18:53 **Pastelería** 70.0 kg"
  (`13-lote-sin-formula.png`). A baker who bakes marraqueta and hallulla in the same session sees
  two rows both labelled "Pan" and cannot tell them apart. The same denormalised field feeds the
  desktop till's *batch ready* banner, so the cashier sees the category too.
- **Cause (verified)**: `Product` carries both `name` ("Marraqueta") and `productType` ("Pan").
  `logBatch` stores `productTypeName = product.productType`, but every consumer renders that field
  as a product name — and `ProductionSchemaTest.kt:94` and `PosViewModelTest.kt:404` both seed it
  with `"Marraqueta"`, i.e. the tests encode the *intended* meaning, which the write path does not
  honour.
- **Recommendation**: Store `product.name` in `productTypeName` (matching the field's KDoc,
  "a denormalized display snapshot", and the existing tests). If the category is genuinely wanted
  too, add a separate field rather than overloading this one. Check
  `ProductionBatchSyncApplier.kt:77` and `BatchReadySseListener.kt:58`, which populate the same
  field from the sync and SSE paths and would otherwise disagree with local writes.
- **Suggested command**: `/impeccable harden`

### [P0] The weight keypad has no maximum width

- **Location**: `mobile/feature/production/impl/.../BatchEntryScreen.kt:254-278` (`Keypad`)
- **Category**: Adaptivity
- **Impact**: Each key is `Modifier.weight(1f).aspectRatio(1.4f)` inside a `fillMaxWidth()` column,
  so key size grows without bound with the viewport:

  | Viewport | Key size | Result |
  |---|---|---|
  | Phone portrait (411 dp) | ~121 × 86 dp | Correct |
  | Phone landscape (914 dp) | ~289 × 206 dp | Only the top sliver of row 1 is visible; no digit is reachable without scrolling (`15-produccion-landscape.png`) |
  | Tablet (1280 dp) | ~384 × 274 dp | 1.5 rows fill the screen (`20-tablet-keypad.png`) |

  Weight entry is the single job of this screen. On a tablet or a rotated phone the baker cannot
  complete it without scrolling between every keystroke and the readout.
- **Guideline**: Material 3 — *"Never ship a phone bottom-bar untouched on a tablet"*; window size
  classes should drive layout.
- **Recommendation**: Constrain the keypad (`widthIn(max = ~320.dp)`, centred) so keys keep a
  thumb-sized target instead of scaling with the window. At expanded width, place readout and
  keypad side by side rather than stacked — the same two-pane split would fix landscape, where the
  wasted horizontal space is exactly what the vertical axis needs.
- **Suggested command**: `/impeccable adapt`

### [P1] The selected navigation item has lower contrast than the unselected one

- **Location**: `core/ui/.../theme/Color.kt:26` (`SecondaryLight`), `:63` (`SecondaryDark`);
  consumed by `composeApp/.../navigation/AppNavigation.kt:111-118`
- **Category**: Accessibility / Theming
- **Impact**: The active tab reads as the inactive one. Measured from the captures (glyph colour
  against the navigation bar background, WCAG 2.x contrast):

  | Theme | Selected label | Unselected label |
  |---|---|---|
  | Light (`07-configuracion-top.png`) | `#E2D8C3` — **1.30:1** | `#7D6B56` — 4.69:1 |
  | Dark (`12-produccion-dark.png`) | `#4A4039` — **1.36:1** | `#C5BCAC` — 7.28:1 |

  WCAG 1.4.3 requires 4.5:1 (3:1 for large text). Both selected values fail by a wide margin, and
  the same inversion is visible on the tablet rail (`19-tablet-produccion-final.png`).
- **Cause (verified)**: no `NavigationSuiteDefaults.itemColors` override exists anywhere in the
  repo, so this is Material's default resolving the selected label to `colorScheme.secondary`. The
  measured glyph colours are *exactly* `SecondaryLight` / `SecondaryDark`. Those two tokens hold
  surface-weight tones — `SecondaryLight` is byte-identical to `SurfaceDimLight`, and
  `SecondaryDark` is byte-identical to both `OutlineDark` and `SurfaceContainerHighestDark`. A
  colour intended for a surface cannot serve as a foreground on a surface.
- **Recommendation**: Give `secondary` a real foreground-weight tone in both schemes (M3 expects
  `secondary` to be usable *on* surface, with `secondaryContainer` carrying the pale tone the
  palette currently has there). Verify nothing else depended on the old value. If the palette must
  stay frozen, pass explicit `itemColors` at `AppNavigation.kt:111` instead — but that leaves the
  token wrong for every future consumer.
- **Suggested command**: `/impeccable colorize`

### [P1] Keypad key outlines are invisible in both themes

- **Location**: `BatchEntryScreen.kt:259-273`; `core/ui/.../theme/Color.kt` (`OutlineLight` /
  `OutlineDark`)
- **Category**: Accessibility
- **Impact**: `OutlinedButton` borders measure **`#ECE5D8` on `#F5F1E6` = 1.11:1** (light,
  `01-produccion-keypad.png`) and **`#312B26` on `#2D2621` = 1.07:1** (dark,
  `12-produccion-dark.png`). WCAG 1.4.11 requires **3:1** for the boundary of a UI component. Only
  the digits themselves suggest where the keys are; the tap targets are guesswork, on the control
  the whole screen exists to serve — used with floury hands, at speed, often in poor bakery light.
- **Recommendation**: Raise `outline` to ≥3:1 against `surface` in both schemes, or give the keys a
  filled/tonal treatment (`FilledTonalButton`) so the target is defined by fill rather than a
  hairline. Note `OutlineDark` is currently identical to `SurfaceContainerHighestDark` — an outline
  the same colour as the surface it outlines cannot ever be visible.
- **Suggested command**: `/impeccable colorize`

### [P1] Stock reports five problems and offers no way to act on any of them

- **Location**: Stock screen (`04-produccion-stock-expanded.png`)
- **Category**: Conformance / UX
- **Impact**: Five of six ingredients show a red "necesita conciliación" badge beside a negative
  quantity (Agua −17.087 kg, Harina −33.279 kg, …). The layout dump confirms **no row, badge or
  value in the list is `clickable`** — the only interactive elements on the screen are the back
  arrow and the sync chip. The screen names a problem, marks it urgent, and dead-ends. Below the
  six rows, roughly 55% of the viewport is empty, so there is no space constraint driving this.
- **Recommendation**: Make the row (or the badge) open a reconciliation entry point; if
  reconciliation is not built yet, say where it happens rather than showing an actionless alarm.
- **Suggested command**: `/impeccable onboard`

### [P1] The primary CTA is pushed under the navigation bar when the ingredient preview appears

- **Location**: `BatchEntryScreen.kt` — `PreviewCard` inserted above `ConfirmButton` in the scrolling
  column
- **Category**: Adaptivity
- **Impact**: Entering a weight inserts the "Se usará: …" card, which pushes "REGISTRAR LOTE" down
  so only a ~10 px orange sliver remains visible above the navigation bar
  (`02-produccion-weight-entered.png`). The action becomes available and simultaneously scrolls out
  of reach; the user must scroll after every weight entry.
- **Recommendation**: Pin the confirm button to the bottom of the screen (or place readout, keypad
  and CTA in a non-scrolling column with only history scrolling), so the primary action holds still
  as content changes above it.
- **Suggested command**: `/impeccable layout`

### [P1] Disabled CTA renders empty parentheses

- **Location**: `BatchEntryScreen.kt` — `ConfirmButton(weightLabel = …)`
- **Category**: Clarity
- **Impact**: With no weight entered the button reads **"REGISTRAR LOTE ()"**
  (`01-produccion-keypad.png`, `12-produccion-dark.png`, `16-produccion-fontscale180.png`) — dangling
  punctuation around an empty string. It reads as a rendering fault.
- **Recommendation**: Drop the parenthetical when `weightLabel` is empty; show
  "REGISTRAR LOTE" alone, or a prompt ("Ingresa el peso").
- **Suggested command**: `/impeccable clarify`

### [P2] Switch thumb is invisible against its track

- **Location**: Configuración → "Color dinámico" (`07-configuracion-top.png`,
  `11-configuracion-dark.png`)
- **Category**: Accessibility
- **Impact**: In the unchecked state the thumb measures **1.24:1** (light) and **1.25:1** (dark)
  against the track — against WCAG 1.4.11's 3:1. In dark the control reads as an empty pill; its
  on/off state is not perceivable.
- **Recommendation**: Same root cause as the keypad — raise `outline` / the unchecked thumb tone.
- **Suggested command**: `/impeccable colorize`

### [P2] Sync chip overflows the top app bar at large font scales

- **Location**: `core/ui/.../components/SyncStatusIndicator.kt`; top bar
- **Category**: Accessibility (text scaling)
- **Impact**: At `font_scale 1.8` the chip wraps to three lines ("Sincro / nizand / o…"), grows past
  the app bar's height and collides with the status bar (`16-produccion-fontscale180.png`). The rest
  of the screen scales acceptably, so this is the one clipping point.
- **Recommendation**: Let the chip degrade to icon-only past a text-size threshold, or allow the top
  bar to grow with it.
- **Suggested command**: `/impeccable harden`

### [P2] Snackbar covers the row it is confirming

- **Location**: `AppNavigation.kt:148-153` (`SnackbarHost`, bottom-aligned)
- **Category**: Conformance
- **Impact**: "Lote registrado: Marraqueta 12.5 kg" lands directly on top of the new "Hoy" row and
  the "Stock ▸" button (`06-lote-registrado.png`); the no-formula variant covers both plus part of
  the row's own text (`13-lote-sin-formula.png`). The user is told the batch was logged but cannot
  see it appear.
- **Recommendation**: Keep the newly-inserted row visible — scroll it above the snackbar, or move
  the history above the fold so the confirmation and its result do not compete for the same band.
- **Suggested command**: `/impeccable layout`

### [P2] Quantities render at inconsistent precision

- **Location**: `BatchEntryViewModel.kt:262` (`formatGrams`), and the Stock list
- **Category**: Clarity
- **Impact**: `formatGrams` uses `grams / 1000.0`, whose decimal count follows the value: the same
  screen shows "12.5 kg", "70.0 kg" and "7.441 kg"; Stock shows "0.0 kg" beside "−33.279 kg"
  (`04-produccion-stock-expanded.png`). Gram-level precision on a sack of flour is noise, and the
  ragged decimals make the column hard to scan.
- **Recommendation**: Fix the decimal count per unit (e.g. kg to 1–2 places, grams to 0) and format
  consistently in both the batch history and the Stock list.
- **Suggested command**: `/impeccable clarify`

### [P2] Settings copy is left over from the project template

- **Location**: `feature/settings/impl/.../strings.xml` — notifications rationale
- **Category**: Clarity
- **Impact**: "Necesario para mostrar recordatorios de **tus eventos**"
  (`07-configuracion-top.png`) — a bakery app has batches, not events. The "Acerca de" dialog
  likewise credits "Ilustraciones por Storyset" (`09-acerca-de-dialog.png`) on a client that shows
  no Storyset illustration.
- **Recommendation**: Rewrite the rationale in domain terms (batch-ready notifications). Drop the
  attribution if the asset is no longer shipped in this client.
- **Suggested command**: `/impeccable clarify`

### [P3] Sync chip gives no feedback when tapped

- **Location**: `SyncStatusIndicator.kt`
- **Category**: Conformance
- **Impact**: The chip reports `clickable` in the layout tree, but tapping it in the "Requiere
  atención" state produced no dialog, snackbar, spinner or state change
  (`05-sync-chip-tapped.png`). If it triggers a retry, the retry is silent; if it does nothing, it
  should not be clickable. Compare the desktop report's dead "Ingreso manual" chip — same class of
  defect.
- **Recommendation**: Show a spinner or snackbar on tap, or remove the click affordance.
- **Suggested command**: `/impeccable clarify`

### [P3] Content is locked to a narrow column at expanded width

- **Location**: Auth (`17-tablet-produccion.png`), Configuración (`14-configuracion-landscape.png`)
- **Category**: Adaptivity
- **Impact**: Device registration renders in a ~680 px column on a 2560 px tablet, leaving ~73% of
  the width empty while the form itself is cut off at the bottom. Configuración in phone landscape
  gives its serif page title ~35% of the vertical space, leaving room for two rows. A centred
  reading column is a legitimate choice; combining it with vertical clipping is not.
- **Recommendation**: Trade the empty horizontal space for vertical room — shrink the page title in
  short viewports, and let the registration card use more of the tablet's width.
- **Suggested command**: `/impeccable adapt`

---

## Patterns & Systemic Issues

1. **Two palette tokens carry surface-weight tones, and three separate contrast failures follow.**
   `SecondaryLight`/`SecondaryDark` and `OutlineLight`/`OutlineDark` are byte-identical to surface
   tokens (`SurfaceDimLight`, `SurfaceContainerHighestDark`). Any Material component that uses
   `secondary` or `outline` as a *foreground* therefore disappears — which is exactly what the
   navigation label, the keypad outline and the switch thumb all do. Fixing the two tokens fixes all
   three, and pre-empts the next component that reaches for them. Because `core/ui` is shared, this
   also lands on the desktop till.

2. **Nothing carries a content description except one icon.** Across every layout dump, the only
   `contentDesc` in the app is the keypad backspace key. Product cards, the sync chip, the theme
   switch, the info icon and the history rows expose text or nothing at all. TalkBack users get no
   state announcements ("selected", "pending"), and the `⚠` glyph in "⚠ sin fórmula" is read as
   punctuation.

3. **One long scroll carries the whole task.** Producción stacks product picker → readout → keypad →
   preview → CTA → history → Stock in a single scrolling column. It survives on a portrait phone and
   fails everywhere else — landscape, tablet, and any state change that inserts content mid-column
   (the P1 CTA finding). The screen has no layout that responds to the window.

4. **Two typefaces, no rule.** A serif carries product names, values and headings; a monospace
   carries section labels, chips, status text and navigation labels — sometimes within one row
   ("⚠ sin fórmula" under "Berlín"). This is the same observation the desktop report records, so it
   is a system-level decision that needs stating, not a per-screen slip.

## Positive Findings

- **The navigation suite adapts correctly.** `NavigationSuiteScaffold` switches to a rail at
  expanded width with no per-platform branching (`19-tablet-produccion-final.png`) — the hard part
  of adaptive navigation is already right.
- **First-run sync is genuinely well designed.** "Preparando tu tienda" with a per-step checklist,
  a completed "Cuenta y sesión — listo" and an explicit "no se puede cancelar" warning
  (`18-tablet-produccion-baker.png`) is a considerate, honest onboarding state.
- **The no-formula path is handled honestly.** "Lote registrado — stock no descontado (sin fórmula)"
  (`13-lote-sin-formula.png`) tells the baker exactly what did and did not happen, rather than
  silently succeeding. The `⚠ sin fórmula` badge warns before the choice is made, too.
- **The ingredient preview is the right idea**: showing the derived quantities *before* commit
  (`02-produccion-weight-entered.png`) lets the baker sanity-check the formula against the mix.
- **Colour is themed through M3 roles, not raw hex at call sites**, which is why the two token fixes
  above propagate cleanly.
- **Permissions use the real system dialog** and the theme picker applies instantly and persists.

## Recommended Actions

1. **[P0] `/impeccable harden`** — batches labelled with `productType` instead of `name`
   (`ProductionRepositoryImpl.kt:83`); fix the write path and the two sync paths that mirror it.
2. **[P0] `/impeccable adapt`** — cap the keypad width and split readout/keypad at expanded width;
   fixes tablet and phone landscape together.
3. **[P1] `/impeccable colorize`** — give `secondary` and `outline` foreground-weight tones; clears
   the navigation label, keypad outline and switch thumb contrast failures at once.
4. **[P1] `/impeccable layout`** — pin the confirm button; stop the snackbar occluding the row it
   reports.
5. **[P1] `/impeccable onboard`** — give "necesita conciliación" somewhere to go.
6. **[P2] `/impeccable clarify`** — empty `()` in the CTA, template copy about "tus eventos",
   inconsistent decimal precision, the silent sync chip.
7. **[P2] `/impeccable harden`** — sync chip overflow at large font scales; add content descriptions
   and selection-state announcements.
8. **[P3] `/impeccable polish`** — final pass.

## Repro notes

```shell
docker compose up -d                      # backend must be up on :8080
./gradlew :android:app:assembleDebug
android emulator start Medium_Phone       # and/or Pixel_Tablet
adb reverse tcp:8080 tcp:8080             # per device
android run --apks=android/app/build/outputs/apk/debug/app-debug.apk --device=<serial>
android screen capture --device=<serial> -o <path>.png
android layout -p                         # semantics tree; how the dead Stock rows were confirmed
```

Contrast figures were measured from the PNGs (most-common glyph colour vs. most-common background
colour in the element's band, WCAG 2.x relative luminance), not estimated by eye. Dark theme and
`font_scale 1.8` were exercised on the phone only; the tablet pass was light theme at 1.0.

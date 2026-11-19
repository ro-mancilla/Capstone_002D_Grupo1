# :core:designsystem

The Ecopan design system for the Compose clients: theme, tokens, shapes, motion, icons and stateless
components. Domain-free (no dependency on any other project module), so the desktop till, the baker's
mobile client and every shared feature can build on it. Package `com.f776.core.designsystem` (the
`com.f776.core.*` convention `:core:ui` uses). `:core:ui` exposes it with `api`, so a feature that
depends on `:core:ui` gets `EcopanTheme` without a second dependency.

The source of truth is the Claude Design project's Ecopan design system; the web dashboard
(`webApp/src/index.css`) is its transcription. The Compose-side deviations were approved in
"Recomendación de deriva (2.3)": crust primary, a `price` role, Outfit, 8/12/16/22 dp corners, status
roles on `StatusPill`.

```
theme/      EcopanTheme, Color (scheme + EcopanColors), Type, Shape, Spacing, Elevation, Motion, StateLayer
icon/       EcopanIcons
component/  everything below
preview/    EcopanGallery (+ ThemeGallery, ControlsGallery, DomainGallery), light and dark
composeResources/font   Outfit (variable), IBM Plex Mono (static)      fonts-licenses/  OFL texts
```

## Theming

```kotlin
EcopanTheme(darkTheme = isDark, dynamicColor = false) { ... }
```

Read tokens with `EcopanTheme.colorScheme` (Material roles), `.colors` (crust, peach, `price`, flour,
ink, status, honey, charts, receipt paper), `.typography`, `.shapes`, `.spacing`, `.motion`, `.isDark`.
Dynamic colour (Android 12+) only replaces the Material roles; status, price, honey and chart colours
keep their meaning.

## Tokens

- Colour `EcopanColors`: `crust` (+ `Hover`, `Active`, `Soft`, `Line`), `peach` (+ `Hover`, `Soft`, `onPeach`),
  `price` (money and only money), `page`/`card`/`sunken`/`inset`/`line`/`lineStrong`/`hairline`, `ink*`,
  `controlBorder` (3:1 against the card), `success`/`warning`/`danger`/`info` (each `fg`/`bg`/`line`),
  `honey1..3` + `honeyInk` (simulated), `chart1..5` (fixed order, never cycled; fold the tail into "Otros"),
  `surfaceDark`, `focusRing`, `paper*` (receipts stay white in dark mode). Light page `#FBF8F5`, card white;
  dark page `#1E1815`, card `#272019`. Dark values come from the design's `tokens/dark.json`.
- Type `EcopanTypography`: `display` 52, `headline` 32, `title` 26, `subtitle` 18, `body` 16, `bodySmall` 14,
  `label` 14 medium, `eyebrow` 12, `numeric` 32 tabular, `quantityLg`/`quantityMd`/`quantitySm` 28/22/20 semibold tabular (baker lists), `figure` (tabular body), `ledger` (Plex Mono 13),
  `scaleReadout` 68. One family, Outfit; Plex Mono only for ids, timestamps and receipt lines. Figures use
  `fontFeatureSettings = "tnum"`; wrap any other number in `figureStyle(...)`.
- Shapes `EcopanShapes`: `small` 8, `medium` 12 (fields), `large` 16, `extraLarge`/`card` 22, `dialog` 30, `pill`.
- Spacing `EcopanSpacing`: 4dp grid `space0_5..space16`, `controlMin` 44, `field` 48, `touch` 56, `rowMin` 56, `cardPadding` 20.
- Elevation: flat by default. `Modifier.ecopanShadow(EcopanElevation.Card|Raised|Overlay|Dialog, shape)` is wide, soft and warm;
  `ecopanAccentGlow` only under the money button.
- Motion `EcopanMotion`: 80 / 140 / 200 / 320 ms on `cubic-bezier(.2, 0, .2, 1)`; `instant()`, `fast()`, `base()`,
  `slow()`, `enter()`, `exit()`. When the platform asks for reduced motion every spec is `snap()` and every
  `*Millis` is 0; check `EcopanTheme.motion.reduced` before any infinite animation.
- Interaction: hover and press darken the fill one ramp step (no scale), keyboard focus draws a 3dp peach ring,
  disabled is drawn at 40% as a whole (`EcopanStateLayer.DisabledAlpha`).

## Components

All stateless; `modifier` is the first optional parameter. Text comes from the caller; defaults live in
`composeResources/values` (Spanish, the product language and the repo's only locale) only for the
accessibility strings and the fixed phrases of a component (`Cerrar aviso`, `Estable`, `Descartar`...).

- Actions: `EcopanButton` (Filled / Tonal / Outlined / Text / Accent; 44dp, `EcopanButtonSize.Touch` 56dp, pill),
  `EcopanTonalButton`, `EcopanOutlinedButton`, `EcopanTextButton`, `EcopanAccentButton` (the money button, with glow),
  `EcopanIconButton` (content description required).
- Status and labels: `StatusPill` (`StatusTone` Success / Warning / Danger / Info / Neutral / Simulated; always an icon and
  a word), `SimulatedChip` (honey, dashed border), `EcopanBadge`, `EcopanTag`, `EcopanFilterChip`, `SyncStatusIndicator`
  (+ `SyncIndicatorState`), `SectionHeader`, `ChipRow`, `HintMessage`.
- Input: `EcopanTextField`, `EcopanSelect`, `EcopanSegmentedSelector`, `EcopanSwitch`, `EcopanCheckbox`,
  `EcopanRadioButton`, `EcopanQuantityStepper`.
- Surfaces and feedback: `EcopanCard` (Default / Sunken / Accent / Outlined / Simulated), `EcopanAlert` (inline, announced
  status band: danger and warning are announced assertively; success, simulated dashed; optional supporting line and action),
  `EcopanDeviceCard` (one card per hardware device; simulated = honey, dashed, `SimulatedChip`, the only way to flag a demo device),
  `EcopanModal` / `EcopanModalSurface` (caller-sized modal), `EcopanDialog` (30dp), `EcopanToast`,
  `EcopanSnackbarHost`, `EcopanTooltip`, `EcopanTabs`, `EcopanListItem` (>= 56dp), `EcopanUnappliedChangesBar`.
- Till and baker: `ScaleReadout` (68sp weight, state row with icon and word, driver chip, price), `ReceiptPaper` (+ `ReceiptText`
  to build exact 32 / 48 column lines, `TornEdgeShape`), `ProductTile`, `StatTile`, `SummaryTile` (compact total for a row of figures), `EcopanDataTable`.

## Rules for feature authors

1. No hardcoded colours, fonts, sizes or shapes in features: use `EcopanTheme.*`, `EcopanSpacing`, `EcopanShapes` and the
   components. If one is missing, add it here.
2. Sentence case everywhere, no emoji. Icons come from `EcopanIcons` (outlined, line-style); every icon-only control needs a
   `contentDescription`.
3. Status is never carried by colour alone: use `StatusPill`, which always adds an icon and a word. Simulated data is honey and
   says "Simulado".
4. Money is `EcopanTheme.colors.price`, set in a tabular figure, and nothing else is that colour.
5. One filled primary action per screen; at most one Accent button.
6. Respect reduced motion: use `EcopanTheme.motion` specs and gate loops on `motion.reduced`.
7. Every new component gets a gallery entry in `preview/`; `EcopanGalleryTest` composes the page in light and dark on the JVM.

## Notes

- Outfit is bundled as the upstream variable font and instanced per weight with `FontVariation`; Google Fonts publishes no
  static Outfit files.
- Honey, `surfaceDark` and `onPeach` values are read from the design's tokens; `controlBorder` and `price` use the
  AA-corrected values the web dashboard adopted.
- Tests: `./gradlew :core:designsystem:jvmTest` (gallery, contrast of every text pair in both themes, approved token values,
  reduced-motion durations, receipt column widths).

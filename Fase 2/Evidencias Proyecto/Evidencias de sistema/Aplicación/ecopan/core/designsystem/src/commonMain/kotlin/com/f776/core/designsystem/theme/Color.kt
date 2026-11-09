package com.f776.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One status family: [fg] is the text and icon colour, [bg] the fill and [line] the hairline. A status
 * is never carried by these alone -- `StatusPill` always adds an icon and a word.
 */
@Immutable
class StatusColors(val fg: Color, val bg: Color, val line: Color)

/**
 * Everything the Ecopan design system names that Material has no role for: the crust/peach brand
 * ramps, the flour ground ladder, the ink ramp, the four status families, the simulated (honey)
 * family, the categorical chart palette, and the fixed receipt paper. Read as `EcopanTheme.colors`.
 *
 * Values are the web dashboard's tokens (`webApp/src/index.css`), which transcribe the Claude Design
 * project's `tokens/colors.css`; the light/dark pairs were measured against WCAG AA there.
 */
@Immutable
class EcopanColors(
    // Crust: identity and every primary action.
    val crust: Color,
    val crustHover: Color,
    val crustActive: Color,
    val crustSoft: Color,
    val crustLine: Color,
    // Peach: the accent, and money. [price] is for money and only money.
    val peach: Color,
    val peachHover: Color,
    val price: Color,
    val peachSoft: Color,
    /** Text and icons on a peach fill: the same warm near-black in both themes (8:1 on peach). */
    val onPeach: Color,
    // Flour: the neutral ground ladder.
    val page: Color,
    val card: Color,
    val sunken: Color,
    val inset: Color,
    val line: Color,
    val lineStrong: Color,
    val hairline: Color,
    // Ink: the text ramp.
    val inkStrong: Color,
    val inkBody: Color,
    val inkMuted: Color,
    val inkSubtle: Color,
    val inkInverse: Color,
    /** Border of an input control: darker than [line] so a white field has an identifiable edge (WCAG 1.4.11). */
    val controlBorder: Color,
    val success: StatusColors,
    val warning: StatusColors,
    val danger: StatusColors,
    val info: StatusColors,
    // Honey: the simulated family. Anything honey is not real data.
    val honey1: Color,
    val honey2: Color,
    val honey3: Color,
    val honeyInk: Color,
    /** Categorical series, assigned in fixed order and never cycled; fold the tail into one "Otros" bucket. */
    val charts: List<Color>,
    /** The dark bottom bar (unapplied changes). */
    val surfaceDark: Color,
    val onSurfaceDark: Color,
    /** The 3dp keyboard-focus ring. */
    val focusRing: Color,
    /** Colour of every shadow; warm in light, black in dark. */
    val shadow: Color,
    /** Fixed receipt paper: white and ink in both themes, because it is a picture of paper. */
    val paper: Color,
    val paperInk: Color,
    val paperMuted: Color,
    /** The honey strip printed above and below a simulated receipt, fixed like the paper it sits on. */
    val paperStrip: Color,
    val paperStripInk: Color,
) {
    /** Chart series [index], 0-based; throws past five -- fold the tail instead of cycling. */
    fun chart(index: Int): Color = charts[index]

    val chart1: Color get() = charts[0]
    val chart2: Color get() = charts[1]
    val chart3: Color get() = charts[2]
    val chart4: Color get() = charts[3]
    val chart5: Color get() = charts[4]
}

private val Paper = Color(0xFFFFFFFF)
private val PaperInk = Color(0xFF1E1A17)
private val PaperMuted = Color(0xFF6E6560)

internal val LightColors = EcopanColors(
    crust = Color(0xFF7A4A22),
    crustHover = Color(0xFF5A3520),
    crustActive = Color(0xFF3A2214),
    crustSoft = Color(0xFFFBF1E8),
    crustLine = Color(0xFFE8D3BC),
    peach = Color(0xFFF6A379),
    peachHover = Color(0xFFEE8A58),
    price = Color(0xFFA5541F),
    peachSoft = Color(0xFFFFF4EC),
    onPeach = Color(0xFF241E1B),
    page = Color(0xFFFBF8F5),
    card = Color(0xFFFFFFFF),
    sunken = Color(0xFFF7F5F1),
    inset = Color(0xFFF0EDE8),
    line = Color(0xFFE5E0D9),
    lineStrong = Color(0xFFCDC6BC),
    hairline = Color(0xFFF4F1ED),
    inkStrong = Color(0xFF241E1B),
    inkBody = Color(0xFF453E3A),
    inkMuted = Color(0xFF6E6560),
    inkSubtle = Color(0xFF7C756F),
    inkInverse = Color(0xFFFFFFFF),
    controlBorder = Color(0xFF948B80),
    success = StatusColors(Color(0xFF3E6B3A), Color(0xFFEEF5EC), Color(0xFFC9DEC4)),
    warning = StatusColors(Color(0xFF8A611A), Color(0xFFFFF7E8), Color(0xFFFBE6BC)),
    danger = StatusColors(Color(0xFF993A25), Color(0xFFFDECE8), Color(0xFFF6C9BF)),
    info = StatusColors(Color(0xFF3D5765), Color(0xFFEDF2F5), Color(0xFFC8D6DE)),
    honey1 = Color(0xFFFFF7E8),
    honey2 = Color(0xFFFBE6BC),
    honey3 = Color(0xFFE5A83E),
    honeyInk = Color(0xFF8A611A),
    charts = listOf(Color(0xFFC25A24), Color(0xFF0E6DA6), Color(0xFFA8830F), Color(0xFF933F63), Color(0xFF5C7A2B)),
    surfaceDark = Color(0xFF241E1B),
    onSurfaceDark = Color(0xFFFFFFFF),
    focusRing = Color(0xFFEE8A58),
    shadow = Color(0xFF241E1B),
    paper = Paper,
    paperInk = PaperInk,
    paperMuted = PaperMuted,
    paperStrip = Color(0xFFFBE6BC),
    paperStripInk = Color(0xFF8A611A),
)

internal val DarkColors = EcopanColors(
    crust = Color(0xFF9A5F2C),
    crustHover = Color(0xFFB47440),
    crustActive = Color(0xFF8A5528),
    crustSoft = Color(0xFF3A2A1E),
    crustLine = Color(0xFF5A3F2A),
    peach = Color(0xFFF0A375),
    peachHover = Color(0xFFF5B08A),
    price = Color(0xFFF0A375),
    peachSoft = Color(0xFF3A2A1E),
    onPeach = Color(0xFF241E1B),
    page = Color(0xFF1E1815),
    card = Color(0xFF272019),
    sunken = Color(0xFF171310),
    inset = Color(0xFF302821),
    line = Color(0xFF3F3731),
    lineStrong = Color(0xFF4F453E),
    hairline = Color(0xFF332B25),
    inkStrong = Color(0xFFF7F2EC),
    inkBody = Color(0xFFE4DAD1),
    inkMuted = Color(0xFFB2A79C),
    inkSubtle = Color(0xFF968B80),
    inkInverse = Color(0xFFFFFFFF),
    controlBorder = Color(0xFF847567),
    success = StatusColors(Color(0xFF8FBF87), Color(0xFF1F2A1D), Color(0xFF34492F)),
    warning = StatusColors(Color(0xFFE0B860), Color(0xFF2E2614), Color(0xFF4D3F1C)),
    danger = StatusColors(Color(0xFFEE9A80), Color(0xFF34201A), Color(0xFF5A3328)),
    info = StatusColors(Color(0xFF9FBDCD), Color(0xFF1C252A), Color(0xFF33454F)),
    honey1 = Color(0xFF2E2614),
    honey2 = Color(0xFF4D3F1C),
    honey3 = Color(0xFFA8831F),
    honeyInk = Color(0xFFE0B860),
    charts = listOf(Color(0xFFD95F19), Color(0xFF2891D5), Color(0xFFA88418), Color(0xFFC8628C), Color(0xFF739739)),
    surfaceDark = Color(0xFF3A3029),
    onSurfaceDark = Color(0xFFF7F2EC),
    focusRing = Color(0xFFF0A375),
    shadow = Color(0xFF000000),
    paper = Paper,
    paperInk = PaperInk,
    paperMuted = PaperMuted,
    paperStrip = Color(0xFFFBE6BC),
    paperStripInk = Color(0xFF8A611A),
)

/** Accent foreground on `crustSoft` in dark (`--text-brand` in tokens/dark.json). */
private val OnCrustSoftDark = Color(0xFFE8B98F)

/**
 * The Material scheme for [colors]. Roles Material has and the design names map one to one (primary is
 * crust, surface is the card, outline is the control border); the rest are read from [EcopanColors].
 */
internal fun ecopanColorScheme(colors: EcopanColors, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = colors.crust,
            onPrimary = colors.inkInverse,
            primaryContainer = colors.crustSoft,
            onPrimaryContainer = OnCrustSoftDark,
            inversePrimary = colors.crustHover,
            secondary = colors.inkMuted,
            onSecondary = colors.page,
            secondaryContainer = colors.inset,
            onSecondaryContainer = colors.inkBody,
            tertiary = colors.price,
            onTertiary = colors.page,
            tertiaryContainer = colors.peachSoft,
            onTertiaryContainer = colors.price,
            background = colors.page,
            onBackground = colors.inkBody,
            surface = colors.card,
            onSurface = colors.inkBody,
            surfaceVariant = colors.inset,
            onSurfaceVariant = colors.inkMuted,
            surfaceTint = colors.crust,
            inverseSurface = colors.inkStrong,
            inverseOnSurface = colors.page,
            error = colors.danger.fg,
            onError = LightColors.inkStrong,
            errorContainer = colors.danger.bg,
            onErrorContainer = colors.danger.fg,
            outline = colors.controlBorder,
            outlineVariant = colors.line,
            scrim = Color.Black,
            surfaceBright = colors.inset,
            surfaceDim = colors.sunken,
            surfaceContainerLowest = colors.sunken,
            surfaceContainerLow = colors.page,
            surfaceContainer = colors.card,
            surfaceContainerHigh = colors.inset,
            surfaceContainerHighest = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.crust,
            onPrimary = colors.inkInverse,
            primaryContainer = colors.crustSoft,
            onPrimaryContainer = colors.crust,
            inversePrimary = colors.peach,
            secondary = colors.inkMuted,
            onSecondary = colors.card,
            secondaryContainer = colors.inset,
            onSecondaryContainer = colors.inkBody,
            tertiary = colors.price,
            onTertiary = colors.card,
            tertiaryContainer = colors.peachSoft,
            onTertiaryContainer = colors.price,
            background = colors.page,
            onBackground = colors.inkBody,
            surface = colors.card,
            onSurface = colors.inkBody,
            surfaceVariant = colors.sunken,
            onSurfaceVariant = colors.inkMuted,
            surfaceTint = colors.crust,
            inverseSurface = colors.inkStrong,
            inverseOnSurface = colors.card,
            error = colors.danger.fg,
            onError = colors.inkInverse,
            errorContainer = colors.danger.bg,
            onErrorContainer = colors.danger.fg,
            outline = colors.controlBorder,
            outlineVariant = colors.line,
            scrim = Color.Black,
            surfaceBright = colors.card,
            surfaceDim = colors.inset,
            surfaceContainerLowest = colors.card,
            surfaceContainerLow = colors.page,
            surfaceContainer = colors.sunken,
            surfaceContainerHigh = colors.inset,
            surfaceContainerHighest = colors.line,
        )
    }

internal val LocalEcopanColors = staticCompositionLocalOf { LightColors }

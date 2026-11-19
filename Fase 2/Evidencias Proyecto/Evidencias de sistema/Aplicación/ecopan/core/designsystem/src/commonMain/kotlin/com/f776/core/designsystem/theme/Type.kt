package com.f776.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ecopan.core.designsystem.generated.resources.IBMPlexMono_Bold
import ecopan.core.designsystem.generated.resources.IBMPlexMono_Medium
import ecopan.core.designsystem.generated.resources.IBMPlexMono_Regular
import ecopan.core.designsystem.generated.resources.IBMPlexMono_SemiBold
import ecopan.core.designsystem.generated.resources.Outfit_Variable
import ecopan.core.designsystem.generated.resources.Res
import org.jetbrains.compose.resources.Font

/** Tabular figures: every price, weight and count sits in a column or changes in place. */
private const val Tabular = "tnum"

/**
 * One geometric sans, Outfit, carries the whole interface; IBM Plex Mono appears only where characters
 * must line up as data (timestamps, ids, receipt lines).
 */
@Immutable
class EcopanFontFamilies(val sans: FontFamily, val mono: FontFamily)

@Composable
internal fun rememberEcopanFontFamilies(): EcopanFontFamilies {
    @Composable fun outfit(weight: FontWeight) = Font(
        Res.font.Outfit_Variable,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
    val sans = FontFamily(
        outfit(FontWeight.Normal),
        outfit(FontWeight.Medium),
        outfit(FontWeight.SemiBold),
        outfit(FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.IBMPlexMono_Regular, FontWeight.Normal),
        Font(Res.font.IBMPlexMono_Medium, FontWeight.Medium),
        Font(Res.font.IBMPlexMono_SemiBold, FontWeight.SemiBold),
        Font(Res.font.IBMPlexMono_Bold, FontWeight.Bold),
    )
    return EcopanFontFamilies(sans, mono)
}

/**
 * The named roles of the design's type scale (readme "Type"), one family, tracking pulled tighter as
 * size grows. The Material scale (`MaterialTheme.typography`) is mapped onto the same faces.
 */
@Immutable
class EcopanTypography(
    /** 52 / 1.1, hero numbers and marketing-sized headings. */
    val display: TextStyle,
    /** 32 / 1.24. */
    val headline: TextStyle,
    /** 26 / 1.24, screen titles. */
    val title: TextStyle,
    /** 18 / 1.32, card and section titles. */
    val subtitle: TextStyle,
    /** 16 / 1.5. */
    val body: TextStyle,
    /** 14 / 1.4, secondary copy. */
    val bodySmall: TextStyle,
    /** 14 / 1.25 medium, buttons, fields, chips. */
    val label: TextStyle,
    /** 12 semibold, the quiet heading above a card group. */
    val eyebrow: TextStyle,
    /** 32 semibold tabular: stat values and large figures. */
    val numeric: TextStyle,
    /** Body-sized tabular figures: money, weights and counts in running UI. */
    val figure: TextStyle,
    /** 28 semibold tabular: the number a row exists to show (baker's suggested production). */
    val quantityLg: TextStyle,
    /** 22 semibold tabular: a balance on a prominent list row. */
    val quantityMd: TextStyle,
    /** 20 semibold tabular: a balance on a secondary list row. */
    val quantitySm: TextStyle,
    /** 13 mono tabular: timestamps, device ids, key/value rows. Never an interface voice. */
    val ledger: TextStyle,
    /** 68sp tabular: the scale's weight. */
    val scaleReadout: TextStyle,
)

internal fun ecopanTypography(fonts: EcopanFontFamilies): EcopanTypography {
    val sans = fonts.sans
    return EcopanTypography(
        display = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 57.sp, letterSpacing = (-0.018).em),
        headline = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.012).em),
        title = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.012).em),
        subtitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.012).em),
        body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        label = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.01.em),
        eyebrow = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.01.em),
        numeric = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-0.012).em, fontFeatureSettings = Tabular),
        figure = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = Tabular),
        quantityLg = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.012).em, fontFeatureSettings = Tabular),
        quantityMd = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.012).em, fontFeatureSettings = Tabular),
        quantitySm = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.012).em, fontFeatureSettings = Tabular),
        ledger = TextStyle(fontFamily = fonts.mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = Tabular),
        scaleReadout = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 68.sp, lineHeight = 72.sp, letterSpacing = (-0.018).em, fontFeatureSettings = Tabular),
    )
}

/** The Material 3 scale on the design's faces, weights and sizes. */
internal fun ecopanMaterialTypography(fonts: EcopanFontFamilies): Typography {
    val t = ecopanTypography(fonts)
    return Typography(
        displayLarge = t.display,
        displayMedium = t.display.copy(fontSize = 44.sp, lineHeight = 50.sp),
        displaySmall = t.display.copy(fontSize = 36.sp, lineHeight = 42.sp),
        headlineLarge = t.headline,
        headlineMedium = t.title,
        headlineSmall = t.title.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = t.title.copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = t.subtitle,
        titleSmall = t.label.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = t.body,
        bodyMedium = t.bodySmall,
        bodySmall = t.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = t.label,
        labelMedium = t.label.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = t.label.copy(fontSize = 11.sp, lineHeight = 14.sp),
    )
}

/**
 * Re-sets a text style with tabular figures, for **figures**: money, weights, counts, keypad digits.
 * They sit in columns and change in place, so proportional digits would make a settling readout look
 * like it is shifting. Reach for this on any number the user reads as a quantity, at any size.
 */
fun figureStyle(base: TextStyle): TextStyle = base.copy(fontFeatureSettings = Tabular)

/** IBM Plex Mono, for genuinely monospaced runs: ids, scanned barcodes, receipt lines. */
@Composable
fun monoFontFamily(): FontFamily = EcopanTheme.fonts.mono

internal val LocalEcopanFonts = staticCompositionLocalOf<EcopanFontFamilies?> { null }
internal val LocalEcopanTypography = staticCompositionLocalOf<EcopanTypography?> { null }

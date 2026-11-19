@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.f776.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Themes [content] with the Ecopan design system: the Material colour scheme, type scale and shapes,
 * plus the extended [EcopanTheme.colors], [EcopanTheme.typography] and [EcopanTheme.motion]. Wrap every
 * screen and every preview in it.
 *
 * [dynamicColor] takes the Material roles from the wallpaper where the platform has such a thing
 * (Android 12+); the extended colours (status, price, honey, charts) stay Ecopan's own, because
 * their meaning must not change with the wallpaper.
 */
@Composable
fun EcopanTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    SystemBarIcons(darkTheme)
    val colors = if (darkTheme) DarkColors else LightColors
    val dynamicScheme = if (dynamicColor) dynamicEcopanColorScheme(darkTheme) else null
    val colorScheme = dynamicScheme ?: remember(darkTheme) { ecopanColorScheme(colors, darkTheme) }
    val fonts = rememberEcopanFontFamilies()
    val typography = remember(fonts) { ecopanTypography(fonts) }
    val materialTypography = remember(fonts) { ecopanMaterialTypography(fonts) }
    val reduced = rememberReducedMotion()
    val motion = remember(reduced) { EcopanMotion(reduced) }
    val shapes = remember {
        Shapes(
            extraSmall = EcopanShapes.extraSmall,
            small = EcopanShapes.small,
            medium = EcopanShapes.medium,
            large = EcopanShapes.large,
            extraLarge = EcopanShapes.extraLarge,
        )
    }

    CompositionLocalProvider(
        LocalEcopanColors provides colors,
        LocalEcopanFonts provides fonts,
        LocalEcopanTypography provides typography,
        LocalEcopanMotion provides motion,
        LocalEcopanDarkTheme provides darkTheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = materialTypography,
            shapes = shapes,
            content = content,
        )
    }
}

internal val LocalEcopanDarkTheme = staticCompositionLocalOf { false }

/** Accessors for everything the current [EcopanTheme] provides. */
object EcopanTheme {
    /** The Material colour scheme; use its roles for everything the design names by a Material role. */
    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

    /** Crust, peach, price, flour, ink, status, honey and chart colours. */
    val colors: EcopanColors
        @Composable @ReadOnlyComposable get() = LocalEcopanColors.current

    /** The design's named type roles; the Material scale is `MaterialTheme.typography`. */
    val typography: EcopanTypography
        @Composable @ReadOnlyComposable get() = checkNotNull(LocalEcopanTypography.current) { "Wrap content in EcopanTheme" }

    val fonts: EcopanFontFamilies
        @Composable @ReadOnlyComposable get() = checkNotNull(LocalEcopanFonts.current) { "Wrap content in EcopanTheme" }

    val shapes: EcopanShapes
        get() = EcopanShapes

    val spacing: EcopanSpacing
        get() = EcopanSpacing

    val motion: EcopanMotion
        @Composable @ReadOnlyComposable get() = LocalEcopanMotion.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalEcopanDarkTheme.current
}

/** A colour scheme from the platform's dynamic colours, or null where there are none. */
@Composable
internal expect fun dynamicEcopanColorScheme(darkTheme: Boolean): ColorScheme?

/**
 * Draws the system bars' icons light on a dark theme and dark on a light one: the platform's own
 * choice follows the device theme, not the app's. Does nothing where there are no system bars.
 */
@Composable
internal expect fun SystemBarIcons(darkTheme: Boolean)

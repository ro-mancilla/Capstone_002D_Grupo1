package com.f776.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation is flat by default: depth is a fill and a hairline. Where a shadow appears it is wide,
 * soft and warm -- never a dark drop, and never on a button (the one exception is the money
 * button's accent glow). The dp values approximate the CSS tokens `--shadow-sm` (0 2px 8px .045),
 * `--shadow-md`, `--shadow-lg` and `--shadow-xl`.
 */
enum class EcopanElevation(internal val radius: Dp, internal val alpha: Float) {
    /** Cards: `0 2px 8px rgba(36,30,27,.045)`. */
    Card(4.dp, 0.07f),
    Raised(8.dp, 0.09f),
    Overlay(14.dp, 0.11f),

    /** Dialogs and sheets: `--shadow-xl`. */
    Dialog(24.dp, 0.14f),
}

/** The warm shadow of [level] under [shape]. Dark themes fall back to near-black, which is barely visible by design. */
@Composable
fun Modifier.ecopanShadow(level: EcopanElevation, shape: Shape): Modifier {
    val tint = EcopanTheme.colors.shadow
    val boost = if (EcopanTheme.isDark) 2.2f else 1f
    val color = tint.copy(alpha = (level.alpha * boost).coerceAtMost(1f))
    return shadow(level.radius, shape, clip = false, ambientColor = color, spotColor = color)
}

/** The accent glow under the money button: peach, wide, once per screen. */
@Composable
fun Modifier.ecopanAccentGlow(shape: Shape, color: Color = EcopanTheme.colors.peach): Modifier =
    shadow(14.dp, shape, clip = false, ambientColor = color.copy(alpha = 0.30f), spotColor = color.copy(alpha = 0.45f))

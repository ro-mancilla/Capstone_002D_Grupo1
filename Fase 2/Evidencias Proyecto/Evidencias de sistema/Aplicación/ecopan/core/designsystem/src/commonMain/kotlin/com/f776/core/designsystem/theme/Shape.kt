package com.f776.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Pill-first geometry (design `tokens/base.css`): controls, chips and badges are fully rounded
 * ([pill]); containers are generously soft. The Material scale is 8 / 12 / 16 / 22 dp, with a 4dp
 * `extraSmall` for tooltips and 30dp [dialog]. Read as `EcopanTheme.shapes`.
 */
@Immutable
object EcopanShapes {
    val none: Shape = RoundedCornerShape(0.dp)
    val extraSmall: RoundedCornerShape = RoundedCornerShape(4.dp)
    val small: RoundedCornerShape = RoundedCornerShape(8.dp)
    val medium: RoundedCornerShape = RoundedCornerShape(12.dp)
    val large: RoundedCornerShape = RoundedCornerShape(16.dp)
    val extraLarge: RoundedCornerShape = RoundedCornerShape(22.dp)

    /** Dialogs and bottom sheets. */
    val dialog: RoundedCornerShape = RoundedCornerShape(30.dp)

    /** Fully rounded: buttons, chips, badges, pills, switches. */
    val pill: Shape = CircleShape

    /** Text fields and selects: 12dp. */
    val control: RoundedCornerShape = medium

    /** Cards: 22dp. */
    val card: RoundedCornerShape = extraLarge
}

package com.f776.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanStateLayer
import com.f776.core.designsystem.theme.FocusRingWidth

/**
 * Draws the keyboard-focus ring (3dp, peach) just outside the bounds when [focused]. Place it
 * **before** the background in the modifier chain so the fill covers the inner half of the stroke.
 */
internal fun Modifier.focusRing(focused: Boolean, shape: Shape, color: Color): Modifier =
    if (!focused) {
        this
    } else {
        drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val width = FocusRingWidth.dp.toPx()
            drawOutline(outline, color, style = Stroke(width = width * 2))
        }
    }

/** A disabled control is drawn at 40% as a whole: fill, border and content together. */
internal fun Modifier.disabledAlpha(enabled: Boolean): Modifier =
    if (enabled) this else alpha(EcopanStateLayer.DisabledAlpha)

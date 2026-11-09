package com.f776.core.designsystem.theme

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/**
 * Interaction rules of the design system (readme "Interaction"): hover and press darken the fill by
 * one ramp step (no scale, no overlay), keyboard focus draws a 3dp peach ring ([FocusRingWidth]),
 * and a disabled control is drawn at [DisabledAlpha].
 */
object EcopanStateLayer {
    const val DisabledAlpha = 0.4f
}

/** Width of the keyboard-focus ring, in dp. */
const val FocusRingWidth = 3f

/** What a control is currently being done to. */
@Immutable
class InteractionFlags(val hovered: Boolean, val pressed: Boolean, val focused: Boolean)

@Composable
fun InteractionSource.collectInteractionFlags(): InteractionFlags {
    val hovered by collectIsHoveredAsState()
    val pressed by collectIsPressedAsState()
    val focused by collectIsFocusedAsState()
    return InteractionFlags(hovered, pressed, focused)
}

/**
 * The fill of a control that rests on [rest], moves to [hover] under the pointer and to [pressed]
 * while pressed. A disabled control keeps [rest] (it is faded as a whole, see `disabledAlpha`).
 */
fun stateFill(rest: Color, hover: Color, pressed: Color, flags: InteractionFlags, enabled: Boolean): Color = when {
    !enabled -> rest
    flags.pressed -> pressed
    flags.hovered -> hover
    else -> rest
}

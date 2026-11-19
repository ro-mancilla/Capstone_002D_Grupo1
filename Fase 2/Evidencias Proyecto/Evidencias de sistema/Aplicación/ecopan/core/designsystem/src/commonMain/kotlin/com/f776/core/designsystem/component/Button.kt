package com.f776.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags
import com.f776.core.designsystem.theme.ecopanAccentGlow
import com.f776.core.designsystem.theme.stateFill

/** Emphasis of an [EcopanButton]. One filled primary action per screen; prefer [Tonal] elsewhere. */
enum class EcopanButtonVariant {
    /** Crust fill, white text: the primary action. */
    Filled,

    /** Soft crust fill, crust text. */
    Tonal,

    /** Transparent with a control border. */
    Outlined,

    /** Text only. */
    Text,

    /** Peach fill with a glow: the money action ("Cobrar"). At most one per screen. */
    Accent,
}

/** [Default] is the 44dp control; [Touch] is the 56dp till button pressed with a floured thumb. */
enum class EcopanButtonSize(internal val minHeight: androidx.compose.ui.unit.Dp) {
    Default(EcopanSpacing.controlMin),
    Touch(EcopanSpacing.touch),
}

@Immutable
private class ButtonColors(
    val rest: Color,
    val hover: Color,
    val pressed: Color,
    val content: Color,
    val border: Color?,
)

@Composable
private fun buttonColors(variant: EcopanButtonVariant): ButtonColors {
    val c = EcopanTheme.colors
    val accentText = EcopanTheme.colorScheme.onPrimaryContainer
    return when (variant) {
        EcopanButtonVariant.Filled -> ButtonColors(c.crust, c.crustHover, c.crustActive, c.inkInverse, null)
        EcopanButtonVariant.Tonal -> ButtonColors(c.crustSoft, c.crustLine, c.crustLine, accentText, null)
        EcopanButtonVariant.Outlined ->
            ButtonColors(Color.Transparent, c.crustSoft, c.crustLine, accentText, c.controlBorder)

        EcopanButtonVariant.Text -> ButtonColors(Color.Transparent, c.crustSoft, c.crustLine, accentText, null)
        EcopanButtonVariant.Accent -> ButtonColors(c.peach, c.peachHover, c.peachHover, c.onPeach, null)
    }
}

/**
 * The button. Pill-shaped; hover and press darken the fill by one ramp step (no scale), keyboard
 * focus draws the 3dp peach ring, disabled draws at 40%. [content] is laid out in a centred row.
 */
@Composable
fun EcopanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: EcopanButtonVariant = EcopanButtonVariant.Filled,
    size: EcopanButtonSize = EcopanButtonSize.Default,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = buttonColors(variant)
    val shape = EcopanShapes.pill
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val motion = EcopanTheme.motion
    val fill by animateColorAsState(
        stateFill(colors.rest, colors.hover, colors.pressed, flags, enabled),
        animationSpec = motion.fast(),
        label = "buttonFill",
    )
    val ring = EcopanTheme.colors.focusRing
    val textStyle = if (size == EcopanButtonSize.Touch) {
        EcopanTheme.typography.label.copy(fontSize = 16.sp, lineHeight = 20.sp)
    } else {
        EcopanTheme.typography.label
    }
    Row(
        modifier = modifier
            .disabledAlpha(enabled)
            .then(if (variant == EcopanButtonVariant.Accent && enabled) Modifier.ecopanAccentGlow(shape) else Modifier)
            .focusRing(flags.focused, shape, ring)
            .background(fill, shape)
            .then(if (colors.border != null) Modifier.border(BorderStroke(1.dp, colors.border), shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minWidth = size.minHeight, minHeight = size.minHeight)
            .padding(horizontal = if (size == EcopanButtonSize.Touch) 28.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.content,
            LocalTextStyle provides textStyle,
        ) { content() }
    }
}

/** A button with a [text] label and optional icons. */
@Composable
fun EcopanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: EcopanButtonVariant = EcopanButtonVariant.Filled,
    size: EcopanButtonSize = EcopanButtonSize.Default,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    EcopanButton(onClick, modifier, variant, size, enabled) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, maxLines = 1)
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun EcopanTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EcopanButtonSize = EcopanButtonSize.Default,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) = EcopanButton(text, onClick, modifier, EcopanButtonVariant.Tonal, size, enabled, leadingIcon)

@Composable
fun EcopanOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EcopanButtonSize = EcopanButtonSize.Default,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) = EcopanButton(text, onClick, modifier, EcopanButtonVariant.Outlined, size, enabled, leadingIcon)

@Composable
fun EcopanTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) = EcopanButton(text, onClick, modifier, EcopanButtonVariant.Text, EcopanButtonSize.Default, enabled, leadingIcon)

/** The money button: peach with a glow. Reserve it for taking payment. */
@Composable
fun EcopanAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EcopanButtonSize = EcopanButtonSize.Touch,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) = EcopanButton(text, onClick, modifier, EcopanButtonVariant.Accent, size, enabled, leadingIcon)

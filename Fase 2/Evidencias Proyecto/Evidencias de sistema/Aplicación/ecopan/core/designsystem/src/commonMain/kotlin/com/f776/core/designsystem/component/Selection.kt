package com.f776.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags

/**
 * The switch: a 52x32 pill track, crust when on and control-border grey when off, with a white
 * thumb. Wrapped in a 44dp touch target. Label it with a sibling text and `Modifier.toggleable` on
 * the row, or pass the label via the surrounding `ListItem`.
 */
@Composable
fun EcopanSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = EcopanTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val motion = EcopanTheme.motion
    val track by animateColorAsState(
        if (checked) (if (flags.hovered) c.crustHover else c.crust) else c.controlBorder,
        animationSpec = motion.fast(),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 2.dp, animationSpec = motion.fast(), label = "switchThumb")
    Box(
        modifier = modifier
            .disabledAlpha(enabled)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .size(width = 52.dp, height = EcopanSpacing.controlMin),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .focusRing(flags.focused, EcopanShapes.pill, c.focusRing)
                .size(width = 52.dp, height = 32.dp)
                .background(track, EcopanShapes.pill),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(28.dp)
                    .background(c.card, CircleShape),
            )
        }
    }
}

/** A 24dp checkbox with a check mark (the state is shape, not only colour), in a 44dp touch target. */
@Composable
fun EcopanCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = EcopanTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val shape = EcopanShapes.small
    val fill by animateColorAsState(
        if (checked) (if (flags.hovered) c.crustHover else c.crust) else c.card,
        animationSpec = EcopanTheme.motion.fast(),
        label = "checkboxFill",
    )
    Box(
        modifier = modifier
            .disabledAlpha(enabled)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .size(EcopanSpacing.controlMin),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .focusRing(flags.focused, shape, c.focusRing)
                .size(24.dp)
                .background(fill, shape)
                .border(BorderStroke(if (checked) 0.dp else 1.5.dp, c.controlBorder), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(EcopanIcons.Check, contentDescription = null, tint = c.inkInverse, modifier = Modifier.size(18.dp))
        }
    }
}

/** A 24dp radio button with an inner dot, in a 44dp touch target. */
@Composable
fun EcopanRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = EcopanTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val ring by animateColorAsState(
        if (selected) c.crust else c.controlBorder,
        animationSpec = EcopanTheme.motion.fast(),
        label = "radioRing",
    )
    Box(
        modifier = modifier
            .disabledAlpha(enabled)
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .size(EcopanSpacing.controlMin),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .focusRing(flags.focused, CircleShape, c.focusRing)
                .size(24.dp)
                .background(c.card, CircleShape)
                .border(BorderStroke(if (selected) 2.dp else 1.5.dp, ring), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(12.dp).background(c.crust, CircleShape))
        }
    }
}

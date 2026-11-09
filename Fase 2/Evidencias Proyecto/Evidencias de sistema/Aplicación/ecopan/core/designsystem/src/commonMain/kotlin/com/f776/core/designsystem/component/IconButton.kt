package com.f776.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags
import com.f776.core.designsystem.theme.stateFill

enum class EcopanIconButtonVariant { Standard, Tonal, Filled, Outlined }

/**
 * An icon-only button, a circle of 44dp ([touch] = 56dp). [contentDescription] is required because
 * there is no label; pass a real string, never null.
 */
@Composable
fun EcopanIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: EcopanIconButtonVariant = EcopanIconButtonVariant.Standard,
    touch: Boolean = false,
    enabled: Boolean = true,
) {
    val c = EcopanTheme.colors
    val accentText = EcopanTheme.colorScheme.onPrimaryContainer
    val rest: Color
    val hover: Color
    val pressed: Color
    val content: Color
    when (variant) {
        EcopanIconButtonVariant.Standard -> {
            rest = Color.Transparent; hover = c.inset; pressed = c.line; content = c.inkBody
        }

        EcopanIconButtonVariant.Tonal -> {
            rest = c.crustSoft; hover = c.crustLine; pressed = c.crustLine; content = accentText
        }

        EcopanIconButtonVariant.Filled -> {
            rest = c.crust; hover = c.crustHover; pressed = c.crustActive; content = c.inkInverse
        }

        EcopanIconButtonVariant.Outlined -> {
            rest = Color.Transparent; hover = c.crustSoft; pressed = c.crustLine; content = accentText
        }
    }
    val shape = EcopanShapes.pill
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val fill by animateColorAsState(
        stateFill(rest, hover, pressed, flags, enabled),
        animationSpec = EcopanTheme.motion.fast(),
        label = "iconButtonFill",
    )
    val dimension = if (touch) EcopanSpacing.touch else EcopanSpacing.controlMin
    Box(
        modifier = modifier
            .disabledAlpha(enabled)
            .focusRing(flags.focused, shape, c.focusRing)
            .size(dimension)
            .background(fill, shape)
            .then(
                if (variant == EcopanIconButtonVariant.Outlined) {
                    Modifier.border(BorderStroke(1.dp, c.controlBorder), shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (touch) 28.dp else 22.dp))
    }
}

package com.f776.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags

/** A quiet, non-interactive label: a category, a unit, a branch. */
@Composable
fun EcopanTag(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = EcopanTheme.colors
    Row(
        modifier = modifier
            .background(c.inset, EcopanShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(14.dp))
        Text(label, style = EcopanTheme.typography.eyebrow, color = c.inkBody, maxLines = 1)
    }
}

/**
 * A selectable filter: [selected] fills with soft crust and shows a check, so the state is never
 * carried by colour alone. 44dp tall.
 */
@Composable
fun EcopanFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = EcopanTheme.colors
    val shape = EcopanShapes.pill
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val rest = if (selected) c.crustSoft else c.card
    val fill by animateColorAsState(
        if (enabled && flags.hovered) (if (selected) c.crustLine else c.inset) else rest,
        animationSpec = EcopanTheme.motion.fast(),
        label = "chipFill",
    )
    val borderColor = if (selected) c.crust else c.controlBorder
    val contentColor = if (selected) EcopanTheme.colorScheme.onPrimaryContainer else c.inkBody
    Row(
        modifier = modifier
            .disabledAlpha(enabled)
            .focusRing(flags.focused, shape, c.focusRing)
            .background(fill, shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = EcopanSpacing.controlMin)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (selected) EcopanIcons.Check else leadingIcon
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Text(label, style = EcopanTheme.typography.label, color = contentColor, maxLines = 1)
    }
}

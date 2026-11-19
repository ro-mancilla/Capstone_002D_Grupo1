package com.f776.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanShapes
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags

/**
 * A pill toggle with one segment per option ("Ingreso manual / Balanza serial / Simulada"). The
 * selected segment is filled with crust and the others stay flat, so one glance finds the mode.
 * Segments are 44dp tall, or 56dp with [touch].
 */
@Composable
fun <T> EcopanSegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touch: Boolean = false,
    icon: (T) -> ImageVector? = { null },
) {
    val c = EcopanTheme.colors
    Row(
        modifier = modifier
            .disabledAlpha(enabled)
            .background(c.inset, EcopanShapes.pill)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (option in options) {
            Segment(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                touch = touch,
                modifier = Modifier.weight(1f),
            ) {
                val tint = if (option == selected) c.inkInverse else c.inkMuted
                icon(option)?.let { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
                Text(
                    label(option),
                    style = EcopanTheme.typography.label,
                    color = tint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Segment(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    touch: Boolean,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = EcopanTheme.colors
    val shape = EcopanShapes.pill
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val target = when {
        selected -> if (flags.hovered) c.crustHover else c.crust
        flags.hovered -> c.line
        else -> c.inset
    }
    val fill by animateColorAsState(target, animationSpec = EcopanTheme.motion.fast(), label = "segmentFill")
    Row(
        modifier = modifier
            .focusRing(flags.focused, shape, c.focusRing)
            .background(fill, shape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = if (touch) EcopanSpacing.touch else EcopanSpacing.controlMin)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

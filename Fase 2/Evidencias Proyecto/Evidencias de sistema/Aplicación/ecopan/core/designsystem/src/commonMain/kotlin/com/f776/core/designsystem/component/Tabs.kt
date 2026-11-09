package com.f776.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags

/**
 * A row of text tabs with a crust underline under the selected one, over a hairline. Stateless:
 * the caller owns [selectedIndex]. The selected tab is also semibold so it never depends on colour.
 */
@Composable
fun EcopanTabs(
    selectedIndex: Int,
    tabs: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EcopanTheme.colors
    Box(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.line))
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { index, title ->
                Tab(title, index == selectedIndex) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun Tab(title: String, selected: Boolean, onClick: () -> Unit) {
    val c = EcopanTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    val ink by animateColorAsState(
        if (selected) EcopanTheme.colorScheme.onPrimaryContainer else if (flags.hovered) c.inkBody else c.inkMuted,
        animationSpec = EcopanTheme.motion.fast(),
        label = "tabInk",
    )
    val underline by animateColorAsState(
        if (selected) c.crust else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = EcopanTheme.motion.base(),
        label = "tabUnderline",
    )
    Box(
        modifier = Modifier
            .focusRing(flags.focused, EcopanTheme.shapes.small, c.focusRing)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = EcopanSpacing.controlMin)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = EcopanTheme.typography.label.copy(
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
            ),
            color = ink,
            maxLines = 1,
        )
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(underline))
    }
}

package com.f776.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.collectInteractionFlags

/**
 * A list row at least 56dp tall: optional [leading] and [trailing] slots around a [headline] and an
 * optional [supporting] line. A hairline closes the row when [divider] is set. Pass [onClick] to
 * make the row a button; otherwise it is inert content.
 */
@Composable
fun EcopanListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
) {
    val c = EcopanTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val flags = interaction.collectInteractionFlags()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRing(onClick != null && flags.focused, EcopanTheme.shapes.small, c.focusRing)
                .background(if (onClick != null && flags.hovered) c.sunken else androidx.compose.ui.graphics.Color.Transparent)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .defaultMinSize(minHeight = EcopanSpacing.rowMin)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) Box(contentAlignment = Alignment.Center) { leading() }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    headline,
                    style = EcopanTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = c.inkStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(supporting, style = EcopanTheme.typography.bodySmall, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) Box(contentAlignment = Alignment.CenterEnd) { trailing() }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    }
}

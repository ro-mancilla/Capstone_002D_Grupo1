package com.f776.ecopan.feature.settings.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.component.StatusPill
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.core.designsystem.theme.EcopanTheme
import ecopan.feature.settings.impl.generated.resources.Res
import ecopan.feature.settings.impl.generated.resources.permission_grant_action
import ecopan.feature.settings.impl.generated.resources.permission_missing
import ecopan.feature.settings.impl.generated.resources.permission_ok
import org.jetbrains.compose.resources.stringResource

/**
 * The eyebrow over a settings card. Deliberately quiet — muted, not accent-coloured, and no
 * heavier than the rows beneath it: these labels name a group of controls the user is already
 * looking at, so a column of bold accent headings only competed with the settings themselves.
 */
@Composable
internal fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
internal fun PermissionListItem(
    title: String,
    explanation: String,
    isOk: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(explanation)
                // An explicit button, because the row's tappability was invisible: a missing
                // permission looked like a read-only status line, and the only way to discover
                // that tapping it opened the system prompt was to try.
                if (!isOk) {
                    OutlinedButton(onClick = onClick) {
                        Text(stringResource(Res.string.permission_grant_action))
                    }
                }
            }
        },
        trailingContent = {
            // A pill, not bare coloured text: "Sin permiso" in red read as an error message about
            // the row rather than as the row's current state.
            StatusPill(
                label = if (isOk) stringResource(Res.string.permission_ok) else stringResource(Res.string.permission_missing),
                tone = if (isOk) StatusTone.Success else StatusTone.Danger,
            )
        },
        // Transparent so the row takes the enclosing card's surface instead of painting its own
        // over it — ListItem's default container is `surface`, which is a different value from
        // the card's inside a themed OutlinedCard.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // The whole row stays tappable too: the button is the affordance, not the only target.
        modifier = Modifier.clickable(enabled = !isOk) { onClick() }
    )
}

@LocalizedPreview
@Composable
private fun SettingsCategoryHeaderPreview() {
    EcopanTheme {
        Surface {
            SettingsCategoryHeader(title = "Category")
        }
    }
}

@LocalizedPreview
@Composable
private fun PermissionListItemPreview() {
    EcopanTheme {
        Surface {
            PermissionListItem(
                title = "Permission",
                explanation = "Explanation",
                isOk = false,
                onClick = {}
            )
        }
    }
}

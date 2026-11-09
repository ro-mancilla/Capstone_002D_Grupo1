package com.f776.ecopan.feature.settings.screens.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.ecopan.feature.settings.screens.settings.components.AboutDialog
import com.f776.ecopan.feature.settings.screens.settings.components.PermissionListItem
import ecopan.feature.settings.impl.generated.resources.Res
import ecopan.feature.settings.impl.generated.resources.alarms_explanation
import ecopan.feature.settings.impl.generated.resources.alarms_label
import ecopan.feature.settings.impl.generated.resources.dynamic_color_explanation
import ecopan.feature.settings.impl.generated.resources.dynamic_color_label
import ecopan.feature.settings.impl.generated.resources.notifications_explanation
import ecopan.feature.settings.impl.generated.resources.notifications_label
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun SettingsScreen(
    onBack: (() -> Unit)?,
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAboutDialog by remember { mutableStateOf(false) }

    // TODO: Handle notification permission rationale (open settings)
    val showAlarmItem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    val showNotificationItem = viewModel.isNotificationPermissionRequired
    val showPermissions = showAlarmItem || showNotificationItem

    SettingsScreenContent(
        uiState = uiState,
        onBack = onBack,
        onThemeSelected = viewModel::setTheme,
        onRetryRejected = viewModel::retryRejected,
        onDiscardRejected = viewModel::discardRejected,
        onAboutClick = { showAboutDialog = true },
        dynamicColorSetting = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.dynamic_color_label)) },
                    supportingContent = { Text(stringResource(Res.string.dynamic_color_explanation)) },
                    trailingContent = {
                        Switch(
                            checked = uiState.useDynamicColors,
                            onCheckedChange = viewModel::toggleDynamicColors
                        )
                    },
                    // Transparent: the row lives inside the appearance card, whose surface it
                    // should inherit rather than repaint.
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = { viewModel.toggleDynamicColors(!uiState.useDynamicColors) })
                )
            }
        },
        alarmPermissionSetting = {
            if (showAlarmItem) {
                PermissionListItem(
                    title = stringResource(Res.string.alarms_label),
                    explanation = stringResource(Res.string.alarms_explanation),
                    isOk = uiState.hasAlarmPermission,
                    onClick = viewModel::requestAlarmPermission
                )
            }
        },
        notificationPermissionSetting = {
            if (showNotificationItem) {
                PermissionListItem(
                    title = stringResource(Res.string.notifications_label),
                    explanation = stringResource(Res.string.notifications_explanation),
                    isOk = uiState.hasNotificationPermission,
                    onClick = viewModel::requestNotificationPermission
                )
            }
        },
        showPermissionsSection = showPermissions
    )

    if (showAboutDialog) {
        AboutDialog(onDismissRequest = { showAboutDialog = false })
    }
}

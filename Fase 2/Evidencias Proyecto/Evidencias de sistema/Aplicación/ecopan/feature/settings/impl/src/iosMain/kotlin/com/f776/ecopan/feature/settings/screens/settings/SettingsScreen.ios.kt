package com.f776.ecopan.feature.settings.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.ecopan.feature.settings.screens.settings.components.AboutDialog

@Composable
internal actual fun SettingsScreen(
    onBack: (() -> Unit)?,
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAboutDialog by remember { mutableStateOf(false) }

    SettingsScreenContent(
        uiState = uiState,
        onBack = onBack,
        onThemeSelected = viewModel::setTheme,
        onRetryRejected = viewModel::retryRejected,
        onDiscardRejected = viewModel::discardRejected,
        onAboutClick = { showAboutDialog = true },
    )

    if (showAboutDialog) {
        AboutDialog(onDismissRequest = { showAboutDialog = false })
    }
}

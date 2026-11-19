package com.f776.ecopan.feature.settings.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.ecopan.feature.settings.screens.settings.components.AboutDialog
import com.f776.ecopan.feature.settings.screens.settings.devices.DevicesApplyBar
import com.f776.ecopan.feature.settings.screens.settings.devices.DevicesViewModel
import com.f776.ecopan.feature.settings.screens.settings.devices.OtherDevicesCards
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal actual fun SettingsScreen(
    onBack: (() -> Unit)?,
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val devicesViewModel: DevicesViewModel = koinViewModel()
    val devices by devicesViewModel.uiState.collectAsStateWithLifecycle()
    var showAboutDialog by remember { mutableStateOf(false) }

    SettingsScreenContent(
        uiState = uiState,
        onBack = onBack,
        onThemeSelected = viewModel::setTheme,
        onRetryRejected = viewModel::retryRejected,
        onDiscardRejected = viewModel::discardRejected,
        onAboutClick = { showAboutDialog = true },
        devicesScale = { ScaleCard(state = devices, onAction = devicesViewModel::onAction) },
        devicesOther = { OtherDevicesCards(state = devices, onAction = devicesViewModel::onAction) },
        bottomBar = { DevicesApplyBar(state = devices, onAction = devicesViewModel::onAction) },
        showDevicesSection = true,
    )

    if (showAboutDialog) {
        AboutDialog(onDismissRequest = { showAboutDialog = false })
    }
}

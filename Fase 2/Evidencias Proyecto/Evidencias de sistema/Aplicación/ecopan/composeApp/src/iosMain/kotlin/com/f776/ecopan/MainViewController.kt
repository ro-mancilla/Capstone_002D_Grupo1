@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.f776.ecopan

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

fun MainViewController() = ComposeUIViewController {
    val appViewModel = koinViewModel<AppViewModel>()
    val theme by appViewModel.theme.collectAsStateWithLifecycle()
    val useDynamicColors by appViewModel.useDynamicColors.collectAsStateWithLifecycle()

    App(
        theme = theme,
        dynamicColor = useDynamicColors,
    )
}

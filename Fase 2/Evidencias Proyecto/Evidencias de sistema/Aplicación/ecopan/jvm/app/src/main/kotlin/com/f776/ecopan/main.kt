@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.f776.ecopan

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.ecopan.di.initKoin
import ecopan.jvmapp.generated.resources.icon
import ecopan.jvmapp.generated.resources.app_name
import ecopan.jvmapp.generated.resources.Res
import java.awt.Dimension
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

fun main() = application {
    initKoin()
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        icon = painterResource(resource = Res.drawable.icon),
        // Landscape by default: the POS screen is a two-column layout (product grid + cart) and
        // needs the width far more than the height. Portrait 600dp gave the grid two tiles per row.
        state = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    ) {
        // Below ~1024dp the weigh panel row and the cart's charge button start clipping; a till has
        // no reason to run that small, so clamp instead of trying to adapt to sizes nobody wants.
        LaunchedEffect(Unit) { window.minimumSize = Dimension(1024, 720) }

        val appViewModel = koinViewModel<AppViewModel>()
        val theme by appViewModel.theme.collectAsStateWithLifecycle()
        val useDynamicColors by appViewModel.useDynamicColors.collectAsStateWithLifecycle()

        App(
            theme = theme,
            dynamicColor = useDynamicColors,
        )
    }
}

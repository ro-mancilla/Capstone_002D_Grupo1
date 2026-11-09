package com.f776.ecopan

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.f776.core.ui.appstate.LocalSnackbarAppState
import com.f776.core.ui.locale.LocalClockFormat
import org.koin.compose.koinInject
import com.f776.core.ui.appstate.rememberSnackbarAppState
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.ecopan.domain.preferences.AppTheme
import com.f776.ecopan.feature.auth.screens.auth.AuthGate
import com.f776.ecopan.navigation.AppNavigation
import com.f776.ecopan.navigation.DefaultRoute

@Composable
fun App(
    theme: AppTheme,
    dynamicColor: Boolean = false,
    // Not PosRoute: on a phone there is no POS destination to land on. Each platform's first tab
    // is its own landing screen -- Caja on the till, Producción on the baker's phone.
    initialRoute: NavKey = DefaultRoute
) {
    val isDarkTheme = when (theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    // Configured explicitly rather than left to Coil's defaults, for one reason that matters on
    // this product: `crossfade(false)`. Coil's own fade animates the *painter*, which means a tile
    // briefly draws a half-transparent photo over its ground; `ProductImage` crossfades between
    // the placeholder and the decoded image instead, so a till tile never shows a ghost of a
    // loaf. The ktor3 fetcher is registered here too so network URLs work identically on all
    // three targets rather than depending on what the platform's service loader happens to find.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(false)
            .build()
    }

    EcopanTheme(dynamicColor = dynamicColor, darkTheme = isDarkTheme) {
        val snackbarAppState = rememberSnackbarAppState()

        // The snackbar host lives inside AppNavigation, within the navigation suite's content
        // slot. Hosted out here it sat at the window bottom and covered the navigation bar.
        CompositionLocalProvider(
            LocalSnackbarAppState provides snackbarAppState,
            // The injected one knows about Android's system-wide 24-hour override; the default
            // behind this local only knows the locale.
            LocalClockFormat provides koinInject(),
        ) {
            AuthGate(modifier = Modifier.fillMaxSize()) {
                AppNavigation(
                    initialRoute = initialRoute,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
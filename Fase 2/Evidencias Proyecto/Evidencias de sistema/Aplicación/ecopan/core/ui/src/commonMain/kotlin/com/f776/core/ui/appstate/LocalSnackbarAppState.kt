package com.f776.core.ui.appstate

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for accessing SnackbarAppState throughout the app.
 * Throws an error if accessed outside the provider.
 */
val LocalSnackbarAppState = compositionLocalOf<SnackbarAppState> {
    error("No SnackbarAppState provided. Make sure to wrap your composable with CompositionLocalProvider.")
}


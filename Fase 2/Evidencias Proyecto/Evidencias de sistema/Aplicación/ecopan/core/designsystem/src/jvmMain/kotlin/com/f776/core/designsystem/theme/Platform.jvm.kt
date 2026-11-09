package com.f776.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun dynamicEcopanColorScheme(darkTheme: Boolean): ColorScheme? = null

// Desktop exposes no reduced-motion preference to the JVM.
@Composable
internal actual fun rememberReducedMotion(): Boolean = false

@Composable
internal actual fun SystemBarIcons(darkTheme: Boolean) = Unit

package com.f776.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
internal actual fun dynamicEcopanColorScheme(darkTheme: Boolean): ColorScheme? = null

@Composable
internal actual fun rememberReducedMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }

// The status bar style on iOS is the view controller's, set where it is made.
@Composable
internal actual fun SystemBarIcons(darkTheme: Boolean) = Unit

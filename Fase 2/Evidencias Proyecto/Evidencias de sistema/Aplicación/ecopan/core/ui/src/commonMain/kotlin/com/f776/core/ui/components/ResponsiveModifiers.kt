@file:OptIn(ExperimentalMaterial3Api::class)

package com.f776.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass


val WindowSizeClass.isCompact: Boolean
    get() = !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isMedium: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
            !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

val WindowSizeClass.isExpanded: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/**
 * [responsiveWidth] against the current window — the overload screens actually call, so feature
 * modules don't each need a dependency on the adaptive artifact just to read the size class.
 */
@Composable
fun Modifier.responsiveWidth(): Modifier = responsiveWidth(currentWindowAdaptiveInfo().windowSizeClass)

/**
 * Apply responsive width constraints following the adaptive onboarding guidelines.
 *
 * - Compact: fillMaxWidth
 * - Medium/Expanded: widthIn(max = 480.dp) + fillMaxWidth
 */
fun Modifier.responsiveWidth(windowSizeClass: WindowSizeClass): Modifier {
    return if (windowSizeClass.isCompact) {
        this.fillMaxWidth()
    } else {
        this.widthIn(max = 480.dp).fillMaxWidth()
    }
}

/**
 * Apply responsive button width following the adaptive onboarding guidelines.
 *
 * - Compact: fillMaxWidth
 * - Medium: wrapContentWidth aligned to end, min width 200dp, max 320dp
 * - Expanded: wrapContentWidth aligned to end, min width 200dp, max 320dp
 */
fun Modifier.responsiveButtonWidth(windowSizeClass: WindowSizeClass): Modifier {
    return if (windowSizeClass.isCompact) {
        this.fillMaxWidth()
    } else {
        this.widthIn(min = 200.dp, max = 320.dp)
    }
}



@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package com.f776.core.ui.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable

@Composable
actual fun <T> ThreePaneScaffoldPredictiveBackHandler(
    navigator: ThreePaneScaffoldNavigator<T>,
    defaultBackBehavior: BackNavigationBehavior
) = androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldPredictiveBackHandler(
    navigator = navigator,
    backBehavior = defaultBackBehavior
)
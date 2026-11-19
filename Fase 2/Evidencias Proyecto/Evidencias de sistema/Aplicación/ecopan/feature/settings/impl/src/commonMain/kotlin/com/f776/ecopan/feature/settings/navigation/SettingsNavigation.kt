@file:OptIn(KoinExperimentalAPI::class)

package com.f776.ecopan.feature.settings.navigation

import com.f776.ecopan.feature.settings.route.SettingsRoute
import com.f776.ecopan.feature.settings.screens.settings.SettingsScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

val SettingsNavigationModule = module {
    // A navigation-suite tab, so it is a root: nothing to go back to.
    navigation<SettingsRoute> {
        SettingsScreen(onBack = null)
    }
}

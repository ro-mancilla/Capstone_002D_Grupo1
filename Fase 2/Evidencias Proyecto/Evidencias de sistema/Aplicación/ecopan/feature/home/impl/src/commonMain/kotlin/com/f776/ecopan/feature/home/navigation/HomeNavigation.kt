package com.f776.ecopan.feature.home.navigation

import com.f776.ecopan.feature.home.route.HomeRoute
import com.f776.ecopan.feature.home.screens.home.HomeScreenRoot
import com.f776.ecopan.core.navigation.navigation
import org.koin.dsl.module

val HomeNavigationModule = module {
    navigation<HomeRoute> {
        HomeScreenRoot()
    }
}

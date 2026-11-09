package com.f776.ecopan.feature.pos.navigation

import com.f776.ecopan.core.navigation.navigation
import com.f776.ecopan.feature.pos.route.PosRoute
import com.f776.ecopan.feature.pos.screens.pos.PosScreenRoot
import org.koin.dsl.module

val PosNavigationModule = module {
    navigation<PosRoute> {
        PosScreenRoot()
    }
}

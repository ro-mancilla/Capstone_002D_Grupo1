package com.f776.ecopan.feature.production.navigation

import com.f776.ecopan.core.navigation.Navigator
import com.f776.ecopan.core.navigation.SLIDE_HORIZONTALLY
import com.f776.ecopan.core.navigation.navigation
import com.f776.ecopan.feature.production.route.ProductionRoute
import com.f776.ecopan.feature.production.route.StockGlanceRoute
import com.f776.ecopan.feature.production.screens.batchentry.BatchEntryScreenRoot
import com.f776.ecopan.feature.production.screens.stockglance.StockGlanceScreenRoot
import org.koin.compose.koinInject
import org.koin.dsl.module

val ProductionNavigationModule = module {
    navigation<ProductionRoute> {
        val navigator = koinInject<Navigator>()
        BatchEntryScreenRoot(onOpenStockGlance = { navigator.goTo(StockGlanceRoute) })
    }
    navigation<StockGlanceRoute>(metadata = SLIDE_HORIZONTALLY) {
        val navigator = koinInject<Navigator>()
        StockGlanceScreenRoot(onBack = navigator::goBack)
    }
}

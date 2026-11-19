package com.f776.ecopan.feature.production.route

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass

/** Top-level tab — batch entry + today's history (06 §7.1/§7.2). */
@Serializable
data object ProductionRoute : NavKey

/** Pushed detail, entitlement-gated to `INVENTORY_FORMULAS` (06 §7.3). */
@Serializable
data object StockGlanceRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.productionRoutes() {
    subclass(ProductionRoute.serializer())
    subclass(StockGlanceRoute.serializer())
}

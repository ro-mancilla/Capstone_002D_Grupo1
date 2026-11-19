package com.f776.ecopan.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Settings
import androidx.navigation3.runtime.NavKey
import com.f776.ecopan.feature.production.route.ProductionRoute
import com.f776.ecopan.feature.production.route.productionRoutes
import com.f776.ecopan.feature.settings.route.SettingsRoute
import com.f776.ecopan.feature.settings.route.settingsRoutes
import ecopan.composeapp.generated.resources.Res
import ecopan.composeapp.generated.resources.nav_production_label
import ecopan.composeapp.generated.resources.nav_settings_label
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/**
 * The baker client (SPECS.md Client 2): log a batch, glance at stock, nothing else. Shared by
 * Android and iOS, which are the same product on two operating systems.
 *
 * `StockGlanceRoute` is deliberately not a tab -- it is a detail pushed off Producción (06 §1), so
 * it is registered in [platformRoutes] but owns no back-stack segment of its own.
 */
internal actual val TopLevelDestinations: List<TopLevelDestination> = listOf(
    TopLevelDestination(ProductionRoute, Icons.Default.BakeryDining, Res.string.nav_production_label),
    TopLevelDestination(SettingsRoute, Icons.Default.Settings, Res.string.nav_settings_label),
)

internal actual fun PolymorphicModuleBuilder<NavKey>.platformRoutes() {
    productionRoutes()
    settingsRoutes()
}

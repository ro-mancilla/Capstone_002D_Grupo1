package com.f776.ecopan.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.navigation3.runtime.NavKey
import com.f776.ecopan.feature.pos.route.PosRoute
import com.f776.ecopan.feature.pos.route.posRoutes
import com.f776.ecopan.feature.settings.route.SettingsRoute
import com.f776.ecopan.feature.settings.route.settingsRoutes
import ecopan.composeapp.generated.resources.Res
import ecopan.composeapp.generated.resources.nav_pos_label
import ecopan.composeapp.generated.resources.nav_settings_label
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/**
 * The desktop till (SPECS.md Client 1): sell, and configure the machine that sells.
 *
 * No Producción tab — a till does not mix dough. It still *hears* about batches: the batch-ready
 * banner arrives over SSE (06 §6) and is part of the POS screen, not a destination.
 */
internal actual val TopLevelDestinations: List<TopLevelDestination> = listOf(
    TopLevelDestination(PosRoute, Icons.Default.PointOfSale, Res.string.nav_pos_label),
    TopLevelDestination(SettingsRoute, Icons.Default.Settings, Res.string.nav_settings_label),
)

internal actual fun PolymorphicModuleBuilder<NavKey>.platformRoutes() {
    posRoutes()
    settingsRoutes()
}

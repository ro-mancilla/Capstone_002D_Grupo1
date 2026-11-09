package com.f776.ecopan.feature.settings.route

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass

@Serializable
data object SettingsRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.settingsRoutes() {
    subclass(SettingsRoute.serializer())
}

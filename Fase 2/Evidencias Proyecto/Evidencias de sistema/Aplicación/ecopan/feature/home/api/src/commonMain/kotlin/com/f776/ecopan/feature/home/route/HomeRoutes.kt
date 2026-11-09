package com.f776.ecopan.feature.home.route

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass

@Serializable
data object HomeRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.homeRoutes() {
    subclass(HomeRoute.serializer())
}
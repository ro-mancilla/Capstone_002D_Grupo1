package com.f776.ecopan.feature.pos.route

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass

@Serializable
data object PosRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.posRoutes() {
    subclass(PosRoute.serializer())
}

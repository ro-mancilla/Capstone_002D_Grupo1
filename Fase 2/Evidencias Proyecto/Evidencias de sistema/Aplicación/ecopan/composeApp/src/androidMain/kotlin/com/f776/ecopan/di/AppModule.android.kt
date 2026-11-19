package com.f776.ecopan.di

import dev.icerock.moko.permissions.PermissionsController
import org.koin.core.module.Module
import org.koin.dsl.module

actual val PlatformModule: Module = module {
    includes(MobileModule)

    single { PermissionsController(applicationContext = get()) }
}

package com.f776.shareable.di

import org.koin.core.module.Module
import org.koin.dsl.module

val ImageSharerModule = module {
    includes(PlatformModule)
}

internal expect val PlatformModule: Module
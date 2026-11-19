package com.f776.locale.di

import org.koin.core.module.Module
import org.koin.dsl.module

val LocaleModule = module {
    includes(PlatformLocaleModule)
}

internal expect val PlatformLocaleModule: Module

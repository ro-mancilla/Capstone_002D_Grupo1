package com.f776.ecopan.core.datastore.di

import org.koin.core.module.Module
import org.koin.dsl.module

val DatastoreModule = module {
    includes(PlatformDatastoreModule)
}

internal expect val PlatformDatastoreModule: Module

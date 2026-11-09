package com.f776.ecopan.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.f776.ecopan.core.datastore.createDataStore
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal actual val PlatformDatastoreModule =
    module { single<DataStore<Preferences>>(qualifier = named("datastore")) { createDataStore() } }
package com.f776.ecopan.data.preferences.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.f776.ecopan.core.datastore.di.DatastoreModule
import com.f776.ecopan.data.preferences.ReminderPreferencesDataSourceImpl
import com.f776.ecopan.data.preferences.ThemePreferencesDataSourceImpl
import com.f776.ecopan.domain.preferences.ReminderPreferencesDataSource
import com.f776.ecopan.domain.preferences.ThemePreferencesDataSource
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val PreferencesModule = module {
    includes(DatastoreModule)

    factory {
        ReminderPreferencesDataSourceImpl(
            dataStore = get<DataStore<Preferences>>(named("datastore"))
        )
    }.bind<ReminderPreferencesDataSource>()

    factory {
        ThemePreferencesDataSourceImpl(
            dataStore = get<DataStore<Preferences>>(named("datastore"))
        )
    }.bind<ThemePreferencesDataSource>()
}

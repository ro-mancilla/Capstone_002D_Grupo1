package com.f776.ecopan.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.f776.ecopan.domain.preferences.ReminderPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException

internal class ReminderPreferencesDataSourceImpl(
    private val dataStore: DataStore<Preferences>
) : ReminderPreferencesDataSource {
    override val isReminderEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences ->
            preferences[REMINDER_ENABLED_SETTINGS_KEY] ?: true
        }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit {
            it[REMINDER_ENABLED_SETTINGS_KEY] = enabled
        }
    }

    private companion object {
        val REMINDER_ENABLED_SETTINGS_KEY = booleanPreferencesKey("reminder_enabled_settings")
    }
}
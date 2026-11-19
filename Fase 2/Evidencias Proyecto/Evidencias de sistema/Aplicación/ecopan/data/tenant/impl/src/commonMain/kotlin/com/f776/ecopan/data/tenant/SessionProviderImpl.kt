package com.f776.ecopan.data.tenant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.f776.ecopan.domain.tenant.SessionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import kotlin.uuid.Uuid

internal class SessionProviderImpl(
    private val dataStore: DataStore<Preferences>,
) : SessionProvider {

    override val currentUserId: Flow<Uuid?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[CURRENT_USER_ID_KEY]?.let(Uuid::parse) }

    override suspend fun unlock(userId: Uuid) {
        dataStore.edit { preferences -> preferences[CURRENT_USER_ID_KEY] = userId.toString() }
    }

    override suspend fun lock() {
        dataStore.edit { preferences -> preferences.remove(CURRENT_USER_ID_KEY) }
    }

    private companion object {
        val CURRENT_USER_ID_KEY = stringPreferencesKey("session_current_user_id")
    }
}

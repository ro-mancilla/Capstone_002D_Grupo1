package com.f776.ecopan.data.tenant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.f776.ecopan.domain.tenant.DeviceSessionStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * DataStore-backed [DeviceSessionStore] (01 §4 device session, 03 §3.2/§8). Follows the
 * established `named("datastore")` + `catch { if (it is IOException) emit(emptyPreferences()) }`
 * idiom used by `:data:sync:impl`'s `CursorStore`/`DeviceIdProvider`.
 *
 * [deviceId] deliberately persists under the *same* preference key
 * (`"sync_device_id"`) that `:data:sync:impl`'s `DeviceIdProvider` uses, rather than depending on
 * that internal class directly (`:data:tenant:impl` -> `:data:sync:impl` would be a layering
 * violation the other direction from 03 §1.2's documented dependency, even though it wouldn't
 * form a Gradle cycle) or minting a second, different device-install id. Both classes read/write
 * the one shared `named("datastore")` `DataStore<Preferences>` instance, so whichever of the two
 * runs first generates the id and the other observes the same value afterwards.
 */
internal class TokenStore(
    private val dataStore: DataStore<Preferences>,
) : DeviceSessionStore {

    override suspend fun currentAccessToken(): String? = preferences()[ACCESS_TOKEN_KEY]
    override suspend fun currentRefreshToken(): String? = preferences()[REFRESH_TOKEN_KEY]

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
    }

    override suspend fun deviceName(): String? = preferences()[DEVICE_NAME_KEY]?.takeIf { it.isNotBlank() }

    override suspend fun saveDeviceName(name: String) {
        dataStore.edit { it[DEVICE_NAME_KEY] = name.trim() }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
        }
    }

    /** The per-install id every login/push envelope carries (03 §3.2) -- see class KDoc. */
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun deviceId(): String {
        val existing = preferences()[DEVICE_ID_KEY]
        if (existing != null) return existing

        val generated = Uuid.random().toString()
        dataStore.edit { it[DEVICE_ID_KEY] = generated }
        return generated
    }

    private suspend fun preferences(): Preferences = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it }
        .first()

    private companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("device_session_access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("device_session_refresh_token")
        val DEVICE_NAME_KEY = stringPreferencesKey("device_session_name")
        val DEVICE_ID_KEY = stringPreferencesKey("sync_device_id")
    }
}

package com.f776.ecopan.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * The `deviceId` every push envelope carries (03 §3.2, backend's `PushRequest.deviceId`) -- a
 * per-install identifier, generated once and persisted, *not* a per-session/per-login value (01
 * §4: the device session is what's long-lived; this just names which physical install is talking
 * to the server, independent of who's logged in or PIN-unlocked on it).
 */
internal class DeviceIdProvider(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun get(): String {
        val existing = dataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing

        val generated = Uuid.random().toString()
        dataStore.edit { it[DEVICE_ID_KEY] = generated }
        return generated
    }

    private companion object {
        val DEVICE_ID_KEY = stringPreferencesKey("sync_device_id")
    }
}

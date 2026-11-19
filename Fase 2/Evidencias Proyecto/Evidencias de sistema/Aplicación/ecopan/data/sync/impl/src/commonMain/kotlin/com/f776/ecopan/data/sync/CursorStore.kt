package com.f776.ecopan.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import kotlin.time.Instant

/**
 * The pull cursor (03 §4.1): `lastSeenSeq`, advanced only after a pull page is fully applied.
 * A single un-keyed value, not keyed by `(tenantId, branchId)` as 03's illustrative sketch
 * suggests — a device caches exactly one tenant at a time (01 §4, already the invariant
 * `:data:tenant` builds on), so there is never more than one cursor to track; re-bootstrap
 * ([reset]) clears it the same way `TenantRepository.clear()` clears the tenant cache.
 */
internal class CursorStore(
    private val dataStore: DataStore<Preferences>,
) {
    fun observe(): Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[LAST_SEEN_SEQ_KEY] ?: 0L }

    suspend fun advanceTo(serverSeq: Long) {
        dataStore.edit { it[LAST_SEEN_SEQ_KEY] = serverSeq }
    }

    suspend fun reset() {
        dataStore.edit { it[LAST_SEEN_SEQ_KEY] = 0L }
    }

    /**
     * Which entity types the client could apply when the cursor last moved.
     *
     * The cursor alone is not a safe resume point. `PullWorker` skips a pulled row whose
     * `entityType` has no bound applier -- it must, or an older client would stall forever on a
     * type a newer server introduced -- but it then advances the cursor *past* that row. The row is
     * never offered again, so the data is lost permanently rather than late.
     *
     * That is not hypothetical: PRODUCT/INGREDIENT/FORMULA/USER shipped with no
     * applier, every device drained the whole catalog into a skip counter, and afterwards a
     * correct client asking `since=<end>` was told, correctly, that there was nothing new.
     *
     * Recording the applier set makes that recoverable: when an app update teaches the client a
     * type it used to skip, [readAppliedTypes] no longer matches and the cursor is reset for a full
     * re-pull. Safe by construction -- config appliers are idempotent upserts and fact appliers are
     * insert-if-unseen (03 §4.3), which is exactly what makes re-pulling a page harmless.
     */
    suspend fun readAppliedTypes(): Set<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .first()[APPLIED_TYPES_KEY]
        ?: emptySet()

    suspend fun writeAppliedTypes(types: Set<String>) {
        dataStore.edit { it[APPLIED_TYPES_KEY] = types }
    }

    /**
     * Which round of cursor-repair this device has had applied, so a build can force a one-time
     * full re-pull on devices already holding a poisoned cursor.
     *
     * [readAppliedTypes] can only detect damage it *witnessed*. A device that drained the catalog
     * before that key existed stored no set at all, and the first build to write one recorded the
     * post-fix set against a cursor that had already walked past every row — leaving the two
     * permanently consistent and the device permanently empty, with no way to tell it apart from a
     * device that had legitimately applied everything.
     *
     * Bumping [PullWorker.PULL_HEAL_VERSION] is the escape hatch: it re-pulls once, unconditionally,
     * on every device that has not yet seen that version. Re-pulling is safe by construction (03
     * §4.3 idempotency), so the cost of a false positive is one redundant fetch.
     */
    suspend fun readHealVersion(): Int = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .first()[HEAL_VERSION_KEY]
        ?: 0

    suspend fun writeHealVersion(version: Int) {
        dataStore.edit { it[HEAL_VERSION_KEY] = version }
    }

    /**
     * When a pull last ran to completion, i.e. the last time this device heard from the server.
     * It is what the baker's "Datos de la última sincronización, hoy 04:50" line reads offline.
     * Deliberately untouched by [reset]: a re-bootstrap re-pulls, it does not un-hear the server.
     */
    fun observeLastPulledAt(): Flow<Instant?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[LAST_PULLED_AT_KEY]?.let(Instant::fromEpochMilliseconds) }

    suspend fun recordPulledAt(at: Instant) {
        dataStore.edit { it[LAST_PULLED_AT_KEY] = at.toEpochMilliseconds() }
    }

    private companion object {
        val LAST_PULLED_AT_KEY = longPreferencesKey("sync_last_pulled_at_ms")
        val LAST_SEEN_SEQ_KEY = longPreferencesKey("sync_last_seen_seq")
        val APPLIED_TYPES_KEY = stringSetPreferencesKey("sync_applied_entity_types")
        val HEAL_VERSION_KEY = intPreferencesKey("sync_pull_heal_version")
    }
}

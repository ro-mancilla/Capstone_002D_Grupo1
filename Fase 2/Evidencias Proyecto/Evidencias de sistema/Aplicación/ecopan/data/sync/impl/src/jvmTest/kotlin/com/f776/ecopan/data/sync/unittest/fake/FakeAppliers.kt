package com.f776.ecopan.data.sync.unittest.fake

import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.unigrid.core.database.dao.NoteDao
import com.f776.unigrid.core.database.entity.NoteEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

@Serializable
internal data class FakeRowPayload(val id: String, val title: String)

/**
 * Stands in for a real config applier (e.g. `BranchSyncApplier`) for T9/T10 -- `:data:sync:impl`
 * can't depend on `:data:tenant:impl`/`:data:inventory:impl` (that's the whole point of `SyncApplier`
 * living in `:data:sync:api`, 03 §4.3), so these tests exercise the *dispatch/family* mechanism
 * generically against [NoteEntity] (this template's reference domain, CLAUDE.md), the same stand-in
 * `OutboxAtomicityTest` already uses for "a domain write". Always overwrites by id (LWW, 03 §5).
 */
internal class FakeConfigApplier(
    private val noteDao: NoteDao,
    override val entityType: SyncEntityType = SyncEntityType.PRODUCT,
) : SyncApplier {
    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val row = Json.decodeFromJsonElement<FakeRowPayload>(payload)
        noteDao.upsert(note(row.id, row.title))
    }
}

/** Stands in for a real fact applier (e.g. `StockMovementSyncApplier`) -- insert-if-unseen, never overwrites. */
internal class FakeFactApplier(
    private val noteDao: NoteDao,
    override val entityType: SyncEntityType = SyncEntityType.STOCK_MOVEMENT,
) : SyncApplier {
    private val seen = mutableSetOf<String>()

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val row = Json.decodeFromJsonElement<FakeRowPayload>(payload)
        if (!seen.add(row.id)) return // already applied once -- ignore, mirrors `INSERT OR IGNORE`
        noteDao.upsert(note(row.id, row.title))
    }
}

private fun note(id: String, title: String) = NoteEntity(
    id = Uuid.parse(id),
    title = title,
    body = "",
    createdAt = LocalDate(2026, 1, 1),
    updatedAt = LocalDate(2026, 1, 1),
)

package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Outbox row lifecycle (03 §2) — purely local bookkeeping, never sent on the wire. */
enum class OutboxState {
    PENDING, IN_FLIGHT, ACKED, FAILED,

    /**
     * A `FAILED` row a person chose to give up on. Kept rather than deleted so the discard stays on
     * record (HU-SYN-0006); `takeBatch` and every counter ignore it, so it never sends or alarms.
     */
    DISCARDED,
}

/**
 * The sync outbox (03 §2). [entityType]/[opType] are stored as plain strings, not typed enums —
 * their real enum definitions (`SyncEntityType`/`SyncOpType`) live in `:data:sync:api`, and
 * `:core:database` must never depend on a data-layer module (dependency direction is
 * feature→data→core). `:data:sync:impl` maps `SyncEntityType.name`/`SyncOpType.name` at the edge.
 */
@Entity(
    tableName = "outbox",
    indices = [Index(value = ["state"]), Index(value = ["mutationId"], unique = true)],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val mutationId: Uuid,
    val entityType: String,
    val opType: String,
    val payload: String,
    val createdAt: Instant,
    val state: OutboxState,
    val attempts: Int = 0,
    val lastError: String? = null,
)

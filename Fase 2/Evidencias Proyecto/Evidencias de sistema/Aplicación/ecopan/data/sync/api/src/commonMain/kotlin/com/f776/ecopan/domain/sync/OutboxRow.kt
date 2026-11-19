package com.f776.ecopan.domain.sync

import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Mints the outbox row for one offline mutation (03 §2.1).
 *
 * Shaped as a *value*, not a writer, because of how the atomic write actually works: Room-KMP's
 * `@Transaction` default-body pattern only joins abstract methods on the same DAO, so a domain
 * write is `dao.logBatch(entity, movements, outboxRows)` — it takes rows, not a collaborator to
 * call. The `OutboxWriter` interface this replaces was shaped for a call pattern the storage
 * engine cannot support, and had no production callers: both real writers built the row by hand.
 *
 * What it owns is the row's invariants, which were previously restated at each write site:
 * - [OutboxState.PENDING] is the only state a row may start in. A row minted `IN_FLIGHT` would be
 *   invisible to `takeBatch` forever, which is the failure `SyncEngineImpl`'s crash recovery
 *   exists to undo.
 * - `createdAt` comes from the clock, and orders the push queue.
 * - The stored spellings are `SyncEntityType.name`/`SyncOpType.name`, which `PushWorker` parses
 *   back. `OutboxEntity` deliberately stores them as `String` (core must not depend on data), so
 *   this is the one place the mapping happens.
 *
 * Call it inside the same transaction that writes the domain row — a batch can never exist
 * locally without its outbox entry, or vice versa (06 §4).
 */
fun outboxRow(
    mutationId: Uuid,
    entityType: SyncEntityType,
    opType: SyncOpType,
    payloadJson: String,
    createdAt: Instant = Clock.System.now(),
): OutboxEntity = OutboxEntity(
    mutationId = mutationId,
    entityType = entityType.name,
    opType = opType.name,
    payload = payloadJson,
    createdAt = createdAt,
    state = OutboxState.PENDING,
)

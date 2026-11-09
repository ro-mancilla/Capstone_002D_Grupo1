package com.f776.ecopan.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A mutation the server permanently refused (`REJECTED`, 03 §3.3), as a person needs to see it:
 * what kind of record it was, when the device made it, how many times it was sent, and the reason
 * the server gave. The payload is deliberately not exposed -- this is for deciding, not editing.
 */
data class RejectedOperation(
    val mutationId: Uuid,
    /** Null when the stored name is not a known [SyncEntityType] (a row from a newer build). */
    val entityType: SyncEntityType?,
    val createdAt: Instant,
    val attempts: Int,
    /** The server's error code, e.g. `BRANCH_FORBIDDEN`; null when none was recorded. */
    val reason: String?,
)

/**
 * Review of rejected operations (HU-SYN-0006). The queue never retries a rejected row by itself:
 * the server already said no, and re-sending would repeat the argument. A person decides instead,
 * either to try again (after fixing whatever the server objected to) or to drop the operation.
 *
 * Implemented by `:data:sync:impl`, bound in `SyncDataModule`.
 */
interface RejectedOperations {
    /** Oldest first, matching the order the device made them. Empty when nothing is rejected. */
    val rejected: Flow<List<RejectedOperation>>

    /** Returns the operation to the push queue. No effect if it is no longer rejected. */
    suspend fun retry(mutationId: Uuid)

    /** Gives up on the operation. It stays in the outbox as a record but is never sent again. */
    suspend fun discard(mutationId: Uuid)
}

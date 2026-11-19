package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface OutboxDao {
    /**
     * Ignored on conflict — [OutboxEntity.mutationId] is unique, so a caller re-enqueuing the same
     * mutation (e.g. after a crash mid-transaction, before this insert's transaction committed) is
     * a harmless no-op rather than a constraint violation (01 §6.3 idempotency, mirrored locally).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: OutboxEntity)

    @Query("SELECT COUNT(*) FROM outbox WHERE state = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    /**
     * Pending rows as a person would count them: what they *registered*, not what the sync
     * protocol needs to send. One batch is a `PRODUCTION_BATCH` row plus one `STOCK_MOVEMENT` row
     * per formula ingredient it decremented (those carry the batch's id as `sourceBatchId`), so
     * counting raw rows told a baker that a single batch was "5 pendientes". Derived movements are
     * excluded; a manual adjustment (`sourceBatchId` null) is its own operation and still counts.
     */
    @Query(
        """
        SELECT COUNT(*) FROM outbox WHERE state = 'PENDING'
        AND NOT (entityType = 'STOCK_MOVEMENT' AND payload LIKE '%"sourceBatchId":"%')
        """,
    )
    fun observePendingRegisteredCount(): Flow<Int>

    /** Per-client FIFO by rowid (03 §3.1) — never reordered. */
    @Query("SELECT * FROM outbox WHERE state = 'PENDING' ORDER BY rowid ASC LIMIT :limit")
    suspend fun takeBatch(limit: Int): List<OutboxEntity>

    @Query("UPDATE outbox SET state = 'IN_FLIGHT' WHERE mutationId IN (:mutationIds)")
    suspend fun markInFlight(mutationIds: List<Uuid>)

    /** Acked rows are deleted, not archived (03 §2) — the domain row is the durable record. */
    @Query("DELETE FROM outbox WHERE mutationId IN (:mutationIds)")
    suspend fun deleteAcked(mutationIds: List<Uuid>)

    @Query("UPDATE outbox SET state = 'PENDING' WHERE mutationId IN (:mutationIds)")
    suspend fun revertToPending(mutationIds: List<Uuid>)

    /** Crash recovery (03 §2/T7): any row still IN_FLIGHT at engine startup was never acked. */
    @Query("UPDATE outbox SET state = 'PENDING' WHERE state = 'IN_FLIGHT'")
    suspend fun revertAllInFlightToPending()

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE mutationId IN (:mutationIds)")
    suspend fun incrementAttempts(mutationIds: List<Uuid>)

    @Query("UPDATE outbox SET state = 'FAILED', lastError = :error WHERE mutationId = :mutationId")
    suspend fun markFailed(mutationId: Uuid, error: String)

    @Query("SELECT * FROM outbox WHERE state = 'FAILED' ORDER BY rowid ASC")
    fun observeFailed(): Flow<List<OutboxEntity>>

    /**
     * How many mutations the server has *permanently refused*.
     *
     * `FAILED` is written by `PushWorker` when a result comes back `REJECTED`, and nothing ever
     * moves a row out of it again: [takeBatch] only reads `PENDING`, so a rejected mutation is
     * never retried. That is the correct policy — the server has already said no, and re-sending
     * would only repeat the argument — but it is only safe if somebody is *told*. Until this
     * existed, [observeFailed] had no production caller at all and a rejected sale simply left the
     * queue in silence.
     */
    @Query("SELECT COUNT(*) FROM outbox WHERE state = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    /** Companion to [observePendingMutationIds] for the rows the server rejected. */
    @Query("SELECT mutationId FROM outbox WHERE state = 'FAILED'")
    fun observeFailedMutationIds(): Flow<List<Uuid>>

    /**
     * Puts a rejected row back in the queue for another attempt. Guarded on `FAILED` so a row that
     * is already moving (or was already discarded) is left alone; returns how many rows changed.
     */
    @Query("UPDATE outbox SET state = 'PENDING', lastError = NULL WHERE mutationId = :mutationId AND state = 'FAILED'")
    suspend fun requeueFailed(mutationId: Uuid): Int

    /** Gives up on a rejected row but keeps it as a record: `DISCARDED` is never sent or counted. */
    @Query("UPDATE outbox SET state = 'DISCARDED' WHERE mutationId = :mutationId AND state = 'FAILED'")
    suspend fun discardFailed(mutationId: Uuid): Int

    @Query("SELECT state FROM outbox WHERE mutationId = :mutationId")
    suspend fun stateOf(mutationId: Uuid): OutboxState?

    /**
     * Read-model join seam (06 §6): callers derive a per-row sync status (e.g.
     * `ProductionBatch.syncState`) by checking whether a domain row's own id is still in this set,
     * rather than storing a redundant status column that could drift out of sync with the outbox.
     *
     * `FAILED` is **not** in this set, and a caller that joins on this alone will show a rejected
     * row as synced — which is the opposite of the truth. Pair it with [observeFailedMutationIds].
     */
    @Query("SELECT mutationId FROM outbox WHERE state IN ('PENDING', 'IN_FLIGHT')")
    fun observePendingMutationIds(): Flow<List<Uuid>>

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.repository.model.DecrementMovement
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ProductionBatchRepository {
    /**
     * Append-only fact, idempotent by [id] (02 §4.1). `decrementStatus` starts `PENDING`. An
     * `Applied` insert also enqueues its `ProductionBatchMessage` on the outbox in the same
     * transaction (#142); a `Duplicate` enqueues nothing.
     */
    suspend fun create(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid,
        bakerId: Uuid,
        productId: Uuid,
        mixWeightGrams: Long,
        batchAt: Instant,
        movementsMinted: Boolean,
        deviceId: String
    ): IngestResult<ProductionBatch>

    suspend fun findById(id: Uuid): ProductionBatch?

    suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<ProductionBatch>

    /** Snapshot-on-connect feed for `GET /events/sse` (02 §5): batches received in the last 24h. */
    suspend fun listReceivedSince(tenantId: Uuid, since: Instant): List<ProductionBatch>

    /** Sets the status unconditionally. Tests and tooling only: the worker uses [completeDecrement]. */
    suspend fun updateDecrementStatus(id: Uuid, status: DecrementStatus): ProductionBatch?

    /**
     * The worker's `PENDING -> APPLIED|SKIPPED|FAILED` (02 §6), all-or-nothing (#143): the batch's
     * [movements] (booked at its own `batchAt`, #137), the status change and its `BatchProcessed`
     * notice on the outbox commit in one transaction. The status changes only from `PENDING`, so a
     * batch already completed -- by an earlier delivery, or applied before a failure -- is never
     * overwritten: that call writes nothing and returns `false`.
     */
    suspend fun completeDecrement(id: Uuid, status: DecrementStatus, movements: List<DecrementMovement>): Boolean
}

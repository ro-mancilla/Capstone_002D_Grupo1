package com.f776.ecopan.data.sync.rejected

import com.f776.ecopan.domain.sync.RejectedOperation
import com.f776.ecopan.domain.sync.RejectedOperations
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.unigrid.core.database.dao.OutboxDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class RejectedOperationsImpl(private val outboxDao: OutboxDao) : RejectedOperations {
    override val rejected: Flow<List<RejectedOperation>> = outboxDao.observeFailed().map { rows ->
        rows.map { row ->
            RejectedOperation(
                mutationId = row.mutationId,
                entityType = SyncEntityType.entries.firstOrNull { it.name == row.entityType },
                createdAt = row.createdAt,
                attempts = row.attempts,
                reason = row.lastError,
            )
        }
    }

    // A requeued row is PENDING again, so the scheduler's pending-count watch picks it up and
    // sends it on the next cycle; nothing here needs to nudge the engine.
    override suspend fun retry(mutationId: Uuid) {
        outboxDao.requeueFailed(mutationId)
    }

    override suspend fun discard(mutationId: Uuid) {
        outboxDao.discardFailed(mutationId)
    }
}

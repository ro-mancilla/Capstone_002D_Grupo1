package com.f776.ecopan.data.production.unittest.fake

import com.f776.unigrid.core.database.dao.ProductionBatchDao
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.ProductionBatchEntity
import com.f776.unigrid.core.database.entity.StockMovementEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Takes the same [FakeOutboxDao] instance the test also injects into the repository as its own
 * `OutboxDao` dependency, so both fakes share one backing store — mirroring how, in real Room,
 * `ProductionBatchDao` and `OutboxDao` are two Dao interfaces over the same physical `outbox`
 * table rather than two independent stores.
 */
internal class FakeProductionBatchDao(private val outboxDao: FakeOutboxDao) : ProductionBatchDao {
    private val batches = MutableStateFlow<Map<Uuid, ProductionBatchEntity>>(emptyMap())
    val movements = mutableListOf<StockMovementEntity>()

    override suspend fun insertBatch(batch: ProductionBatchEntity) {
        batches.value = batches.value + (batch.id to batch)
    }

    override suspend fun insertIfUnseen(batch: ProductionBatchEntity) {
        if (batch.id in batches.value) return // OnConflictStrategy.IGNORE, mirrored
        batches.value = batches.value + (batch.id to batch)
    }

    override suspend fun exists(id: Uuid): Boolean = id in batches.value

    override suspend fun insertMovements(movements: List<StockMovementEntity>) {
        this.movements += movements
    }

    override suspend fun insertOutboxRows(rows: List<OutboxEntity>) {
        rows.forEach { outboxDao.insert(it) }
    }

    override fun observeTodaysBatches(branchId: Uuid, startMillis: Long, endMillis: Long) =
        batches.map { byId ->
            byId.values.filter {
                it.branchId == branchId &&
                    it.loggedAt.toEpochMilliseconds() >= startMillis &&
                    it.loggedAt.toEpochMilliseconds() < endMillis
            }.sortedByDescending { it.loggedAt }
        }

    override suspend fun clear() {
        batches.value = emptyMap()
    }
}

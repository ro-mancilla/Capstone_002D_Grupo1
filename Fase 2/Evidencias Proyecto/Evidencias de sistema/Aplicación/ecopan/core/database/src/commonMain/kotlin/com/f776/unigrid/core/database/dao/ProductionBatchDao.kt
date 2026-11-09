package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.ProductionBatchEntity
import com.f776.unigrid.core.database.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ProductionBatchDao {

    @Insert
    suspend fun insertBatch(batch: ProductionBatchEntity)

    @Insert
    suspend fun insertMovements(movements: List<StockMovementEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutboxRows(rows: List<OutboxEntity>)

    /**
     * Atomicity contract (06 §4): batch + its derived movements (if any) + one outbox row per
     * written domain row commit as one local transaction — a batch can never exist locally
     * without its outbox entry, or vice versa. Self-contained on this Dao for the same reason
     * `SaleDao.recordSale` is (see that file's KDoc): Room's `@Transaction` default-body pattern
     * only joins abstract methods declared on the same Dao.
     */
    @Transaction
    suspend fun logBatch(batch: ProductionBatchEntity, movements: List<StockMovementEntity>, outboxRows: List<OutboxEntity>) {
        insertBatch(batch)
        if (movements.isNotEmpty()) insertMovements(movements)
        insertOutboxRows(outboxRows)
    }

    /**
     * Pull-side insert for `ProductionBatchSyncApplier` (03 §4.3 family 2 — append-only fact):
     * a batch pulled back after this device's own push, or re-delivered on a re-pulled page
     * (03 §4.1 crash recovery), is a harmless no-op rather than a constraint violation. Mirrors
     * [StockMovementDao.insertIfUnseen]; [insertBatch] above keeps its default ABORT strategy
     * because a *local* id collision there really is a bug worth crashing on.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfUnseen(batch: ProductionBatchEntity)

    /** Lets the applier tell "genuinely new" from "already seen" so a re-pull can't re-fire the
     *  batch-ready banner. Room's `@Insert` return value is not a documented signal for this. */
    @Query("SELECT EXISTS(SELECT 1 FROM production_batches WHERE id = :id)")
    suspend fun exists(id: Uuid): Boolean

    @Query(
        "SELECT * FROM production_batches WHERE branchId = :branchId AND loggedAt >= :startMillis " +
            "AND loggedAt < :endMillis ORDER BY loggedAt DESC",
    )
    fun observeTodaysBatches(branchId: Uuid, startMillis: Long, endMillis: Long): Flow<List<ProductionBatchEntity>>

    @Query("DELETE FROM production_batches")
    suspend fun clear()
}

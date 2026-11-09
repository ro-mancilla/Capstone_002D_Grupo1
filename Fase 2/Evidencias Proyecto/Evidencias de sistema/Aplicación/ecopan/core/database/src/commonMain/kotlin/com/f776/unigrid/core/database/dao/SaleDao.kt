package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.SaleEntity
import com.f776.unigrid.core.database.entity.SaleLineEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface SaleDao {

    @Insert
    suspend fun insertSale(sale: SaleEntity)

    @Insert
    suspend fun insertLines(lines: List<SaleLineEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutboxRow(row: OutboxEntity)

    /**
     * Atomicity contract (03 / 05 §1): sale + lines + outbox row commit as one local transaction
     * — a crash between them must be impossible. The outbox row is built and inserted here,
     * directly, rather than via a writer collaborator, because Room's `@Transaction`
     * default-body pattern only joins abstract methods declared on *this* Dao into the ambient
     * transaction (verified against the Room 2.8 KMP docs before writing this). `:data:sales:impl`
     * still never touches `SyncEngine`, transport, or connectivity — only this table's stable,
     * minimal insert-row shape (the row comes from `:data:sync:api`'s `outboxRow`).
     */
    @Transaction
    suspend fun recordSale(sale: SaleEntity, lines: List<SaleLineEntity>, outboxRow: OutboxEntity): Int {
        val receiptNumber = nextReceiptNumber()
        insertSale(sale.copy(receiptNumber = receiptNumber))
        insertLines(lines)
        insertOutboxRow(outboxRow)
        return receiptNumber
    }

    /** The next per-till correlativo: one past the highest ever assigned on this database. */
    @Query("SELECT COALESCE(MAX(receiptNumber), 0) + 1 FROM sales")
    suspend fun nextReceiptNumber(): Int

    @Query(
        "SELECT * FROM sales WHERE branchId = :branchId AND timestamp >= :startMillis " +
            "AND timestamp < :endMillis ORDER BY timestamp DESC",
    )
    fun observeSalesInRange(branchId: Uuid, startMillis: Long, endMillis: Long): Flow<List<SaleEntity>>

    @Query(
        "SELECT * FROM sale_lines WHERE saleId IN " +
            "(SELECT id FROM sales WHERE branchId = :branchId AND timestamp >= :startMillis AND timestamp < :endMillis)",
    )
    fun observeLinesInRange(branchId: Uuid, startMillis: Long, endMillis: Long): Flow<List<SaleLineEntity>>

    /**
     * Units of each product sold on this till whose sale the server has not yet acknowledged (its
     * outbox row is still `PENDING`/`IN_FLIGHT`; acked rows are deleted). The server's stock figure
     * does not include these yet, so the till subtracts them to keep its badge honest between syncs.
     * Weighed lines have no `quantity` and are ignored -- stock is Almacén, which is unit-only.
     */
    @Query(
        "SELECT sale_lines.productId AS productId, SUM(sale_lines.quantity) AS units FROM sale_lines " +
            "JOIN outbox ON outbox.mutationId = sale_lines.saleId " +
            "WHERE outbox.entityType = 'SALE' AND outbox.state IN ('PENDING', 'IN_FLIGHT') " +
            "AND sale_lines.quantity IS NOT NULL GROUP BY sale_lines.productId",
    )
    fun observeUnsyncedUnitsByProduct(): Flow<List<UnsyncedProductUnits>>

    @Query("DELETE FROM sales")
    suspend fun clearSales()

    @Query("DELETE FROM sale_lines")
    suspend fun clearLines()
}

data class UnsyncedProductUnits(val productId: Uuid, val units: Long)

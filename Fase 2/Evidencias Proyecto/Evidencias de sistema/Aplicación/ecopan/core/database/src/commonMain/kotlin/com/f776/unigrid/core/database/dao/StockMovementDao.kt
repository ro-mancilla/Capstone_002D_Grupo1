package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.f776.unigrid.core.database.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/** Projection for a Σ deltaGrams-by-ingredient query — not a domain type, just this DAO's shape. */
data class StockLevelSum(val ingredientId: Uuid, val totalDeltaGrams: Long)

@Dao
interface StockMovementDao {

    @Insert
    suspend fun insertMovements(movements: List<StockMovementEntity>)

    /**
     * Pull-side apply for the `STOCK_MOVEMENT` append-only fact family (03 §4.3): ignored on
     * conflict because the id is this row's client-minted UUID (03 §6.1 "insert if unseen") —
     * a movement pulled back after this device's own push, or re-delivered on a re-pulled page
     * (03 §4.1 crash recovery), is a harmless no-op rather than a constraint violation.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfUnseen(movements: List<StockMovementEntity>)

    @Query(
        "SELECT ingredientId, SUM(deltaGrams) AS totalDeltaGrams FROM stock_movements " +
            "WHERE branchId = :branchId GROUP BY ingredientId",
    )
    fun observeDeltaSumsByBranch(branchId: Uuid): Flow<List<StockLevelSum>>

    @Query(
        "SELECT * FROM stock_movements WHERE branchId = :branchId AND ingredientId = :ingredientId " +
            "ORDER BY recordedAt DESC",
    )
    fun observeByIngredient(branchId: Uuid, ingredientId: Uuid): Flow<List<StockMovementEntity>>

    @Query("DELETE FROM stock_movements")
    suspend fun clear()
}

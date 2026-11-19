package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.IngredientReconciliation
import com.f776.ecopan.data.database.repository.model.IngredientStock
import com.f776.ecopan.data.database.repository.model.StockMovement
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Append-only delta ledger (01 §6.1) -- **no update/delete member exists on this interface at all**.
 * Current stock is always Σ [deltaGrams] over movements, never a stored mutable quantity.
 */
interface StockMovementRepository {
    /**
     * Idempotent by [id] (`INSERT ... ON CONFLICT DO NOTHING` -- also silently absorbs the
     * `(sourceBatchId, ingredientId)` unique conflict used by the worker's fallback decrement path,
     * 02 §1/§6).
     */
    suspend fun append(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid,
        ingredientId: Uuid,
        deltaGrams: Long,
        kind: StockMovementKind,
        sourceBatchId: Uuid?,
        note: String?,
        createdBy: Uuid?,
        occurredAt: Instant
    ): IngestResult<StockMovement>

    suspend fun findById(id: Uuid): StockMovement?

    /** `SUM(delta_grams) GROUP BY ingredient_id` for one branch (02 §3 I3). */
    suspend fun sumByIngredient(tenantId: Uuid, branchId: Uuid): List<IngredientStock>

    /**
     * Ledger balance per ingredient over `[from, to)` for one branch, from a single grouped query:
     * opening = Σ before [from]; purchases / decrements / adjustments = Σ by kind inside the window.
     * Ingredients with no movement before [to] are absent. Closing is opening plus the three
     * per-kind sums, so a new [StockMovementKind] must get its own column here or closing would
     * stop equaling [sumByIngredient].
     */
    suspend fun reconcileByIngredient(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): List<IngredientReconciliation>

    /**
     * Distinct production batches that decremented any ingredient of one branch inside `[from, to)`.
     * Not derivable from [reconcileByIngredient]: one batch decrements every ingredient of its
     * formula, so summing the per-ingredient [IngredientReconciliation.batchCount] counts it once
     * per ingredient.
     */
    suspend fun countDecrementBatches(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): Int

    /** Paged, filterable (02 §3 I4). */
    suspend fun listMovements(
        tenantId: Uuid,
        branchId: Uuid? = null,
        ingredientId: Uuid? = null,
        from: Instant? = null,
        to: Instant? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<StockMovement>

    suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<StockMovement>
}

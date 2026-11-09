package com.f776.ecopan.domain.inventory

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface InventoryRepository {
    /** Every tenant ingredient with its derived current stock for [branchId] (0 if untouched). */
    fun stockLevels(branchId: Uuid): Flow<List<StockLevel>>

    fun movements(branchId: Uuid, ingredientId: Uuid): Flow<List<StockMovement>>

    /** Append-only — no update/delete member exists anywhere on this interface (01 §6.1). */
    suspend fun appendMovements(movements: List<StockMovement>)
}

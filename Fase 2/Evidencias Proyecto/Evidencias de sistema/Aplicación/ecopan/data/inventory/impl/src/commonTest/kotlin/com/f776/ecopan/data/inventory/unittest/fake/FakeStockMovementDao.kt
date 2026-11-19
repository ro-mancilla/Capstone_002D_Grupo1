package com.f776.ecopan.data.inventory.unittest.fake

import com.f776.unigrid.core.database.dao.StockLevelSum
import com.f776.unigrid.core.database.dao.StockMovementDao
import com.f776.unigrid.core.database.entity.StockMovementEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FakeStockMovementDao : StockMovementDao {
    private val state = MutableStateFlow<List<StockMovementEntity>>(emptyList())

    override suspend fun insertMovements(movements: List<StockMovementEntity>) {
        state.value = state.value + movements
    }

    override suspend fun insertIfUnseen(movements: List<StockMovementEntity>) {
        val existingIds = state.value.map { it.id }.toSet()
        state.value = state.value + movements.filter { it.id !in existingIds }
    }

    override fun observeDeltaSumsByBranch(branchId: Uuid) =
        state.map { rows ->
            rows.filter { it.branchId == branchId }
                .groupBy { it.ingredientId }
                .map { (ingredientId, group) -> StockLevelSum(ingredientId, group.sumOf { it.deltaGrams }) }
        }

    override fun observeByIngredient(branchId: Uuid, ingredientId: Uuid) =
        state.map { rows ->
            rows.filter { it.branchId == branchId && it.ingredientId == ingredientId }
                .sortedByDescending { it.recordedAt }
        }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

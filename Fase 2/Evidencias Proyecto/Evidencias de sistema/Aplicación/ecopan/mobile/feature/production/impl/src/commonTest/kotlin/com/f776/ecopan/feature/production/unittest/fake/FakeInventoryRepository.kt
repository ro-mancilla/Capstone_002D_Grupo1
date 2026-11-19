package com.f776.ecopan.feature.production.unittest.fake

import com.f776.ecopan.domain.inventory.InventoryRepository
import com.f776.ecopan.domain.inventory.StockLevel
import com.f776.ecopan.domain.inventory.StockMovement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

class FakeInventoryRepository : InventoryRepository {
    val levels = MutableStateFlow<List<StockLevel>>(emptyList())

    override fun stockLevels(branchId: Uuid) = levels

    override fun movements(branchId: Uuid, ingredientId: Uuid) = flowOf(emptyList<StockMovement>())

    override suspend fun appendMovements(movements: List<StockMovement>) = error("not needed by StockGlanceViewModelTest")
}

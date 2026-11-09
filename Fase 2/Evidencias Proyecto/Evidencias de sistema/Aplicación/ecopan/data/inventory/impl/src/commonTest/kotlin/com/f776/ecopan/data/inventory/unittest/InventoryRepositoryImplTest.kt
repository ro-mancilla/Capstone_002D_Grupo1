package com.f776.ecopan.data.inventory.unittest

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.inventory.InventoryRepositoryImpl
import com.f776.ecopan.data.inventory.unittest.fake.FakeIngredientDao
import com.f776.ecopan.data.inventory.unittest.fake.FakeStockMovementDao
import com.f776.ecopan.data.inventory.unittest.fake.FakeTenantRepository
import com.f776.ecopan.domain.inventory.MovementKind
import com.f776.ecopan.domain.inventory.StockMovement
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.unigrid.core.database.entity.IngredientEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class InventoryRepositoryImplTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val ingredientDao = FakeIngredientDao()
    private val stockMovementDao = FakeStockMovementDao()
    private val tenantRepository = FakeTenantRepository()
    private val repository = InventoryRepositoryImpl(ingredientDao, stockMovementDao, tenantRepository)

    @Test
    fun `an ingredient with no movements shows up at zero not missing`() = runTest {
        val ingredientId = seedIngredient("Flour")

        val levels = repository.stockLevels(branchId).first()

        assertEquals(0L, levels.single { it.ingredientId == ingredientId }.currentGrams)
    }

    @Test
    fun `stock level is the signed sum of movements negative allowed`() = runTest {
        val ingredientId = seedIngredient("Flour")
        repository.appendMovements(
            listOf(
                movement(ingredientId, deltaGrams = 25_000, kind = MovementKind.PURCHASE),
                movement(ingredientId, deltaGrams = -5_952, kind = MovementKind.FORMULA_DECREMENT),
                movement(ingredientId, deltaGrams = -30_000, kind = MovementKind.MANUAL_ADJUSTMENT),
            ),
        )

        val level = repository.stockLevels(branchId).first().single { it.ingredientId == ingredientId }

        assertEquals(25_000L - 5_952L - 30_000L, level.currentGrams)
    }

    @Test
    fun `movements are scoped to their branch and ingredient`() = runTest {
        val ingredientId = seedIngredient("Flour")
        val otherBranch = Uuid.random()
        repository.appendMovements(
            listOf(
                movement(ingredientId, deltaGrams = 1_000, branchId = branchId),
                movement(ingredientId, deltaGrams = 9_999, branchId = otherBranch),
            ),
        )

        val movements = repository.movements(branchId, ingredientId).first()

        assertEquals(listOf(1_000L), movements.map { it.deltaGrams })
    }

    private suspend fun seedIngredient(name: String): Uuid {
        tenantRepository.upsertTenant(
            TenantProfile(tenantId, "panaderia-centro", "Panadería Centro", "CLP", setOf(EntitlementModule.INVENTORY_FORMULAS)),
        )
        val ingredientId = Uuid.random()
        ingredientDao.upsertAll(
            listOf(
                IngredientEntity(
                    id = ingredientId,
                    tenantId = tenantId,
                    name = name,
                    lowStockThresholdGrams = null,
                    active = true,
                    serverSeq = 1,
                    cachedAt = Clock.System.now(),
                ),
            ),
        )
        return ingredientId
    }

    private fun movement(
        ingredientId: Uuid,
        deltaGrams: Long,
        kind: MovementKind = MovementKind.PURCHASE,
        branchId: Uuid = this.branchId,
    ) = StockMovement(
        id = Uuid.random(),
        tenantId = tenantId,
        branchId = branchId,
        ingredientId = ingredientId,
        deltaGrams = deltaGrams,
        kind = kind,
        sourceBatchId = null,
        note = null,
        recordedAt = Clock.System.now(),
        recordedBy = Uuid.random(),
    )
}

package com.f776.ecopan.data.inventory.unittest

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.inventory.IngredientRepositoryImpl
import com.f776.ecopan.data.inventory.unittest.fake.FakeIngredientDao
import com.f776.ecopan.data.inventory.unittest.fake.FakeTenantRepository
import com.f776.ecopan.domain.inventory.IngredientUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class IngredientRepositoryImplTest {

    private val tenantId = Uuid.random()
    private val tenantRepository = FakeTenantRepository()
    private val repository = IngredientRepositoryImpl(FakeIngredientDao(), tenantRepository)

    @Test
    fun `no tenant cached yet observes an empty ingredient list`() = runTest {
        assertEquals(emptyList(), repository.observeActiveIngredients().first())
    }

    @Test
    fun `ingredients round-trip a nullable low-stock threshold and stay ordered by name`() = runTest {
        tenantRepository.upsertTenant(
            TenantProfile(tenantId, "panaderia-centro", "Panadería Centro", "CLP", setOf(EntitlementModule.INVENTORY_FORMULAS)),
        )
        repository.upsertIngredients(
            listOf(
                IngredientUpsert(Uuid.random(), tenantId, "Yeast", lowStockThresholdGrams = null, active = true, serverSeq = 1),
                IngredientUpsert(Uuid.random(), tenantId, "Flour", lowStockThresholdGrams = 5_000, active = true, serverSeq = 1),
            ),
        )

        val ingredients = repository.observeActiveIngredients().first()

        assertEquals(listOf("Flour", "Yeast"), ingredients.map { it.name })
        assertEquals(5_000L, ingredients.first { it.name == "Flour" }.lowStockThresholdGrams)
        assertNull(ingredients.first { it.name == "Yeast" }.lowStockThresholdGrams)
    }
}

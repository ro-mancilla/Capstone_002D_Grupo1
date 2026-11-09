package com.f776.ecopan.api

import com.f776.ecopan.api.fakes.FakeForecastRepository
import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.fakes.FakeCatalogRepository
import com.f776.ecopan.api.fakes.FakeIngredientRepository
import com.f776.ecopan.api.fakes.FakeProductStockRepository
import com.f776.ecopan.api.fakes.FakeStockMovementRepository
import com.f776.ecopan.api.feature.analytics.domain.AnalyticsServiceImpl
import com.f776.ecopan.api.feature.inventory.controller.dto.StockLevelDto
import com.f776.ecopan.api.feature.inventory.domain.InventoryServiceImpl
import com.f776.ecopan.api.feature.inventory.domain.ProductStockCalculator
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.repository.AnalyticsRepository
import com.f776.ecopan.data.database.repository.ForecastRepository
import com.f776.ecopan.data.database.repository.model.Forecast
import com.f776.ecopan.data.database.repository.model.ProductionFact
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One low-stock rule across ingredients, Almacén products and the baker glance: **at or under**
 * the reorder level (CONTEXT.md, *Stock bajo*). These pin the boundary on both backend surfaces
 * that compute it for ingredients.
 *
 * Zero is a deliberate case. CONTEXT.md splits Almacén products into *Stock bajo* (above 0) and
 * *Agotado* (exactly 0), but ingredients have no *Agotado* state, so an ingredient at 0 stays
 * flagged low: it is the one that needs reordering most.
 */
class IngredientLowStockRuleTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val ingredients = FakeIngredientRepository()
    private val movements = FakeStockMovementRepository()

    private fun seed(thresholdGrams: Long, currentGrams: Long) = runBlocking {
        val ingredient = ingredients.create(Uuid.random(), tenantId, "Harina", thresholdGrams)
        movements.append(
            id = Uuid.random(), tenantId = tenantId, branchId = branchId, ingredientId = ingredient.id,
            deltaGrams = currentGrams, kind = StockMovementKind.PURCHASE, sourceBatchId = null, note = null,
            createdBy = null, occurredAt = Clock.System.now(),
        )
    }

    private fun inventoryLow(thresholdGrams: Long, currentGrams: Long): Boolean = runBlocking {
        seed(thresholdGrams, currentGrams)
        val productStock = FakeProductStockRepository()
        val outcome = InventoryServiceImpl(
            ingredients, movements, FakeCatalogRepository(), productStock, ProductStockCalculator(productStock), reportingTz = "America/Santiago",
        ).stockLevels(tenantId, branchId, principalBranchId = null)
        assertIs<ServiceOutcome.Success<List<StockLevelDto>>>(outcome).value.single().lowStock
    }

    private fun analyticsLow(thresholdGrams: Long, currentGrams: Long): Boolean = runBlocking {
        seed(thresholdGrams, currentGrams)
        AnalyticsServiceImpl(NoAnalytics, ingredients, movements, FakeForecastRepository(), reportingTz = "America/Santiago")
            .stockLevels(tenantId, branchId).single().lowStock
    }

    @Test
    fun `inventory flags an ingredient exactly at its reorder level as low`() {
        assertTrue(inventoryLow(thresholdGrams = 1_000, currentGrams = 1_000))
    }

    @Test
    fun `inventory does not flag an ingredient above its reorder level`() {
        assertFalse(inventoryLow(thresholdGrams = 1_000, currentGrams = 1_001))
    }

    @Test
    fun `inventory keeps an ingredient at zero low whatever its reorder level`() {
        assertTrue(inventoryLow(thresholdGrams = 0, currentGrams = 0))
    }

    @Test
    fun `inventory keeps an ingredient at zero low when the reorder level is above zero`() {
        assertTrue(inventoryLow(thresholdGrams = 1_000, currentGrams = 0))
    }

    @Test
    fun `overview analytics flags an ingredient exactly at its reorder level as low`() {
        assertTrue(analyticsLow(thresholdGrams = 1_000, currentGrams = 1_000))
    }

    @Test
    fun `overview analytics does not flag an ingredient above its reorder level`() {
        assertFalse(analyticsLow(thresholdGrams = 1_000, currentGrams = 1_001))
    }

    @Test
    fun `overview analytics keeps an ingredient at zero low whatever its reorder level`() {
        assertTrue(analyticsLow(thresholdGrams = 0, currentGrams = 0))
    }

    @Test
    fun `overview analytics keeps an ingredient at zero low when the reorder level is above zero`() {
        assertTrue(analyticsLow(thresholdGrams = 1_000, currentGrams = 0))
    }

    private object NoAnalytics : AnalyticsRepository {
        override suspend fun saleLineFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<SaleLineFact> =
            emptyList()

        override suspend fun productionFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductionFact> =
            emptyList()
    }


}

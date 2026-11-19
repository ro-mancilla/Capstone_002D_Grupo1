package com.f776.ecopan.data.production.unittest

import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.production.BatchReadyBus
import com.f776.ecopan.data.production.ProductionRepositoryImpl
import com.f776.ecopan.data.production.domain.FormulaPreview
import com.f776.ecopan.data.production.sideeffects.FormulaDecrementSideEffect
import com.f776.ecopan.data.production.sideeffects.NoOpBatchSideEffects
import com.f776.ecopan.data.production.unittest.fake.FakeEntitlementsProvider
import com.f776.ecopan.data.production.unittest.fake.FakeFormulaRepository
import com.f776.ecopan.data.production.unittest.fake.FakeIngredientRepository
import com.f776.ecopan.data.production.unittest.fake.FakeOutboxDao
import com.f776.ecopan.data.production.unittest.fake.FakeProductRepository
import com.f776.ecopan.data.production.unittest.fake.FakeProductionBatchDao
import com.f776.ecopan.domain.catalog.Formula
import com.f776.ecopan.domain.catalog.FormulaLine
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.inventory.Ingredient
import com.f776.ecopan.domain.production.BatchLogResult
import com.f776.ecopan.domain.production.BatchSyncState
import com.f776.ecopan.domain.production.LogBatch
import com.f776.ecopan.domain.production.NoDecrementReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ProductionRepositoryImplTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val bakerId = Uuid.random()
    private val productId = Uuid.random()
    private val flourId = Uuid.random()
    private val waterId = Uuid.random()

    private val outboxDao = FakeOutboxDao()
    private val batchDao = FakeProductionBatchDao(outboxDao)
    private val productRepository = FakeProductRepository()
    private val formulaRepository = FakeFormulaRepository()
    private val ingredientRepository = FakeIngredientRepository()
    private val entitlementsProvider = FakeEntitlementsProvider()
    private val formulaPreview = FormulaPreview(formulaRepository, ingredientRepository)
    private val batchReadyBus = BatchReadyBus()
    private val repository = ProductionRepositoryImpl(
        batchDao,
        outboxDao,
        productRepository,
        formulaPreview,
        FormulaDecrementSideEffect(formulaPreview),
        NoOpBatchSideEffects(),
        entitlementsProvider,
        batchReadyBus,
    )

    @Test
    fun `logBatch entitled with a formula writes the batch its movements and both outbox rows`() = runTest {
        seedProductAndFormula()
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, true)

        val result = repository.logBatch(LogBatch(branchId, bakerId, productId, mixWeightGrams = 10_000))

        val logged = assertIs<BatchLogResult.Logged>(result)
        assertEquals(2, logged.derivedMovements.size)
        assertEquals(2, batchDao.movements.size)
        assertEquals(3, outboxDao.observePendingCount().first()) // 1 batch row + 2 movement rows
    }

    @Test
    fun `movement deltas are negative and reference the batch as sourceBatchId`() = runTest {
        seedProductAndFormula()
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, true)

        val result = repository.logBatch(LogBatch(branchId, bakerId, productId, mixWeightGrams = 10_000)) as BatchLogResult.Logged

        batchDao.movements.forEach { movement ->
            assertTrue(movement.deltaGrams < 0, "expected a negative delta, was ${movement.deltaGrams}")
            assertEquals(result.batch.id, movement.sourceBatchId)
        }
    }

    @Test
    fun `logBatch with the module disabled still records the batch but writes zero movements`() = runTest {
        seedProductAndFormula()
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, false)

        val result = repository.logBatch(LogBatch(branchId, bakerId, productId, mixWeightGrams = 10_000))

        val skipped = assertIs<BatchLogResult.LoggedWithoutDecrement>(result)
        assertEquals(NoDecrementReason.MODULE_DISABLED, skipped.reason)
        assertTrue(batchDao.movements.isEmpty())
        assertEquals(1, outboxDao.observePendingCount().first()) // batch row only
    }

    @Test
    fun `logBatch with no formula for the product still logs the batch and enqueues its outbox row`() = runTest {
        productRepository.seed(product())
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, true)
        // no formula seeded

        val result = repository.logBatch(LogBatch(branchId, bakerId, productId, mixWeightGrams = 10_000))

        val skipped = assertIs<BatchLogResult.LoggedWithoutDecrement>(result)
        assertEquals(NoDecrementReason.NO_FORMULA, skipped.reason)
        assertTrue(batchDao.movements.isEmpty())
        assertEquals(1, outboxDao.observePendingCount().first())
    }

    @Test
    fun `todaysBatches derives PENDING while the batch's outbox row hasn't been acked SYNCED once it has`() = runTest {
        seedProductAndFormula()
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, false)
        val logged = repository.logBatch(LogBatch(branchId, bakerId, productId, mixWeightGrams = 10_000)) as BatchLogResult.LoggedWithoutDecrement

        val pending = repository.todaysBatches(branchId).first().single()
        assertEquals(BatchSyncState.PENDING, pending.syncState)

        outboxDao.deleteAcked(listOf(logged.batch.id))

        val synced = repository.todaysBatches(branchId).first().single()
        assertEquals(BatchSyncState.SYNCED, synced.syncState)
    }

    /**
     * The join asks "is this id still waiting in the outbox?", and a rejected row is not waiting --
     * it has been refused and will never be retried. Reading that as SYNCED told a baker the
     * morning's batch had registered when the server had thrown it away.
     */
    @Test
    fun `todaysBatches reports REJECTED not SYNCED once the server has refused the batch`() = runTest {
        seedProductAndFormula()
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, false)
        val logged = repository.logBatch(LogBatch(branchId, bakerId, productId, mixWeightGrams = 10_000)) as BatchLogResult.LoggedWithoutDecrement

        outboxDao.markFailed(logged.batch.id, "BRANCH_FORBIDDEN")

        assertEquals(BatchSyncState.REJECTED, repository.todaysBatches(branchId).first().single().syncState)
    }

    @Test
    fun `previewDerivation returns null while the module is disabled even with a valid formula`() = runTest {
        seedProductAndFormula()
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, false)

        assertNull(repository.previewDerivation(productId, mixWeightGrams = 10_000))
    }

    @Test
    fun `previewDerivation matches exactly what logBatch would write`() = runTest {
        seedProductAndFormula()
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, true)

        val preview = repository.previewDerivation(productId, mixWeightGrams = 10_000)
        val logged = repository.logBatch(LogBatch(branchId, bakerId, productId, mixWeightGrams = 10_000)) as BatchLogResult.Logged

        assertEquals(preview, logged.derivedMovements)
    }

    /**
     * The largest-remainder split hands its results back in remainder order, which moves with the
     * weight: a baker saw "levadura, harina, agua, sal" and then "levadura, agua, sal, harina" for
     * the same product. The preview and the written movements both follow the formula's own order.
     */
    @Test
    fun `derived ingredients keep the formula's line order whatever the weight`() = runTest {
        val saltId = Uuid.random()
        val yeastId = Uuid.random()
        productRepository.seed(product())
        listOf(flourId to "Harina", waterId to "Agua", saltId to "Sal", yeastId to "Levadura").forEach { (id, name) ->
            ingredientRepository.seed(Ingredient(id, tenantId, name, null, active = true))
        }
        val lineOrder = listOf(flourId, waterId, saltId, yeastId)
        formulaRepository.seed(
            Formula(
                id = Uuid.random(),
                tenantId = tenantId,
                productId = productId,
                active = true,
                lines = listOf(
                    FormulaLine(flourId, 10_000),
                    FormulaLine(waterId, 6_500),
                    FormulaLine(saltId, 200),
                    FormulaLine(yeastId, 100),
                ),
            ),
        )
        entitlementsProvider.setEnabled(EntitlementModule.INVENTORY_FORMULAS, true)

        listOf(10_000L, 5_000L, 2_000L, 7_777L, 12_345L).forEach { grams ->
            val preview = repository.previewDerivation(productId, grams)!!
            assertEquals(lineOrder, preview.map { it.ingredientId }, "preview order at $grams g")
            val logged = repository.logBatch(LogBatch(branchId, bakerId, productId, grams)) as BatchLogResult.Logged
            assertEquals(lineOrder, logged.derivedMovements.map { it.ingredientId }, "logged order at $grams g")
        }
    }

    private fun seedProductAndFormula() {
        productRepository.seed(product())
        ingredientRepository.seed(Ingredient(flourId, tenantId, "Flour", null, active = true))
        ingredientRepository.seed(Ingredient(waterId, tenantId, "Water", null, active = true))
        formulaRepository.seed(
            Formula(
                id = Uuid.random(),
                tenantId = tenantId,
                productId = productId,
                active = true,
                lines = listOf(FormulaLine(flourId, 10_000), FormulaLine(waterId, 6_500)),
            ),
        )
    }

    private fun product() = Product(
        id = productId,
        tenantId = tenantId,
        name = "Marraqueta",
        productType = "Marraqueta",
        soldBy = SoldBy.WEIGHT,
        pricePerKgMinor = 1200,
        priceMinor = null,
        barcode = null,
        imageUrl = null,
        active = true,
        mixToLoafRatioMilli = 2200,
        minMixGrams = null,
        maxMixGrams = null,
    )
}

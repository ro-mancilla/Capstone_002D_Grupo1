package com.f776.ecopan.feature.production.unittest

import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.domain.inventory.Ingredient
import com.f776.ecopan.domain.inventory.StockLevel
import com.f776.ecopan.feature.production.screens.stockglance.StockGlanceViewModel
import com.f776.ecopan.feature.production.screens.stockglance.StockStatus
import com.f776.ecopan.feature.production.unittest.fake.FakeIngredientRepository
import com.f776.ecopan.feature.production.unittest.fake.FakeInventoryRepository
import com.f776.ecopan.feature.production.unittest.fake.FakeShellContext
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class StockGlanceViewModelTest {

    private lateinit var inventoryRepository: FakeInventoryRepository
    private lateinit var ingredientRepository: FakeIngredientRepository
    private lateinit var shellContext: FakeShellContext
    private val branchId = Uuid.random()
    private val tenantId = Uuid.random()
    private val testDispatcher = MainDispatcherRule.createTestDispatcher()

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        inventoryRepository = FakeInventoryRepository()
        ingredientRepository = FakeIngredientRepository()
        shellContext = FakeShellContext(branchId = branchId)
    }

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    private fun viewModel() = StockGlanceViewModel(
        inventoryRepository = inventoryRepository,
        ingredientRepository = ingredientRepository,
        shellContext = shellContext,
    )

    @Test
    fun `stock level is sum of movements including negatives and sorted by name`() = runTest {
        inventoryRepository.levels.value = listOf(
            StockLevel(Uuid.random(), "Sal", -200),
            StockLevel(Uuid.random(), "Harina", 45_000),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(listOf("Harina", "Sal"), state.rows.map { it.name })
        assertTrue(state.rows.first { it.name == "Sal" }.currentGrams < 0)
    }

    @Test
    fun `reorder point is joined onto the row from the ingredient`() = runTest {
        val flourId = Uuid.random()
        inventoryRepository.levels.value = listOf(StockLevel(flourId, "Harina", 45_000))
        ingredientRepository.ingredients.value = listOf(
            Ingredient(id = flourId, tenantId = tenantId, name = "Harina", lowStockThresholdGrams = 40_000, active = true),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(40_000, viewModel.uiState.first().rows.single().lowStockThresholdGrams)
    }

    @Test
    fun `a level with no matching ingredient still renders without a reorder point`() = runTest {
        inventoryRepository.levels.value = listOf(StockLevel(Uuid.random(), "Levadura", 900))
        ingredientRepository.ingredients.value = emptyList()
        val viewModel = viewModel()
        advanceUntilIdle()

        val row = viewModel.uiState.first().rows.single()
        assertEquals("Levadura", row.name)
        assertNull(row.lowStockThresholdGrams)
    }

    @Test
    fun `negative count drives the reconcile banner`() = runTest {
        inventoryRepository.levels.value = listOf(
            StockLevel(Uuid.random(), "Harina", -33_279),
            StockLevel(Uuid.random(), "Sal", -664),
            StockLevel(Uuid.random(), "Azúcar", 0),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.first().negativeCount)
    }

    private fun ingredient(id: Uuid, name: String, thresholdGrams: Long?) =
        Ingredient(id = id, tenantId = tenantId, name = name, lowStockThresholdGrams = thresholdGrams, active = true)

    /** The design's own data: Azúcar -0,2, Manteca 0, Levadura 1 (low), Harina/Agua/Sal fine. */
    private fun seedDesignData(): StockGlanceViewModel {
        val ids = List(6) { Uuid.random() }
        inventoryRepository.levels.value = listOf(
            StockLevel(ids[0], "Azúcar", -200),
            StockLevel(ids[1], "Manteca", 0),
            StockLevel(ids[2], "Levadura", 1_000),
            StockLevel(ids[3], "Harina", 194_048),
            StockLevel(ids[4], "Agua", 146_131),
            StockLevel(ids[5], "Sal", 9_881),
        )
        ingredientRepository.ingredients.value = listOf(
            ingredient(ids[0], "Azúcar", 3_000),
            ingredient(ids[1], "Manteca", 5_000),
            ingredient(ids[2], "Levadura", 1_000),
            ingredient(ids[3], "Harina", 20_000),
            ingredient(ids[4], "Agua", null),
            ingredient(ids[5], "Sal", 2_000),
        )
        return viewModel()
    }

    @Test
    fun `por reponer lists the negative balance first, then the low ones, most urgent first`() = runTest {
        val viewModel = seedDesignData()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(listOf("Azúcar", "Manteca", "Levadura"), state.toReplenish.map { it.name })
        assertEquals(
            listOf(StockStatus.NEGATIVE, StockStatus.LOW, StockStatus.LOW),
            state.toReplenish.map { it.status },
        )
    }

    @Test
    fun `con stock lists the rest, the biggest balance first`() = runTest {
        val viewModel = seedDesignData()
        advanceUntilIdle()

        assertEquals(listOf("Harina", "Agua", "Sal"), viewModel.uiState.first().inStock.map { it.name })
    }

    @Test
    fun `a balance at zero is low stock, there is no agotado for ingredients`() = runTest {
        val id = Uuid.random()
        inventoryRepository.levels.value = listOf(StockLevel(id, "Manteca", 0))
        ingredientRepository.ingredients.value = listOf(ingredient(id, "Manteca", 5_000))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(StockStatus.LOW, viewModel.uiState.first().rows.single().status)
    }

    @Test
    fun `a balance exactly at the reorder point is low, one gram above is not`() = runTest {
        val low = Uuid.random()
        val fine = Uuid.random()
        inventoryRepository.levels.value = listOf(StockLevel(low, "Levadura", 1_000), StockLevel(fine, "Sal", 2_001))
        ingredientRepository.ingredients.value = listOf(ingredient(low, "Levadura", 1_000), ingredient(fine, "Sal", 2_000))
        val viewModel = viewModel()
        advanceUntilIdle()

        val byName = viewModel.uiState.first().rows.associate { it.name to it.status }
        assertEquals(StockStatus.LOW, byName["Levadura"])
        assertEquals(StockStatus.OK, byName["Sal"])
    }

    @Test
    fun `an ingredient with no reorder point is never flagged low, however little is left`() = runTest {
        val id = Uuid.random()
        inventoryRepository.levels.value = listOf(StockLevel(id, "Agua", 5))
        ingredientRepository.ingredients.value = listOf(ingredient(id, "Agua", null))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(StockStatus.OK, state.rows.single().status)
        assertTrue(state.toReplenish.isEmpty())
    }

    @Test
    fun `negative still wins when there is no reorder point`() = runTest {
        val id = Uuid.random()
        inventoryRepository.levels.value = listOf(StockLevel(id, "Agua", -1))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(StockStatus.NEGATIVE, viewModel.uiState.first().rows.single().status)
    }

    @Test
    fun `the alert can name the single negative ingredient and counts several`() = runTest {
        val viewModel = seedDesignData()
        advanceUntilIdle()
        assertEquals(listOf("Azúcar"), viewModel.uiState.first().negativeRows.map { it.name })

        inventoryRepository.levels.value = listOf(
            StockLevel(Uuid.random(), "Azúcar", -200),
            StockLevel(Uuid.random(), "Sal", -5),
            StockLevel(Uuid.random(), "Harina", -9),
        )
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.first().negativeCount)
    }

    @Test
    fun `the last sync time and offline-ness reach the state`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.lastSyncedAt)
        assertFalse(viewModel.uiState.value.offline)

        val at = Instant.parse("2026-10-07T09:10:00Z")
        shellContext.setLastSyncedAt(at)
        shellContext.setIndicator(SyncIndicatorState.OfflinePending(0))
        advanceUntilIdle()

        assertEquals(at, viewModel.uiState.value.lastSyncedAt)
        assertTrue(viewModel.uiState.value.offline)
    }
}

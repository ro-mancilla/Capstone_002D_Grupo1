package com.f776.ecopan.feature.production.unittest

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.production.Estimator
import com.f776.ecopan.domain.production.SuggestedProduction
import com.f776.ecopan.domain.production.SuggestedQuantity
import com.f776.ecopan.feature.production.screens.batchentry.BatchEntryAction
import com.f776.ecopan.feature.production.screens.batchentry.BatchEntryViewModel
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedUnit
import com.f776.ecopan.feature.production.unittest.fake.FakeEntitlementsProvider
import com.f776.ecopan.feature.production.unittest.fake.FakeFormulaRepository
import com.f776.ecopan.feature.production.unittest.fake.FakeProductRepository
import com.f776.ecopan.feature.production.unittest.fake.FakeProductionRepository
import com.f776.ecopan.feature.production.unittest.fake.FakeShellContext
import com.f776.ecopan.feature.production.unittest.fake.FakeSuggestedProductionRepository
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * *Producción sugerida para mañana* on the baker's production screen: read from the local cache,
 * gated by DEMAND_FORECASTING, hidden when stale. The baseline-labelled path is the main case --
 * the transfer experiment (#50) left every bakery series on a baseline, and a baseline row must
 * never read as the model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SuggestedProductionViewModelTest {

    private val testDispatcher = MainDispatcherRule.createTestDispatcher()

    // 2026-07-29T19:00Z is mid-afternoon on the 29th in Santiago (UTC-4): tomorrow is the 30th.
    private val now = Instant.parse("2026-07-29T19:00:00Z")
    private val tomorrow = LocalDate(2026, 7, 30)
    private val branchId = Uuid.random()

    private lateinit var suggestions: FakeSuggestedProductionRepository
    private lateinit var entitlements: FakeEntitlementsProvider
    private lateinit var viewModel: BatchEntryViewModel
    private val shell = FakeShellContext(branchId = branchId, userId = Uuid.random())

    private var clockNow = now
    private val movableClock = object : Clock {
        override fun now(): Instant = clockNow
    }

    private fun suggestion(
        name: String,
        quantity: SuggestedQuantity,
        modelVersion: String = "baseline-trailing-mean",
        generatedAt: Instant = now - 2.hours,
        day: LocalDate = tomorrow,
        type: String = "Pan",
    ) = SuggestedProduction(
        id = Uuid.random(), branchId = branchId, productId = Uuid.random(), productName = name, productType = type,
        forecastDate = day, quantity = quantity, estimator = Estimator.fromModelVersion(modelVersion),
        generatedAt = generatedAt,
    )

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        suggestions = FakeSuggestedProductionRepository()
        entitlements = FakeEntitlementsProvider().apply {
            enabled.value = setOf(EntitlementModule.POS_SALES, EntitlementModule.DEMAND_FORECASTING)
        }
        viewModel = newViewModel()
    }

    /** Uses the production default timezone on purpose: that default is what is under test. */
    private fun newViewModel() = BatchEntryViewModel(
        productionRepository = FakeProductionRepository(),
        productRepository = FakeProductRepository(),
        formulaRepository = FakeFormulaRepository(),
        entitlementsProvider = entitlements,
        shellContext = shell,
        suggestedProductionRepository = suggestions,
        clock = movableClock,
    )

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    @Test
    fun `lists tomorrow's quantity per product with unit and estimator, baseline labelled as baseline`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("Marraqueta", SuggestedQuantity.Grams(12_500)),
            suggestion("Hallulla", SuggestedQuantity.Units(40), modelVersion = "baseline-swlw"),
        )
        advanceUntilIdle()

        val card = assertNotNull(viewModel.uiState.value.suggestedProduction)
        // Kilogram groups come before unit groups, whatever the names sort to.
        assertEquals(listOf("Marraqueta", "Hallulla"), card.rows.map { it.productName })
        assertEquals(SuggestedQuantity.Grams(12_500), card.rows.first().quantity)
        assertEquals(SuggestedQuantity.Units(40), card.rows.last().quantity)
        assertEquals(setOf(Estimator.BASELINE), card.rows.map { it.estimator }.toSet())
    }

    @Test
    fun `a trained-model row is labelled model and an unrecognised version is unknown`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("Marraqueta", SuggestedQuantity.Grams(1), modelVersion = "lgbm-favorita"),
            suggestion("Hallulla", SuggestedQuantity.Units(1), modelVersion = "mystery-9"),
        )
        advanceUntilIdle()

        val byName = assertNotNull(viewModel.uiState.value.suggestedProduction).rows.associate { it.productName to it.estimator }
        assertEquals(Estimator.MODEL, byName["Marraqueta"])
        assertEquals(Estimator.UNKNOWN, byName["Hallulla"])
    }

    @Test
    fun `asks the cache for tomorrow in the reporting zone, in the baker's branch`() = runTest {
        advanceUntilIdle()

        assertEquals(listOf(branchId to tomorrow), suggestions.requested)
    }

    @Test
    fun `only tomorrow's rows are shown`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("Marraqueta", SuggestedQuantity.Grams(1)),
            suggestion("Hoy", SuggestedQuantity.Grams(2), day = LocalDate(2026, 7, 29)),
        )
        advanceUntilIdle()

        assertEquals(listOf("Marraqueta"), assertNotNull(viewModel.uiState.value.suggestedProduction).rows.map { it.productName })
    }

    @Test
    fun `is hidden when the tenant does not have DEMAND_FORECASTING`() = runTest {
        entitlements.enabled.value = setOf(EntitlementModule.POS_SALES)
        suggestions.rows.value = listOf(suggestion("Marraqueta", SuggestedQuantity.Grams(1)))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.suggestedProduction)

        entitlements.enabled.value = setOf(EntitlementModule.POS_SALES, EntitlementModule.DEMAND_FORECASTING)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.suggestedProduction)
    }

    @Test
    fun `stale suggestions, older than a day, are hidden`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("Marraqueta", SuggestedQuantity.Grams(1), generatedAt = now - 25.hours),
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.suggestedProduction)
    }

    @Test
    fun `a stale row is dropped while fresh ones stay, and updatedAt is the newest`() = runTest {
        val fresh = now - 3.hours
        suggestions.rows.value = listOf(
            suggestion("Old", SuggestedQuantity.Grams(1), generatedAt = now - 30.hours),
            suggestion("Fresh", SuggestedQuantity.Grams(2), generatedAt = fresh),
            suggestion("Fresher", SuggestedQuantity.Grams(3), generatedAt = now - 1.hours),
        )
        advanceUntilIdle()

        val card = assertNotNull(viewModel.uiState.value.suggestedProduction)
        assertEquals(setOf("Fresh", "Fresher"), card.rows.map { it.productName }.toSet())
        assertEquals(now - 1.hours, card.updatedAt)
    }

    @Test
    fun `nothing cached means no card`() = runTest {
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.suggestedProduction)
    }

    @Test
    fun `tomorrow is the reporting-zone day by default, not the phone's or UTC's`() = runTest {
        // 02:30Z on the 30th is still the evening of the 29th in Santiago: tomorrow is the 30th.
        // A UTC (or UTC+ phone) reading would ask for the 31st.
        clockNow = Instant.parse("2026-07-30T02:30:00Z")
        suggestions.requested.clear()
        viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf(branchId to LocalDate(2026, 7, 30)), suggestions.requested)
    }

    @Test
    fun `resuming re-evaluates staleness and the day without a timer`() = runTest {
        suggestions.rows.value = listOf(suggestion("Marraqueta", SuggestedQuantity.Grams(1), generatedAt = now - 2.hours))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.suggestedProduction)

        // The screen stays open; a day passes. Nothing emits until the baker comes back.
        clockNow = now + 25.hours
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.suggestedProduction, "no timer: still shown until something re-evaluates")

        viewModel.onAction(BatchEntryAction.OnResumed)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.suggestedProduction)
        assertEquals(LocalDate(2026, 7, 31), suggestions.requested.last().second)
    }

    @Test
    fun `rows group by product type and unit, largest first within a group`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("Hallulla", SuggestedQuantity.Grams(3_200)),
            suggestion("Pan Amasado", SuggestedQuantity.Grams(22_700)),
            suggestion("Marraqueta", SuggestedQuantity.Grams(18_300)),
            suggestion("Berlín", SuggestedQuantity.Units(19), type = "Pastelería"),
            suggestion("Empanada de Pino", SuggestedQuantity.Units(19), type = "Pastelería"),
        )
        advanceUntilIdle()

        val card = assertNotNull(viewModel.uiState.value.suggestedProduction)
        assertEquals(listOf("Pan" to SuggestedUnit.KILOGRAMS, "Pastelería" to SuggestedUnit.UNITS), card.groups.map { it.typeName to it.unit })
        assertEquals(listOf("Pan Amasado", "Marraqueta", "Hallulla"), card.groups[0].rows.map { it.productName })
        // Ties break by name so the order does not flicker between syncs.
        assertEquals(listOf("Berlín", "Empanada de Pino"), card.groups[1].rows.map { it.productName })
        assertEquals(5, card.totalCount)
    }

    @Test
    fun `the folded card shows two rows of the first group and one of each later group`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("Pan Amasado", SuggestedQuantity.Grams(22_700)),
            suggestion("Marraqueta", SuggestedQuantity.Grams(18_300)),
            suggestion("Hallulla", SuggestedQuantity.Grams(3_200)),
            suggestion("Berlín", SuggestedQuantity.Units(19), type = "Pastelería"),
            suggestion("Empanada de Pino", SuggestedQuantity.Units(19), type = "Pastelería"),
        )
        advanceUntilIdle()

        val card = assertNotNull(viewModel.uiState.value.suggestedProduction)
        assertEquals(
            listOf("Pan Amasado", "Marraqueta", "Berlín"),
            card.visibleGroups(expanded = false).flatMap { it.rows }.map { it.productName },
        )
        assertEquals(5, card.visibleGroups(expanded = true).flatMap { it.rows }.size)
        assertTrue(card.canCollapse)
    }

    @Test
    fun `a card that is short already has nothing to fold`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("Pan Amasado", SuggestedQuantity.Grams(22_700)),
            suggestion("Berlín", SuggestedQuantity.Units(19), type = "Pastelería"),
        )
        advanceUntilIdle()

        val card = assertNotNull(viewModel.uiState.value.suggestedProduction)
        assertFalse(card.canCollapse)
        assertEquals(2, card.visibleGroups(expanded = false).sumOf { it.rows.size })
    }

    @Test
    fun `the expander toggles and starts folded`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.suggestedExpanded)

        viewModel.onAction(BatchEntryAction.OnToggleSuggestedExpanded)
        assertTrue(viewModel.uiState.value.suggestedExpanded)

        viewModel.onAction(BatchEntryAction.OnToggleSuggestedExpanded)
        assertFalse(viewModel.uiState.value.suggestedExpanded)
    }

    @Test
    fun `the card names its estimator once and a row only when it differs`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("Pan Amasado", SuggestedQuantity.Grams(22_700)),
            suggestion("Marraqueta", SuggestedQuantity.Grams(18_300)),
            suggestion("Hallulla", SuggestedQuantity.Grams(3_200), modelVersion = "lgbm-favorita"),
        )
        advanceUntilIdle()

        val card = assertNotNull(viewModel.uiState.value.suggestedProduction)
        assertEquals(Estimator.BASELINE, card.estimator)
        assertEquals(mapOf("Pan Amasado" to false, "Marraqueta" to false, "Hallulla" to true), card.rows.associate { it.productName to it.showEstimator })
    }

    @Test
    fun `an all-baseline card shows no per-row estimator at all`() = runTest {
        suggestions.rows.value = listOf(suggestion("Pan Amasado", SuggestedQuantity.Grams(1)), suggestion("Marraqueta", SuggestedQuantity.Grams(2)))
        advanceUntilIdle()

        assertTrue(assertNotNull(viewModel.uiState.value.suggestedProduction).rows.none { it.showEstimator })
    }

    @Test
    fun `a tie between model and baseline is named baseline`() = runTest {
        suggestions.rows.value = listOf(
            suggestion("A", SuggestedQuantity.Grams(1), modelVersion = "lgbm-x"),
            suggestion("B", SuggestedQuantity.Grams(2)),
        )
        advanceUntilIdle()

        assertEquals(Estimator.BASELINE, assertNotNull(viewModel.uiState.value.suggestedProduction).estimator)
    }

    @Test
    fun `the card knows which day it is for, so the heading can name it`() = runTest {
        suggestions.rows.value = listOf(suggestion("Marraqueta", SuggestedQuantity.Grams(1)))
        advanceUntilIdle()

        assertEquals(tomorrow, assertNotNull(viewModel.uiState.value.suggestedProduction).day)
    }

    @Test
    fun `the last sync time reaches the state`() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.lastSyncedAt)

        val at = Instant.parse("2026-07-29T08:50:00Z")
        shell.setLastSyncedAt(at)
        advanceUntilIdle()

        assertEquals(at, viewModel.uiState.value.lastSyncedAt)
    }
}

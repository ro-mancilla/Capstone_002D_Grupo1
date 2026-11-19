@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.feature.production.screens.batchentry

import com.f776.ecopan.feature.shell.ShellContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.catalog.FormulaRepository
import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.production.BatchLogResult
import com.f776.ecopan.domain.production.LogBatch
import com.f776.ecopan.domain.production.NoDecrementReason
import com.f776.ecopan.domain.production.ProductionBatch
import com.f776.ecopan.domain.production.ProductionRepository
import com.f776.ecopan.domain.production.Estimator
import com.f776.ecopan.domain.production.SuggestedProduction
import com.f776.ecopan.domain.production.SuggestedQuantity
import com.f776.ecopan.domain.production.SuggestedProductionRepository
import com.f776.ecopan.domain.tenant.EntitlementsProvider
import com.f776.ecopan.feature.production.screens.formatAmount
import com.f776.ecopan.feature.production.screens.formatKg
import com.f776.ecopan.core.domain.formula.estimateLoaves
import com.f776.ecopan.feature.production.screens.batchentry.model.BatchRowUi
import com.f776.ecopan.feature.production.screens.batchentry.model.DerivedIngredientUi
import com.f776.ecopan.feature.production.screens.batchentry.model.LoggedBatchUi
import com.f776.ecopan.feature.production.screens.batchentry.model.ProductTileUi
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedGroupUi
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedProductionUi
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedUnit
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedRowUi
import com.f776.ecopan.feature.production.screens.batchentry.model.WeightValidation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

private const val MAX_MIX_GRAMS_HARD_CAP = 200_000L
private const val DEFAULT_SOFT_MIN_GRAMS = 500L
private const val DEFAULT_SOFT_MAX_GRAMS = 100_000L
/**
 * The timezone the backend buckets forecast days in (`reporting.tz` / `REPORTING_TZ`, default
 * America/Santiago); the web keeps the same constant in `reportingTime.ts`. A forecast's date is a
 * day in this zone, so "tomorrow" must be too, whatever the phone's own zone says.
 */
internal val REPORTING_TIME_ZONE: TimeZone = TimeZone.of("America/Santiago")

private const val MAX_WEIGHT_DIGITS = "999.9".length

/** A suggestion older than a day is a stale guess about a different day's bake: hidden, not dimmed. */
private val SUGGESTION_MAX_AGE = 1.days

/**
 * The baker's whole job in one screen (06 §2): product type → mix weight → confirm. Products,
 * their formula badge, and today's history are all live repository streams; the weight keypad and
 * derived-ingredient preview are plain view-model state driven by taps.
 */
internal class BatchEntryViewModel(
    private val productionRepository: ProductionRepository,
    private val productRepository: ProductRepository,
    private val formulaRepository: FormulaRepository,
    private val entitlementsProvider: EntitlementsProvider,
    private val shellContext: ShellContext,
    private val suggestedProductionRepository: SuggestedProductionRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = REPORTING_TIME_ZONE,
) : ViewModel() {

    private val _eventChannel = Channel<BatchEntryEvent>()
    val eventChannel = _eventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(BatchEntryUiState())
    val uiState = _uiState.asStateFlow()

    private var products: List<Product> = emptyList()

    /** Bumped on resume: a timer-free trigger to recompute "tomorrow" and staleness. */
    private val reevaluate = MutableStateFlow(0)
    private var previewJob: Job? = null

    private val shift = shellContext.shift
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activeBranchId = shift.map { it?.branchId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activeBakerId = shift.map { it?.userId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            // Production is Panadería-only (spec #22): a bought-finished product has no formula.
            productRepository.observeActiveProducts()
                .map { all -> all.filter { it.origin == ProductOrigin.PANADERIA } }
                .flatMapLatest { active ->
                    products = active
                    if (active.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(active.map { product -> product.formulaBadgeFlow() }) { it.toList() }
                    }
                }
                .collect { tiles -> _uiState.update { it.copy(loading = false, products = tiles) } }
        }
        viewModelScope.launch {
            entitlementsProvider.isModuleEnabled(EntitlementModule.INVENTORY_FORMULAS)
                .collect { enabled -> _uiState.update { it.copy(stockGlanceEnabled = enabled) } }
        }
        viewModelScope.launch {
            // Cached rows for tomorrow, so it reads offline. "Tomorrow" is the *reporting-zone* day
            // (the one the server keyed the forecast on), not the phone's. The day and the staleness
            // cut-off are taken when the cache emits, when this collector restarts, and on every
            // resume ([BatchEntryAction.OnResumed]) -- there is no timer, so a screen left in the
            // foreground across midnight keeps the old day until the next sync or resume.
            combine(
                entitlementsProvider.isModuleEnabled(EntitlementModule.DEMAND_FORECASTING),
                activeBranchId.filterNotNull(),
                reevaluate,
            ) { enabled, branchId, _ -> branchId.takeIf { enabled } }
                .flatMapLatest { branchId ->
                    if (branchId == null) {
                        flowOf(null)
                    } else {
                        val tomorrow = clock.now().toLocalDateTime(timeZone).date.plus(1, DateTimeUnit.DAY)
                        suggestedProductionRepository.observeForDay(branchId, tomorrow).map { it.toVisibleCard(tomorrow) }
                    }
                }
                .collect { card -> _uiState.update { it.copy(suggestedProduction = card) } }
        }
        viewModelScope.launch {
            activeBranchId.filterNotNull()
                .flatMapLatest { branchId -> productionRepository.todaysBatches(branchId) }
                // Joined against the live catalog, because `ProductionBatch.productTypeName` is
                // the product's *category* ("Pan", "Pastelería") -- it snapshots the type, not the
                // name, so the day's ledger listed three rows of "Pan" and a baker could not tell
                // a marraqueta batch from a hallulla one. The snapshot is still the fallback: it
                // is what survives when the product is later removed from the catalog, which is
                // the case it exists for.
                .combine(productRepository.observeActiveProducts()) { batches, catalog ->
                    val namesById = catalog.associate { it.id to it.name }
                    batches.sortedByDescending { it.loggedAt }
                        .map { batch -> batch.toRowUi(namesById[batch.productId] ?: batch.productTypeName) }
                }
                .collect { rows ->
                    val total = rows.sumOf { it.mixWeightGrams }
                    _uiState.update { it.copy(todaysBatches = rows, todaysTotalGrams = total) }
                }
        }
        viewModelScope.launch {
            shellContext.indicator.collect { indicator ->
                _uiState.update { it.copy(syncIndicator = indicator) }
            }
        }
        viewModelScope.launch {
            shellContext.lastSyncedAt.collect { at -> _uiState.update { it.copy(lastSyncedAt = at) } }
        }
        viewModelScope.launch {
            shellContext.shift.collect { shift ->
                _uiState.update { it.copy(tenantName = shift.tenantName) }
            }
        }
    }

    /** Rows older than [SUGGESTION_MAX_AGE] are dropped; if none are left the card is hidden. */
    private fun List<SuggestedProduction>.toVisibleCard(day: LocalDate): SuggestedProductionUi? {
        val now = clock.now()
        val fresh = filter { now - it.generatedAt <= SUGGESTION_MAX_AGE }
        if (fresh.isEmpty()) return null
        val cardEstimator = fresh.groupingBy { it.estimator }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Estimator, Int>> { it.value }.thenBy { it.key.tieRank })
            .first().key
        val groups = fresh
            .groupBy { it.productType to it.quantity.unit }
            .map { (key, rows) ->
                SuggestedGroupUi(
                    typeName = key.first,
                    unit = key.second,
                    rows = rows
                        .sortedWith(compareByDescending<SuggestedProduction> { it.quantity.magnitude }.thenBy { it.productName })
                        .map { SuggestedRowUi(it.productId, it.productName, it.quantity, it.estimator, showEstimator = it.estimator != cardEstimator) },
                )
            }
            .sortedWith(compareBy<SuggestedGroupUi> { it.unit }.thenBy { it.typeName })
        return SuggestedProductionUi(day = day, groups = groups, estimator = cardEstimator, updatedAt = fresh.maxOf { it.generatedAt })
    }

    private fun Product.formulaBadgeFlow() =
        formulaRepository.observeFormulaForProduct(id)
            .map { formula ->
                val lines = formula?.lines.orEmpty()
                ProductTileUi(id = id, name = name, hasFormula = lines.isNotEmpty(), materialCount = lines.size)
            }

    fun onAction(action: BatchEntryAction) {
        when (action) {
            is BatchEntryAction.OnSelectProduct -> selectProduct(action.productId)
            is BatchEntryAction.OnKeypadDigit -> onKeypadDigit(action.digit)
            BatchEntryAction.OnKeypadBackspace -> onKeypadBackspace()
            BatchEntryAction.OnConfirmRequested -> onConfirmRequested()
            BatchEntryAction.OnConfirm -> onConfirm()
            BatchEntryAction.OnDismissLogged -> _uiState.update { it.copy(lastLogged = null) }
            BatchEntryAction.OnSyncIndicatorTapped -> viewModelScope.launch { shellContext.syncNow() }
            BatchEntryAction.OnToggleSuggestedExpanded -> _uiState.update { it.copy(suggestedExpanded = !it.suggestedExpanded) }
            BatchEntryAction.OnResumed -> {
                reevaluate.update { it + 1 }
                // Coming back to the app is the moment the baker expects fresh server data; waiting
                // for the 5-minute tick left adjustments invisible. The engine single-flights.
                viewModelScope.launch { shellContext.syncNow() }
            }
        }
    }

    private fun selectProduct(productId: Uuid) {
        _uiState.update { it.copy(selectedProductId = productId, confirmArmed = false) }
        recomputePreview()
    }

    private fun onKeypadDigit(digit: Char) {
        val current = _uiState.value.weightInput
        if (digit == '.' && current.contains('.')) return
        if (current.length >= MAX_WEIGHT_DIGITS) return
        setWeightInput(current + digit)
    }

    private fun onKeypadBackspace() {
        setWeightInput(_uiState.value.weightInput.dropLast(1))
    }

    private fun setWeightInput(text: String) {
        val mixGrams = text.toDoubleOrNull()?.let { kg -> kotlin.math.round(kg * 1000).toLong() }
        val validation = validate(mixGrams)
        _uiState.update {
            it.copy(
                weightInput = text,
                mixWeightGrams = mixGrams,
                validation = validation,
                confirmArmed = false,
                estimatedUnits = mixGrams?.let { grams -> estimatedUnits(grams) },
            )
        }
        recomputePreview()
    }

    private fun validate(mixGrams: Long?): WeightValidation {
        if (mixGrams == null) return WeightValidation.EMPTY
        if (mixGrams <= 0 || mixGrams > MAX_MIX_GRAMS_HARD_CAP) return WeightValidation.HARD_INVALID
        val product = selectedProduct()
        val min = product?.minMixGrams ?: DEFAULT_SOFT_MIN_GRAMS
        val max = product?.maxMixGrams ?: DEFAULT_SOFT_MAX_GRAMS
        return if (mixGrams < min || mixGrams > max) WeightValidation.OUT_OF_SOFT_BOUNDS else WeightValidation.OK
    }

    private fun estimatedUnits(mixGrams: Long): Int? =
        estimateLoaves(mixGrams, selectedProduct()?.mixToLoafRatioMilli)

    private fun selectedProduct(): Product? =
        products.firstOrNull { it.id == _uiState.value.selectedProductId }

    private fun recomputePreview() {
        previewJob?.cancel()
        val productId = _uiState.value.selectedProductId
        val mixGrams = _uiState.value.mixWeightGrams
        if (productId == null || mixGrams == null || mixGrams <= 0) {
            _uiState.update { it.copy(derivedPreview = null) }
            return
        }
        previewJob = viewModelScope.launch {
            val derived = productionRepository.previewDerivation(productId, mixGrams)
            _uiState.update {
                it.copy(
                    derivedPreview = derived?.map { d ->
                        DerivedIngredientUi(ingredientId = d.ingredientId, name = d.ingredientName, label = formatAmount(d.grams))
                    }
                )
            }
        }
    }

    private fun onConfirmRequested() {
        val state = _uiState.value
        if (state.validation == WeightValidation.HARD_INVALID || state.selectedProductId == null) return
        if (state.validation == WeightValidation.OUT_OF_SOFT_BOUNDS && !state.confirmArmed) {
            _uiState.update { it.copy(confirmArmed = true) }
            return
        }
        onConfirm()
    }

    private fun onConfirm() {
        val state = _uiState.value
        val productId = state.selectedProductId ?: return
        val mixGrams = state.mixWeightGrams ?: return
        if (state.validation == WeightValidation.HARD_INVALID) return
        if (state.confirmInFlight) return
        val branchId = activeBranchId.value ?: return
        val bakerId = activeBakerId.value ?: return
        val product = selectedProduct() ?: return

        _uiState.update { it.copy(confirmInFlight = true) }
        viewModelScope.launch {
            val result = productionRepository.logBatch(
                LogBatch(branchId = branchId, bakerId = bakerId, productId = productId, mixWeightGrams = mixGrams)
            )
            val kgLabel = formatAmount(mixGrams)
            val logged = LoggedBatchUi(
                productName = product.name,
                timeLabel = nowTimeLabel(),
                kgLabel = kgLabel,
                // Read from the write, not from `derivedPreview`: the preview is recomputed on
                // every keystroke and is not cleared while in flight, so confirming straight
                // after the last digit could report the previous weight's amounts. `logBatch`
                // returns the movements it wrote in the same transaction — use those.
                decremented = (result as? BatchLogResult.Logged)
                    ?.derivedMovements
                    .orEmpty()
                    .map { d ->
                        DerivedIngredientUi(
                            ingredientId = d.ingredientId,
                            name = d.ingredientName,
                            label = formatAmount(d.grams),
                        )
                    },
            )

            // Selection persists — bakers batch the same product repeatedly (06 §7.1). Reset the
            // state before the event send below, which suspends until the Root's collector picks
            // it up — the reset must not wait behind that handshake.
            _uiState.update {
                it.copy(
                    weightInput = "",
                    mixWeightGrams = null,
                    validation = WeightValidation.EMPTY,
                    derivedPreview = null,
                    estimatedUnits = null,
                    confirmArmed = false,
                    confirmInFlight = false,
                    lastLogged = logged,
                )
            }
            when (result) {
                is BatchLogResult.Logged -> _eventChannel.send(BatchEntryEvent.BatchLogged(product.name, kgLabel))
                is BatchLogResult.LoggedWithoutDecrement -> when (result.reason) {
                    NoDecrementReason.NO_FORMULA, NoDecrementReason.MODULE_DISABLED ->
                        _eventChannel.send(BatchEntryEvent.BatchLoggedNoDecrement(product.name, kgLabel))
                }
            }
        }
    }

    private fun ProductionBatch.toRowUi(displayName: String): BatchRowUi {
        val local = loggedAt.toLocalDateTime(TimeZone.currentSystemDefault())
        return BatchRowUi(
            id = id,
            timeLabel = local.timeLabel(),
            productName = displayName,
            mixWeightGrams = mixWeightGrams,
            syncState = syncState,
        )
    }
}

private fun nowTimeLabel(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).timeLabel()

private fun LocalDateTime.timeLabel(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** On a tie the card names the most conservative claim: a baseline never reads as the model. */
private val Estimator.tieRank: Int
    get() = when (this) {
        Estimator.BASELINE -> 0
        Estimator.UNKNOWN -> 1
        Estimator.MODEL -> 2
    }

private val SuggestedQuantity.unit: SuggestedUnit
    get() = when (this) {
        is SuggestedQuantity.Grams -> SuggestedUnit.KILOGRAMS
        is SuggestedQuantity.Units -> SuggestedUnit.UNITS
    }

private val SuggestedQuantity.magnitude: Long
    get() = when (this) {
        is SuggestedQuantity.Grams -> grams
        is SuggestedQuantity.Units -> units.toLong()
    }

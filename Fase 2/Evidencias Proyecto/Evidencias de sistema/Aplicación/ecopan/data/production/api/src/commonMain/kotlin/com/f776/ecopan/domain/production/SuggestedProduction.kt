package com.f776.ecopan.domain.production

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The expected sales of one Panadería product for one day, in its sales unit (HU-PRE-0001's
 * *producción sugerida*). Not dough weight and not an automatic batch: it is a figure the baker
 * reads before deciding what to mix.
 */
data class SuggestedProduction(
    val id: Uuid,
    val branchId: Uuid,
    val productId: Uuid,
    val productName: String,
    val productType: String,
    val forecastDate: LocalDate,
    val quantity: SuggestedQuantity,
    val estimator: Estimator,
    /** When the server produced this figure; staleness is judged on it. */
    val generatedAt: Instant,
)

/** A forecast is in grams (by-weight products) or whole units (by-unit products), never both. */
sealed interface SuggestedQuantity {
    data class Grams(val grams: Long) : SuggestedQuantity
    data class Units(val units: Int) : SuggestedQuantity
}

/**
 * What produced a figure, read from the server's `model_version` alone: `lgbm-*` is the trained
 * model, `baseline-*` a labelled baseline. Anything else (including a missing version) is
 * [UNKNOWN] -- never promoted to [MODEL]. Whether a baseline is there for lack of history or
 * because the model does not cover the series is the server's business; the label claims neither.
 */
enum class Estimator {
    MODEL,
    BASELINE,
    UNKNOWN;

    companion object {
        fun fromModelVersion(modelVersion: String?): Estimator = when {
            modelVersion == null -> UNKNOWN
            modelVersion.startsWith("lgbm-") -> MODEL
            modelVersion.startsWith("baseline-") -> BASELINE
            else -> UNKNOWN
        }
    }
}

interface SuggestedProductionRepository {
    /**
     * The cached suggestions for [day] in [branchId], by product name. Read from the local cache,
     * so it works offline; the rows arrive through sync pull. Empty when none are cached --
     * which is also what the cache holds before the first forecast refresh or while the tenant
     * has `DEMAND_FORECASTING` off (the server sends none then).
     */
    fun observeForDay(branchId: Uuid, day: LocalDate): Flow<List<SuggestedProduction>>
}

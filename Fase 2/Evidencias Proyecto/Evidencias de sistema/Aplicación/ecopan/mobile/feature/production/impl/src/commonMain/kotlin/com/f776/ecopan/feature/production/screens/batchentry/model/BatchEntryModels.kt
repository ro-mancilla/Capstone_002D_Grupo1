package com.f776.ecopan.feature.production.screens.batchentry.model

import com.f776.ecopan.domain.production.BatchSyncState
import com.f776.ecopan.domain.production.Estimator
import com.f776.ecopan.domain.production.SuggestedQuantity
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal data class ProductTileUi(
    val id: Uuid,
    val name: String,
    val hasFormula: Boolean,
    /** Formula line count, shown under the picker as "· 4 materias primas". 0 when [hasFormula]. */
    val materialCount: Int = 0,
)

internal data class DerivedIngredientUi(
    val ingredientId: Uuid,
    val name: String,
    val label: String,
)

internal data class BatchRowUi(
    val id: Uuid,
    val timeLabel: String,
    val productName: String,
    /** Grams, not a label: the "Hoy" header sums these, and a formatted string cannot be summed. */
    val mixWeightGrams: Long,
    val syncState: BatchSyncState,
)

/**
 * The inline confirmation that replaces the batch-logged snackbar. A baker logging a mix is
 * usually looking at the dough, not the phone — a snackbar times out unseen, so the receipt of
 * what was just recorded (and what it took out of stock) stays on screen until dismissed or
 * superseded by the next batch.
 */
internal data class LoggedBatchUi(
    val productName: String,
    val timeLabel: String,
    val kgLabel: String,
    /** Empty when the product has no formula — nothing was decremented, and the note says so. */
    val decremented: List<DerivedIngredientUi>,
)

/** Hard-invalid weight disables confirm outright (06 §9); soft-out-of-bounds demands the 2-tap arm. */
internal enum class WeightValidation { EMPTY, HARD_INVALID, OUT_OF_SOFT_BOUNDS, OK }

/** One line of *Producción sugerida para mañana*: a product, tomorrow's expected sales, who said so. */
internal data class SuggestedRowUi(
    val productId: Uuid,
    val productName: String,
    val quantity: SuggestedQuantity,
    val estimator: Estimator,
    /**
     * True only when this row's estimator differs from the card's own ([SuggestedProductionUi.estimator]).
     * The card names the estimator once for everyone; a row repeats it only to say "this one is
     * different" (copy deck: "por fila solo si difiere").
     */
    val showEstimator: Boolean = false,
)

/** The unit a group of rows is read in; its heading reads "Pan · kg" or "Pastelería · unidades". */
internal enum class SuggestedUnit { KILOGRAMS, UNITS }

/** Rows of one product type in one unit, largest first. [typeName] is empty when the catalog has none. */
internal data class SuggestedGroupUi(
    val typeName: String,
    val unit: SuggestedUnit,
    val rows: List<SuggestedRowUi>,
)

/**
 * The baker's suggested-production card. Absent from the state (null) when the module is off,
 * nothing is cached, or everything cached is stale -- there is no "empty" card, because a card
 * saying "no suggestions" on a bakery that never enabled forecasting is noise.
 */
internal data class SuggestedProductionUi(
    /** The reporting-zone day the figures are for: the card's heading names it. */
    val day: LocalDate,
    val groups: List<SuggestedGroupUi>,
    /** What produced most of the rows; named once under the heading. */
    val estimator: Estimator,
    /** The newest `generatedAt` among the rows. */
    val updatedAt: Instant,
) {
    val rows: List<SuggestedRowUi> get() = groups.flatMap { it.rows }
    val totalCount: Int get() = groups.sumOf { it.rows.size }

    /**
     * The folded card: the first [COLLAPSED_FIRST_GROUP_ROWS] rows of the first group and one row
     * of each later group -- enough to see both units and the biggest figure, short enough to leave
     * the product chips on screen.
     */
    val collapsedGroups: List<SuggestedGroupUi>
        get() = groups.mapIndexed { index, group ->
            group.copy(rows = group.rows.take(if (index == 0) COLLAPSED_FIRST_GROUP_ROWS else 1))
        }

    /** False when folding would hide nothing, so the expander would be a button that does nothing. */
    val canCollapse: Boolean get() = collapsedGroups.sumOf { it.rows.size } < totalCount

    fun visibleGroups(expanded: Boolean): List<SuggestedGroupUi> = if (expanded || !canCollapse) groups else collapsedGroups

    private companion object {
        const val COLLAPSED_FIRST_GROUP_ROWS = 2
    }
}

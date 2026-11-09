package com.f776.ecopan.feature.production.screens.stockglance

import com.f776.core.designsystem.component.SyncIndicatorState
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Where a balance stands. Ingredients have no "agotado": zero is simply [LOW]. */
internal enum class StockStatus {
    /** Below zero: the ledger and the shelf disagree. "Revisar saldo". */
    NEGATIVE,

    /** At or under the reorder point. "Stock bajo". */
    LOW,

    OK,
}

internal data class StockRowUi(
    val ingredientId: Uuid,
    val name: String,
    val currentGrams: Long,
    /**
     * The ingredient's reorder point, shown as "Reponer en 3 kg". Null when none is configured:
     * "Sin punto de reposición", and the ingredient is then never flagged low -- a bare number
     * tells the baker nothing about whether it is enough, and the reorder point is the only thing
     * in the domain that answers that.
     */
    val lowStockThresholdGrams: Long? = null,
) {
    val negative: Boolean get() = currentGrams < 0

    val status: StockStatus
        get() = when {
            currentGrams < 0 -> StockStatus.NEGATIVE
            lowStockThresholdGrams != null && currentGrams <= lowStockThresholdGrams -> StockStatus.LOW
            else -> StockStatus.OK
        }
}

internal data class StockGlanceUiState(
    val loading: Boolean = true,
    /** Every active ingredient, by name. The screen reads [toReplenish] and [inStock] instead. */
    val rows: List<StockRowUi> = emptyList(),
    /** 06 §7.3: "Same SyncStatus header" as the batch-entry screen (03 §6.1). */
    val syncIndicator: SyncIndicatorState = SyncIndicatorState.OnlineIdle,
    val tenantName: String = "",
    /** When the device last heard from the server; the subtitle and the offline line print it. */
    val lastSyncedAt: Instant? = null,
) {
    val negativeRows: List<StockRowUi> get() = rows.filter { it.status == StockStatus.NEGATIVE }

    /** Drives the alert at the top: how many ingredients the ledger has driven below zero. */
    val negativeCount: Int get() = negativeRows.size

    /** Negative and low balances, the most urgent (smallest) first. */
    val toReplenish: List<StockRowUi>
        get() = rows.filter { it.status != StockStatus.OK }.sortedWith(compareBy({ it.currentGrams }, { it.name }))

    /** Everything else, the fullest shelf first. */
    val inStock: List<StockRowUi>
        get() = rows.filter { it.status == StockStatus.OK }.sortedWith(compareBy({ -it.currentGrams }, { it.name }))

    /** Offline is normal for this app; it is a fact to state, never an error. */
    val offline: Boolean get() = syncIndicator is SyncIndicatorState.OfflinePending
}

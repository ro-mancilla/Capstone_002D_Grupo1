package com.f776.ecopan.feature.production.screens.batchentry

import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.feature.production.screens.batchentry.model.BatchRowUi
import com.f776.ecopan.feature.production.screens.batchentry.model.DerivedIngredientUi
import com.f776.ecopan.feature.production.screens.batchentry.model.LoggedBatchUi
import com.f776.ecopan.feature.production.screens.batchentry.model.ProductTileUi
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedProductionUi
import com.f776.ecopan.feature.production.screens.batchentry.model.WeightValidation
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal data class BatchEntryUiState(
    val loading: Boolean = true,
    val products: List<ProductTileUi> = emptyList(),
    val selectedProductId: Uuid? = null,
    val weightInput: String = "",
    val mixWeightGrams: Long? = null,
    val validation: WeightValidation = WeightValidation.EMPTY,
    val derivedPreview: List<DerivedIngredientUi>? = null,
    val estimatedUnits: Int? = null,
    val confirmArmed: Boolean = false,
    val confirmInFlight: Boolean = false,
    val todaysBatches: List<BatchRowUi> = emptyList(),
    /** Sum of [todaysBatches], shown against the "Hoy" heading so the day has a running figure. */
    val todaysTotalGrams: Long = 0,
    /** The last batch logged in this session, held on screen until dismissed. Null before the first. */
    val lastLogged: LoggedBatchUi? = null,
    val stockGlanceEnabled: Boolean = false,
    /** Tomorrow's suggested production; null = show nothing (see [SuggestedProductionUi]). */
    val suggestedProduction: SuggestedProductionUi? = null,
    /** Whether the folded card is opened out to every product. Not remembered across visits. */
    val suggestedExpanded: Boolean = false,
    /** When the device last heard from the server: the offline line under the card's heading. */
    val lastSyncedAt: Instant? = null,
    /** 03 §6.1 -- must be visible at all times on the baker's logging screen, same as POS. */
    val syncIndicator: SyncIndicatorState = SyncIndicatorState.OnlineIdle,
    /** Shown beside the indicator per 06 §7's header mock. Display only -- there is no tenant
     *  switcher on the baker client (a device is enrolled to exactly one tenant, 01 §4). */
    val tenantName: String = "",
) {
    val selectedProduct: ProductTileUi?
        get() = products.firstOrNull { it.id == selectedProductId }
}

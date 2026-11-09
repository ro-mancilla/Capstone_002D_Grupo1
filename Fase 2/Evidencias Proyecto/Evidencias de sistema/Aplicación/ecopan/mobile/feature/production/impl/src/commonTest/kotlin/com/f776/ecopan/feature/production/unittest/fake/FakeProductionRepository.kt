package com.f776.ecopan.feature.production.unittest.fake

import com.f776.ecopan.domain.production.BatchLogResult
import com.f776.ecopan.domain.production.BatchSyncState
import com.f776.ecopan.domain.production.DerivedIngredient
import com.f776.ecopan.domain.production.LogBatch
import com.f776.ecopan.domain.production.NoDecrementReason
import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import com.f776.ecopan.domain.production.ProductionBatch
import com.f776.ecopan.domain.production.ProductionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.uuid.Uuid

class FakeProductionRepository : ProductionRepository {
    val batches = MutableStateFlow<List<ProductionBatch>>(emptyList())
    private val readyFeed = MutableSharedFlow<BatchReadyAnnouncement>()

    /** productId -> preview result (null = no formula / module off). */
    var previews: Map<Uuid, List<DerivedIngredient>> = emptyMap()

    /**
     * productId -> what `logBatch` actually wrote, when a test needs it to differ from the
     * preview. Absent means the write agrees with [previews], which is the ordinary case.
     */
    var loggedDerivations: Map<Uuid, List<DerivedIngredient>> = emptyMap()

    /** productId -> reason when no formula/module disabled; absent = formula applies. */
    var noDecrementReasons: Map<Uuid, NoDecrementReason> = emptyMap()

    val loggedCommands = mutableListOf<LogBatch>()

    override fun todaysBatches(branchId: Uuid) = batches

    override fun batchReadyFeed() = readyFeed

    override suspend fun logBatch(cmd: LogBatch): BatchLogResult {
        loggedCommands += cmd
        val batch = ProductionBatch(
            id = Uuid.random(),
            tenantId = Uuid.random(),
            branchId = cmd.branchId,
            bakerId = cmd.bakerId,
            productId = cmd.productId,
            productTypeName = "Product",
            mixWeightGrams = cmd.mixWeightGrams,
            estimatedUnits = null,
            loggedAt = Clock.System.now(),
            syncState = BatchSyncState.PENDING,
        )
        batches.value = batches.value + batch
        val reason = noDecrementReasons[cmd.productId]
        return if (reason != null) {
            BatchLogResult.LoggedWithoutDecrement(batch, reason)
        } else {
            BatchLogResult.Logged(batch, (loggedDerivations[cmd.productId] ?: previews[cmd.productId]).orEmpty())
        }
    }

    override suspend fun previewDerivation(productId: Uuid, mixWeightGrams: Long): List<DerivedIngredient>? =
        if (noDecrementReasons[productId] != null) null else previews[productId]
}

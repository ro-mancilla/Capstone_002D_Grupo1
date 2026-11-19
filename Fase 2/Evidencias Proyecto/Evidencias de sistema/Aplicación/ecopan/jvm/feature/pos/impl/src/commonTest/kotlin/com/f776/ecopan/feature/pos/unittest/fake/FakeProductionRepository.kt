package com.f776.ecopan.feature.pos.unittest.fake

import com.f776.ecopan.domain.production.BatchLogResult
import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import com.f776.ecopan.domain.production.DerivedIngredient
import com.f776.ecopan.domain.production.LogBatch
import com.f776.ecopan.domain.production.ProductionBatch
import com.f776.ecopan.domain.production.ProductionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal class FakeProductionRepository : ProductionRepository {
    private val batchReady = MutableSharedFlow<BatchReadyAnnouncement>(extraBufferCapacity = 16)
    private val batches = MutableStateFlow<List<ProductionBatch>>(emptyList())

    override fun todaysBatches(branchId: Uuid): Flow<List<ProductionBatch>> = batches

    override fun batchReadyFeed(): Flow<BatchReadyAnnouncement> = batchReady

    override suspend fun logBatch(cmd: LogBatch): BatchLogResult {
        error("not needed by PosViewModelTest")
    }

    override suspend fun previewDerivation(productId: Uuid, mixWeightGrams: Long): List<DerivedIngredient>? {
        error("not needed by PosViewModelTest")
    }

    /** Test hook: simulate a batch-ready announcement arriving (local log, SSE, or pull applier). */
    suspend fun emitBatchReady(announcement: BatchReadyAnnouncement) {
        batchReady.emit(announcement)
    }
}

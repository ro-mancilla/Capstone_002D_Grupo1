package com.f776.ecopan.domain.production

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface ProductionRepository {
    fun todaysBatches(branchId: Uuid): Flow<List<ProductionBatch>>

    /**
     * Batches this device logged **and** batches from other devices (06 §6). Remote ones arrive
     * over SSE while the device is online ([BatchReadyStream]), and otherwise on the next pull
     * cycle — pull is the source of truth, SSE only shortens latency. Each batch is announced at
     * most once no matter how many of those paths deliver it.
     */
    fun batchReadyFeed(): Flow<BatchReadyAnnouncement>

    /** Atomic: batch + derived movements (if any) + their outbox rows in one local transaction. */
    suspend fun logBatch(cmd: LogBatch): BatchLogResult

    /** Null = no formula for [productId] / an invalid formula / `INVENTORY_FORMULAS` disabled. */
    suspend fun previewDerivation(productId: Uuid, mixWeightGrams: Long): List<DerivedIngredient>?
}

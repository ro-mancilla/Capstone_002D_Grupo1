@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.production

import com.f776.ecopan.core.domain.formula.estimateLoaves
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.production.domain.FormulaPreview
import com.f776.ecopan.data.production.local.mapper.toDomain
import com.f776.ecopan.data.production.local.mapper.toEntity
import com.f776.ecopan.data.production.sideeffects.FormulaDecrementSideEffect
import com.f776.ecopan.data.production.sideeffects.NoOpBatchSideEffects
import com.f776.ecopan.data.production.wire.toPushPayload
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.production.BatchLogResult
import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import com.f776.ecopan.domain.production.BatchSyncState
import com.f776.ecopan.domain.production.DerivedIngredient
import com.f776.ecopan.domain.production.LogBatch
import com.f776.ecopan.domain.production.ProductionBatch
import com.f776.ecopan.domain.production.ProductionRepository
import com.f776.ecopan.domain.production.SideEffectOutcome
import com.f776.ecopan.domain.sync.outboxRow
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.EntitlementsProvider
import com.f776.unigrid.core.database.dao.OutboxDao
import com.f776.unigrid.core.database.dao.ProductionBatchDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * [batchReadyFeed] delegates to the shared [BatchReadyBus], so it carries both this device's own
 * `logBatch` inserts and batches pulled from other devices via `ProductionBatchSyncApplier`
 * (06 §6). The remaining unbuilt half is *push* latency, not coverage: there is still no SSE
 * client, so a remote batch surfaces on the next sync cycle rather than instantly.
 */
internal class ProductionRepositoryImpl(
    private val productionBatchDao: ProductionBatchDao,
    private val outboxDao: OutboxDao,
    private val productRepository: ProductRepository,
    private val formulaPreview: FormulaPreview,
    private val formulaDecrementSideEffect: FormulaDecrementSideEffect,
    private val noOpBatchSideEffects: NoOpBatchSideEffects,
    private val entitlementsProvider: EntitlementsProvider,
    private val batchReadyBus: BatchReadyBus,
) : ProductionRepository {

    override fun todaysBatches(branchId: Uuid): Flow<List<ProductionBatch>> {
        val (startMillis, endMillis) = todayRangeMillis()
        return combine(
            productionBatchDao.observeTodaysBatches(branchId, startMillis, endMillis),
            outboxDao.observePendingMutationIds(),
            outboxDao.observeFailedMutationIds(),
        ) { batches, pendingIds, rejectedIds ->
            val pending = pendingIds.toSet()
            val rejected = rejectedIds.toSet()
            batches.map { batch ->
                batch.toDomain(
                    when (batch.id) {
                        in pending -> BatchSyncState.PENDING
                        // Checked second and separately: `observePendingMutationIds` deliberately
                        // excludes FAILED, so "not pending" alone does not mean "accepted".
                        in rejected -> BatchSyncState.REJECTED
                        else -> BatchSyncState.SYNCED
                    }
                )
            }
        }
    }

    override fun batchReadyFeed(): Flow<BatchReadyAnnouncement> = batchReadyBus.feed

    override suspend fun logBatch(cmd: LogBatch): BatchLogResult {
        val product = requireNotNull(productRepository.findById(cmd.productId)) {
            "logBatch called with an unknown productId ${cmd.productId} — a ViewModel bug, not a user-reachable state"
        }
        val batch = ProductionBatch(
            id = Uuid.random(),
            tenantId = product.tenantId,
            branchId = cmd.branchId,
            bakerId = cmd.bakerId,
            productId = cmd.productId,
            productTypeName = product.productType,
            mixWeightGrams = cmd.mixWeightGrams,
            estimatedUnits = estimateLoaves(cmd.mixWeightGrams, product.mixToLoafRatioMilli),
            loggedAt = Clock.System.now(),
            syncState = BatchSyncState.PENDING,
        )

        val entitled = entitlementsProvider.isModuleEnabled(EntitlementModule.INVENTORY_FORMULAS).first()
        val outcome = if (entitled) formulaDecrementSideEffect.computeMovements(batch) else noOpBatchSideEffects.computeMovements(batch)
        val movements = (outcome as? SideEffectOutcome.Applied)?.movements.orEmpty()

        // One timestamp for the whole write, so the batch and its movements queue together
        // rather than straddling a tick.
        val now = Clock.System.now()
        val outboxRows = buildList {
            add(
                outboxRow(
                    mutationId = batch.id,
                    entityType = SyncEntityType.PRODUCTION_BATCH,
                    opType = SyncOpType.CREATE,
                    payloadJson = Json.encodeToString(batch.toPushPayload()),
                    createdAt = now,
                ),
            )
            movements.forEach { movement ->
                add(
                    outboxRow(
                        mutationId = movement.id,
                        entityType = SyncEntityType.STOCK_MOVEMENT,
                        opType = SyncOpType.CREATE,
                        payloadJson = Json.encodeToString(movement.toPushPayload()),
                        createdAt = now,
                    ),
                )
            }
        }

        productionBatchDao.logBatch(batch.toEntity(), movements.map { it.toEntity() }, outboxRows)
        batchReadyBus.publish(
            BatchReadyAnnouncement(
                batchId = batch.id,
                branchId = batch.branchId,
                productId = batch.productId,
                productTypeName = batch.productTypeName,
                mixWeightGrams = batch.mixWeightGrams,
                readyAt = batch.loggedAt,
            ),
        )

        return when (outcome) {
            is SideEffectOutcome.Applied -> BatchLogResult.Logged(batch, outcome.derived)
            is SideEffectOutcome.Skipped -> BatchLogResult.LoggedWithoutDecrement(batch, outcome.reason)
        }
    }

    override suspend fun previewDerivation(productId: Uuid, mixWeightGrams: Long): List<DerivedIngredient>? {
        if (!entitlementsProvider.isModuleEnabled(EntitlementModule.INVENTORY_FORMULAS).first()) return null
        return formulaPreview.derive(productId, mixWeightGrams)
    }

    private fun todayRangeMillis(): Pair<Long, Long> {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val startOfDay = LocalDateTime(today, LocalTime(0, 0)).toInstant(zone)
        val startOfNextDay = startOfDay.plus(1, DateTimeUnit.DAY, zone)
        return startOfDay.toEpochMilliseconds() to startOfNextDay.toEpochMilliseconds()
    }
}

package com.f776.ecopan.data.production.sync

import com.f776.ecopan.core.domain.formula.estimateLoaves
import com.f776.ecopan.data.production.BatchReadyBus
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.unigrid.core.database.dao.ProductionBatchDao
import com.f776.unigrid.core.database.entity.ProductionBatchEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Pull-side applier for `PRODUCTION_BATCH` (03 §4.3 family 2 — append-only fact: insert if unseen
 * by client-minted UUID, never update, never delete). This is what makes 06 §6's cross-device half
 * real: a batch logged on a baker's phone lands in every other device's local `production_batches`
 * cache on their next pull, and is published to [BatchReadyBus] so POS's batch-ready banner
 * (05 §3 item 9) shows batches from *other* devices, not just locally-logged ones.
 *
 * **This is the durable path, not the fast one.** Being pull-driven it lands on the next sync cycle
 * (03 §7's cadence); `BatchReadySseListener` usually announces the same batch sooner over SSE.
 * That listener writes nothing, though — only this applier produces the row, and it is the only
 * path that works at all when the device was offline when the batch was logged.
 *
 * **Two fields aren't on the wire** (see the backend's `ProductionBatchPullPayload`), both
 * resolved from the local catalog cache the same way `logBatch` derives them:
 * - `productTypeName` — a denormalized display snapshot. If the product isn't in this device's
 *   cache the row is still inserted (dropping a real fact would silently corrupt history) but
 *   with an empty name and **no banner** — a banner naming nothing is worse than no banner, and
 *   the next config pull backfills the product so the history row reads correctly from then on.
 * - `estimatedUnits` — informational only (06 §2: "displayed, never ledgered"), recomputed from
 *   the product's `mixToLoafRatioMilli` exactly as `ProductionRepositoryImpl.logBatch` does.
 *
 * `tenantId` isn't on the wire either (it's the JWT principal on push, 02 §4.1) and is resolved
 * from [TenantRepository], relying on 01 §4's one-tenant-per-device invariant — identical to
 * `StockMovementSyncApplier`, including the "no cached tenant yet" no-op.
 *
 * Public (not `internal`) for the same reason the other two real appliers are: `:data:sync:impl`'s
 * cross-device tests construct it directly. Production wiring reaches it only through Koin's
 * `getAll<SyncApplier>()` (see [SyncApplier]'s KDoc).
 */
class ProductionBatchSyncApplier(
    private val productionBatchDao: ProductionBatchDao,
    private val productRepository: ProductRepository,
    private val tenantRepository: TenantRepository,
    private val batchReadyBus: BatchReadyBus,
) : SyncApplier {

    override val entityType: SyncEntityType = SyncEntityType.PRODUCTION_BATCH

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val tenantId = tenantRepository.observeTenant().first()?.id ?: return
        val wire = json.decodeFromJsonElement<ProductionBatchPullPayload>(payload)
        val batchId = Uuid.parse(wire.id)

        // Checked before the insert so a re-pulled page (03 §4.1) or this device's own echo can't
        // re-fire the banner. Runs inside PullWorker's ambient per-page transaction; the insert
        // stays IGNORE anyway, so the worst a lost race could cost is a duplicate banner, never a
        // duplicate row.
        if (productionBatchDao.exists(batchId)) return

        val product = productRepository.findById(Uuid.parse(wire.productId))

        val entity = ProductionBatchEntity(
            id = batchId,
            tenantId = tenantId,
            branchId = Uuid.parse(wire.branchId),
            bakerId = Uuid.parse(wire.bakerId),
            productId = Uuid.parse(wire.productId),
            productTypeName = product?.productType.orEmpty(),
            mixWeightGrams = wire.mixWeightGrams,
            estimatedUnits = estimateLoaves(wire.mixWeightGrams, product?.mixToLoafRatioMilli),
            loggedAt = Instant.fromEpochMilliseconds(wire.batchAt),
        )
        productionBatchDao.insertIfUnseen(entity)

        // Usually a no-op in practice: while the device is online `BatchReadySseListener` has
        // already announced this batch, and the bus de-duplicates by id. This path is what makes
        // the banner still appear when SSE was down or the device was offline when it was logged.
        if (product != null) {
            batchReadyBus.publish(
                BatchReadyAnnouncement(
                    batchId = entity.id,
                    branchId = entity.branchId,
                    productId = entity.productId,
                    productTypeName = entity.productTypeName,
                    mixWeightGrams = entity.mixWeightGrams,
                    readyAt = entity.loggedAt,
                ),
            )
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Mirrors the backend's `ProductionBatchPullPayload` exactly (`batchAt` is epoch millis on the
 * wire, `decrementStatus` is server-written and ignored client-side -- the client already holds
 * the movements it minted itself, 06 §4).
 */
@Serializable
private data class ProductionBatchPullPayload(
    val id: String,
    val branchId: String,
    val bakerId: String,
    val productId: String,
    val mixWeightGrams: Long,
    val batchAt: Long,
    val movementsMinted: Boolean = true,
    val decrementStatus: String? = null,
)

package com.f776.ecopan.data.production.sse

import com.f776.ecopan.data.production.BatchReadyBus
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import com.f776.ecopan.domain.production.BatchReadyStream
import com.f776.ecopan.domain.sync.SyncEventStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Turns the backend's `batch-ready` SSE frames (02 §5) into [BatchReadyBus] announcements, so POS's
 * banner (05 §3 item 9) appears when a baker logs a batch rather than on POS's next sync cycle.
 * The last 24 h replayed on every (re)connect arrive as `batch-snapshot` (#74) and are handled the
 * same way: [BatchReadyBus]'s id de-duplication keeps an already-announced batch quiet, while one
 * logged during the disconnect is announced -- which is what the replay exists for.
 *
 * **Announce-only — it deliberately writes nothing to the database.** The SSE payload
 * (`NotificationProcessorImpl.BatchReadyPayload`) has no `bakerId` and no `tenantId`, both of which
 * `ProductionBatchEntity` requires, so persisting from here would mean inventing them. The durable
 * row arrives moments later through the pull feed and `ProductionBatchSyncApplier`, which has the
 * full payload; [BatchReadyBus]'s id de-duplication is what stops that second arrival from
 * announcing the same batch twice. Pull stays the source of truth, SSE only shortens latency —
 * exactly the division [SyncEventStream]'s KDoc describes.
 *
 * `productType` *is* on the SSE payload (the notifier resolves it server-side), but the local
 * catalog cache is preferred when it has the product: it is what every other surface on this
 * device displays, and a tenant that renamed a product should not see two spellings depending on
 * which path delivered the banner.
 */
internal class BatchReadySseListener(
    private val eventStream: SyncEventStream,
    private val batchReadyBus: BatchReadyBus,
    private val productRepository: ProductRepository,
    private val scope: CoroutineScope,
) : BatchReadyStream {

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return // idempotent -- a second start must not open a second stream
        job = scope.launch {
            eventStream.events()
                .collect { event ->
                    if (event.type != BATCH_READY_EVENT && event.type != BATCH_SNAPSHOT_EVENT) return@collect
                    val wire = runCatching { json.decodeFromString<BatchReadyWire>(event.data) }.getOrNull()
                        ?: return@collect // malformed frame: skip it, the pull feed will still deliver the batch
                    val productId = Uuid.parse(wire.productId)
                    val cachedName = productRepository.findById(productId)?.productType

                    batchReadyBus.publish(
                        BatchReadyAnnouncement(
                            batchId = Uuid.parse(wire.batchId),
                            branchId = Uuid.parse(wire.branchId),
                            productId = productId,
                            productTypeName = cachedName ?: wire.productType.orEmpty(),
                            mixWeightGrams = wire.mixWeightGrams,
                            readyAt = Instant.fromEpochMilliseconds(wire.batchAt),
                        ),
                    )
                }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val BATCH_READY_EVENT = "batch-ready"
        const val BATCH_SNAPSHOT_EVENT = "batch-snapshot"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Mirrors the union of the two server-side shapes that arrive on this event: the notifier's live
 * `BatchReadyPayload` (which includes `productType`) and `EventsRouter`'s snapshot-on-connect
 * `BatchReadySnapshotPayload` (which does not). `productType` is therefore optional here rather
 * than required — the snapshot frames would otherwise fail to decode and be silently dropped.
 */
@Serializable
private data class BatchReadyWire(
    val batchId: String,
    val branchId: String,
    val productId: String,
    val mixWeightGrams: Long,
    val batchAt: Long,
    val productType: String? = null,
    val decrementStatus: String? = null,
)

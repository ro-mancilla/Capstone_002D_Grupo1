package com.f776.ecopan.notifier.domain

import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import com.f776.ecopan.data.messagebroker.SsePushBroker
import com.f776.ecopan.data.messagebroker.model.BatchProcessed
import com.f776.ecopan.data.messagebroker.model.SsePushEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val TYPE_BATCH_READY = "batch-ready"

@Serializable
data class BatchReadyPayload(
    val batchId: String,
    val branchId: String,
    val productId: String,
    val productType: String,
    val mixWeightGrams: Long,
    val batchAt: Long,
    val decrementStatus: String
)

/**
 * Turns a stream of [BatchProcessed] events into exactly one "batch-ready" SSE broadcast per batch
 * (02 §5/§6) -- a batch is one event, no progress counting needed, so the old debounce-map/terminal
 * CAS logic from the template's per-item job pipeline is gone entirely. Re-reads the batch from the
 * DB (idempotent under at-least-once/duplicate delivery -- recomputed, never incremented).
 */
class NotificationProcessorImpl(
    private val productionBatchRepository: ProductionBatchRepository,
    private val catalogRepository: CatalogRepository,
    private val ssePushBroker: SsePushBroker
) : NotificationProcessor {
    private val logger = LoggerFactory.getLogger(NotificationProcessorImpl::class.java)

    override suspend fun handle(event: BatchProcessed) {
        val batch = productionBatchRepository.findById(kotlin.uuid.Uuid.parse(event.batchId))
        if (batch == null) {
            logger.warn("BatchProcessed for unknown batch ${event.batchId}; dropping")
            return
        }
        val product = catalogRepository.findProductById(batch.productId)
        val payload = Json.encodeToString(
            BatchReadyPayload(
                batchId = batch.id.toString(),
                branchId = batch.branchId.toString(),
                productId = batch.productId.toString(),
                productType = product?.productType ?: "UNKNOWN",
                mixWeightGrams = batch.mixWeightGrams,
                batchAt = batch.batchAt.toEpochMilliseconds(),
                decrementStatus = batch.decrementStatus.name
            )
        )
        ssePushBroker.publish(
            SsePushEvent(
                tenantId = batch.tenantId.toString(),
                branchId = batch.branchId.toString(),
                type = TYPE_BATCH_READY,
                payload = payload
            )
        )
        logger.info("Batch ${batch.id} ready for tenant ${batch.tenantId}: $payload")
    }
}

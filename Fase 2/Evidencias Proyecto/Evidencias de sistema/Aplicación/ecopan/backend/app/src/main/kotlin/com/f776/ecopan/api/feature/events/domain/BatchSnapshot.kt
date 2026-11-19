package com.f776.ecopan.api.feature.events.domain

import com.f776.ecopan.data.database.repository.model.ProductionBatch
import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** How far back the snapshot-on-connect replay reaches (02 §5). */
val SNAPSHOT_WINDOW = 24.hours

/**
 * SSE event name of the replayed batches. Deliberately NOT `batch-ready`: that name is the live
 * push (the one clients announce to the user); a replay only repaints state. Same payload shape.
 */
const val BATCH_SNAPSHOT_EVENT = "batch-snapshot"

@Serializable
internal data class BatchReadySnapshotPayload(
    val batchId: String,
    val branchId: String,
    val productId: String,
    val mixWeightGrams: Long,
    val batchAt: Long,
    val decrementStatus: String
)

fun snapshotSince(now: Instant): Instant = now - SNAPSHOT_WINDOW

/** One `batch-snapshot` frame per batch visible to the connection's branch scope (null = tenant-wide). */
fun snapshotFrames(batches: List<ProductionBatch>, branchId: String?): List<ServerSentEvent> =
    batches
        .filter { branchId == null || it.branchId.toString() == branchId }
        .map { batch ->
            val payload = Json.encodeToString(
                BatchReadySnapshotPayload(
                    batchId = batch.id.toString(),
                    branchId = batch.branchId.toString(),
                    productId = batch.productId.toString(),
                    mixWeightGrams = batch.mixWeightGrams,
                    batchAt = batch.batchAt.toEpochMilliseconds(),
                    decrementStatus = batch.decrementStatus.name
                )
            )
            ServerSentEvent(event = BATCH_SNAPSHOT_EVENT, data = payload, id = batch.id.toString())
        }

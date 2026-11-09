package com.f776.ecopan.domain.production

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * "A batch is ready" — the front-of-house notification (06 §6), not a ledger row.
 *
 * Deliberately *not* [ProductionBatch]: an announcement can originate from the SSE `batch-ready`
 * frame (02 §5), whose payload carries no `bakerId` and no `tenantId`. Reusing [ProductionBatch]
 * would have forced this path to invent values for fields the banner never shows, to satisfy a
 * type whose real purpose is the durable record. The durable record still arrives — via the pull
 * feed and `ProductionBatchSyncApplier` — and remains the source of truth; this is only what POS
 * needs to draw a banner.
 */
data class BatchReadyAnnouncement(
    val batchId: Uuid,
    val branchId: Uuid,
    val productId: Uuid,
    /** Denormalized display name; empty when the product isn't in this device's catalog cache. */
    val productTypeName: String,
    val mixWeightGrams: Long,
    val readyAt: Instant,
)

package com.f776.ecopan.data.messagebroker.model

import kotlinx.serialization.Serializable

/**
 * Emitted by the worker after a production batch's decrement processing reaches a terminal status
 * (02 §6). Only a wake-up signal -- the notifier re-reads the batch from the DB, so a duplicate
 * delivery is harmless.
 */
@Serializable
data class BatchProcessed(
    val batchId: String,
    val tenantId: String,
    val branchId: String,
    /** APPLIED | SKIPPED | FAILED (mirrors [com.f776.ecopan.data.database.entity.DecrementStatus], minus PENDING). */
    val status: String
)

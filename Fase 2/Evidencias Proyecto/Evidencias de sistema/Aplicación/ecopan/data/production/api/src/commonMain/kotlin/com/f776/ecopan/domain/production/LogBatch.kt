package com.f776.ecopan.domain.production

import kotlin.uuid.Uuid

/**
 * The command a baker's confirm tap issues. [branchId]/[bakerId] come from the caller's current
 * device/PIN session (01 §4 — no `:feature:auth` session type exists yet to source them from
 * automatically); the batch's own id and timestamp are minted inside the repository.
 */
data class LogBatch(
    val branchId: Uuid,
    val bakerId: Uuid,
    val productId: Uuid,
    val mixWeightGrams: Long,
)

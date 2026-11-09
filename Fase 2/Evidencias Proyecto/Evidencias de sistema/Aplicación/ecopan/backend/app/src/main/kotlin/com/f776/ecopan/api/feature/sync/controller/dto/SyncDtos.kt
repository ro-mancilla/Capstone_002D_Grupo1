package com.f776.ecopan.api.feature.sync.controller.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** S1 request (02 §4.1) -- an outbox drain batch, ≤ 200 mutations per call. */
@Serializable
data class PushRequest(val deviceId: String, val mutations: List<MutationDto>)

@Serializable
data class MutationDto(
    val mutationId: String,
    val entityType: String,
    val opType: String,
    val clientTimestamp: Long,
    val payload: JsonElement
)

@Serializable
data class PushResponse(val serverTime: Long, val results: List<MutationResultDto>)

@Serializable
data class MutationResultDto(
    val mutationId: String,
    val status: String,
    val serverSeq: Long? = null,
    val error: SyncErrorDto? = null
)

@Serializable
data class SyncErrorDto(val code: String, val message: String? = null)

// -- Push payload shapes (02 §4.1; camelCase of the §1 columns, tenantId never included). --

@Serializable
data class SalePayload(
    val id: String,
    val branchId: String,
    val cashierId: String,
    val soldAt: Long,
    val paymentMethod: String,
    val totalMinor: Long,
    val lines: List<SaleLinePayload>
)

@Serializable
data class SaleLinePayload(
    val id: String,
    val productId: String,
    val soldBy: String,
    val weightGrams: Long? = null,
    val quantity: Int? = null,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long
)

@Serializable
data class ProductionBatchPayload(
    val id: String,
    val branchId: String,
    val bakerId: String,
    val productId: String,
    val mixWeightGrams: Long,
    val batchAt: Long,
    val movementsMinted: Boolean
)

@Serializable
data class StockMovementPayload(
    val id: String,
    val branchId: String,
    val ingredientId: String,
    val deltaGrams: Long,
    val kind: String,
    val note: String? = null,
    val occurredAt: Long,
    val sourceBatchId: String? = null
)

// -- Pull (02 §4.2). --

@Serializable
data class PullResponse(val changes: List<ChangeDto>, val nextCursor: Long, val hasMore: Boolean)

@Serializable
data class ChangeDto(val entityType: String, val opType: String, val serverSeq: Long, val payload: JsonElement)

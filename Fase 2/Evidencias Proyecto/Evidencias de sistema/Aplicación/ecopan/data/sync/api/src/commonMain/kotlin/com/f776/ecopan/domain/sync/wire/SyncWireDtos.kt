package com.f776.ecopan.domain.sync.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Client-side mirror of the backend's frozen sync wire contract (`backend/app`'s
 * `com.f776.ecopan.api.feature.sync.controller.dto.SyncDtos`/`PullPayloads` — 02 §4, authoritative
 * for every field name here). These are the push/pull *envelope* shapes; the per-entity `payload`
 * inside each is a `JsonElement` because its concrete shape depends on `entityType` and is decoded
 * by the entity's own module (e.g. `data.sales.wire.SalePushPayload`, or a pull-side `SyncApplier`
 * — 03 §4.3) — `:data:sync` itself never needs to know those shapes.
 */
@Serializable
data class PushRequest(val deviceId: String, val mutations: List<MutationDto>)

/**
 * [clientTimestamp] is epoch millis, matching the backend's `Instant.fromEpochMilliseconds`
 * decoding — *not* an ISO-8601 string (03 §3.2's illustrative `clientCreatedAt: "<ISO instant>"`
 * sketch is superseded by the real backend shape, which this mirrors exactly).
 */
@Serializable
data class MutationDto(
    val mutationId: String,
    val entityType: String,
    val opType: String,
    val clientTimestamp: Long,
    val payload: JsonElement,
)

@Serializable
data class PushResponse(val serverTime: Long, val results: List<MutationResultDto>)

/** [status] is one of `"APPLIED"`, `"DUPLICATE"`, `"REJECTED"` (03 §3.2/§5). */
@Serializable
data class MutationResultDto(
    val mutationId: String,
    val status: String,
    val serverSeq: Long? = null,
    val error: SyncErrorDto? = null,
)

@Serializable
data class SyncErrorDto(val code: String, val message: String? = null)

/** `GET /sync/pull` response (02 §4.2/03 §4.1). */
@Serializable
data class PullResponse(val changes: List<ChangeDto>, val nextCursor: Long, val hasMore: Boolean)

@Serializable
data class ChangeDto(val entityType: String, val opType: String, val serverSeq: Long, val payload: JsonElement)

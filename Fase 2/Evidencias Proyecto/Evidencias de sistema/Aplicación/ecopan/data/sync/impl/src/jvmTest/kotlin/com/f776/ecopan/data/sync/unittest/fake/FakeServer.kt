package com.f776.ecopan.data.sync.unittest.fake

import com.f776.ecopan.domain.sync.wire.ChangeDto
import com.f776.ecopan.domain.sync.wire.MutationResultDto
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.ecopan.domain.sync.wire.PushResponse

/**
 * In-memory stand-in for the real backend (03 §9): dedups pushes by `mutationId` (idempotent
 * ingest, 01 §6.3/03 §3.2) and serves a pull feed keyed by an assigned `serverSeq`. Deliberately
 * ignorant of any real entity's payload shape -- it just stores/replays whatever `JsonElement`
 * arrives, exactly like the real server treats `payload` as opaque per entityType (02 §4).
 */
internal class FakeServer {
    private val appliedResults = mutableMapOf<String, MutationResultDto>()
    private val feed = mutableListOf<ChangeDto>()
    private var nextServerSeq = 0L

    /** entityType names this server rejects (validation failure) on push -- always, not just once. */
    var rejectedEntityTypes: Set<String> = emptySet()

    val appliedMutationIds: Set<String> get() = appliedResults.keys

    fun push(request: PushRequest): PushResponse {
        val results = request.mutations.map { mutation ->
            val already = appliedResults[mutation.mutationId]
            when {
                already != null -> MutationResultDto(mutation.mutationId, "DUPLICATE", serverSeq = already.serverSeq)
                mutation.entityType in rejectedEntityTypes -> MutationResultDto(
                    mutation.mutationId,
                    "REJECTED",
                    error = com.f776.ecopan.domain.sync.wire.SyncErrorDto("VALIDATION_ERROR", "rejected by FakeServer"),
                )
                else -> {
                    nextServerSeq += 1
                    val result = MutationResultDto(mutation.mutationId, "APPLIED", serverSeq = nextServerSeq)
                    appliedResults[mutation.mutationId] = result
                    feed += ChangeDto(mutation.entityType, mutation.opType, nextServerSeq, mutation.payload)
                    result
                }
            }
        }
        return PushResponse(serverTime = 0L, results = results)
    }

    /** Seeds a pull-feed row directly (config/fact rows a *different* client pushed, 03 §9 T1/T2/T9/T10). */
    fun seed(entityType: String, opType: String, payload: kotlinx.serialization.json.JsonElement): Long {
        nextServerSeq += 1
        feed += ChangeDto(entityType, opType, nextServerSeq, payload)
        return nextServerSeq
    }

    fun pull(since: Long, limit: Int, entityTypeNames: Set<String>?) = feed
        .asSequence()
        .filter { it.serverSeq > since }
        .filter { entityTypeNames == null || it.entityType in entityTypeNames }
        .sortedBy { it.serverSeq }
        .toList()
        .let { eligible ->
            val page = eligible.take(limit)
            val hasMore = eligible.size > page.size
            val nextCursor = page.lastOrNull()?.serverSeq ?: since
            com.f776.ecopan.domain.sync.wire.PullResponse(page, nextCursor, hasMore)
        }
}

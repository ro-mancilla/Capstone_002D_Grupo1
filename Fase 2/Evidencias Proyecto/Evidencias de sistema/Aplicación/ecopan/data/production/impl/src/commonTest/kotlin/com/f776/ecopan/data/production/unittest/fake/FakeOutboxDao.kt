package com.f776.ecopan.data.production.unittest.fake

import com.f776.unigrid.core.database.dao.OutboxDao
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FakeOutboxDao : OutboxDao {
    private val state = MutableStateFlow<Map<Uuid, OutboxEntity>>(emptyMap())
    private var nextRowid = 1L

    override suspend fun insert(entity: OutboxEntity) {
        if (entity.mutationId in state.value) return
        state.value = state.value + (entity.mutationId to entity.copy(rowid = nextRowid++))
    }

    override fun observePendingCount() =
        state.map { byId -> byId.values.count { it.state == OutboxState.PENDING } }

    override fun observePendingRegisteredCount() =
        state.map { byId ->
            byId.values.count {
                it.state == OutboxState.PENDING &&
                    !(it.entityType == "STOCK_MOVEMENT" && "\"sourceBatchId\":\"" in it.payload)
            }
        }

    override suspend fun takeBatch(limit: Int) =
        state.value.values.filter { it.state == OutboxState.PENDING }.sortedBy { it.rowid }.take(limit)

    override suspend fun markInFlight(mutationIds: List<Uuid>) = updateState(mutationIds, OutboxState.IN_FLIGHT)

    override suspend fun deleteAcked(mutationIds: List<Uuid>) {
        state.value = state.value - mutationIds.toSet()
    }

    override suspend fun revertToPending(mutationIds: List<Uuid>) = updateState(mutationIds, OutboxState.PENDING)

    override suspend fun revertAllInFlightToPending() {
        state.value = state.value.mapValues { (_, row) ->
            if (row.state == OutboxState.IN_FLIGHT) row.copy(state = OutboxState.PENDING) else row
        }
    }

    override suspend fun incrementAttempts(mutationIds: List<Uuid>) {
        state.value = state.value.mapValues { (id, row) ->
            if (id in mutationIds) row.copy(attempts = row.attempts + 1) else row
        }
    }

    override suspend fun markFailed(mutationId: Uuid, error: String) {
        state.value[mutationId]?.let {
            state.value = state.value + (mutationId to it.copy(state = OutboxState.FAILED, lastError = error))
        }
    }

    override fun observeFailed() =
        state.map { byId -> byId.values.filter { it.state == OutboxState.FAILED }.sortedBy { it.rowid } }

    override fun observeFailedCount() =
        state.map { byId -> byId.values.count { it.state == OutboxState.FAILED } }

    override fun observeFailedMutationIds() =
        state.map { byId -> byId.values.filter { it.state == OutboxState.FAILED }.map { it.mutationId } }

    override suspend fun requeueFailed(mutationId: Uuid): Int = transitionFailed(mutationId, OutboxState.PENDING, clearError = true)

    override suspend fun discardFailed(mutationId: Uuid): Int = transitionFailed(mutationId, OutboxState.DISCARDED, clearError = false)

    private fun transitionFailed(mutationId: Uuid, to: OutboxState, clearError: Boolean): Int {
        val row = state.value[mutationId]?.takeIf { it.state == OutboxState.FAILED } ?: return 0
        state.value = state.value + (mutationId to row.copy(state = to, lastError = if (clearError) null else row.lastError))
        return 1
    }

    override suspend fun stateOf(mutationId: Uuid) = state.value[mutationId]?.state

    override fun observePendingMutationIds() =
        state.map { byId -> byId.values.filter { it.state == OutboxState.PENDING || it.state == OutboxState.IN_FLIGHT }.map { it.mutationId } }

    override suspend fun clear() {
        state.value = emptyMap()
    }

    private fun updateState(mutationIds: List<Uuid>, newState: OutboxState) {
        state.value = state.value.mapValues { (id, row) -> if (id in mutationIds) row.copy(state = newState) else row }
    }
}

package com.f776.ecopan.feature.settings.unittest.fake

import com.f776.ecopan.domain.sync.RejectedOperation
import com.f776.ecopan.domain.sync.RejectedOperations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

class FakeRejectedOperations(initial: List<RejectedOperation> = emptyList()) : RejectedOperations {
    private val state = MutableStateFlow(initial)
    val retried = mutableListOf<Uuid>()
    val discarded = mutableListOf<Uuid>()

    override val rejected = state

    override suspend fun retry(mutationId: Uuid) {
        retried += mutationId
        state.update { rows -> rows.filterNot { it.mutationId == mutationId } }
    }

    override suspend fun discard(mutationId: Uuid) {
        discarded += mutationId
        state.update { rows -> rows.filterNot { it.mutationId == mutationId } }
    }
}

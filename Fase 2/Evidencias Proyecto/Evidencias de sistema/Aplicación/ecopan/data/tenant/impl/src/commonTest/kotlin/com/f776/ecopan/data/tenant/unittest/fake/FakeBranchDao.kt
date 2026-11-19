package com.f776.ecopan.data.tenant.unittest.fake

import com.f776.unigrid.core.database.dao.BranchDao
import com.f776.unigrid.core.database.entity.BranchEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FakeBranchDao : BranchDao {
    private val state = MutableStateFlow<Map<Uuid, BranchEntity>>(emptyMap())

    override suspend fun upsertAll(branches: List<BranchEntity>) {
        state.value = state.value + branches.associateBy { it.id }
    }

    override fun observeByTenant(tenantId: Uuid) =
        state.map { byId ->
            byId.values.filter { it.tenantId == tenantId }.sortedBy { it.name }
        }

    override suspend fun clear() {
        state.value = emptyMap()
    }
}

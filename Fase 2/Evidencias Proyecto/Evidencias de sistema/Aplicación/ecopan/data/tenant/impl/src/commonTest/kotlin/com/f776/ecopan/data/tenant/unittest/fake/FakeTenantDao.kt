package com.f776.ecopan.data.tenant.unittest.fake

import com.f776.unigrid.core.database.dao.TenantDao
import com.f776.unigrid.core.database.entity.TenantEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeTenantDao : TenantDao {
    private val state = MutableStateFlow<TenantEntity?>(null)

    override suspend fun upsert(tenant: TenantEntity) {
        state.value = tenant
    }

    override fun observe() = state.asStateFlow()

    override suspend fun clear() {
        state.value = null
    }
}

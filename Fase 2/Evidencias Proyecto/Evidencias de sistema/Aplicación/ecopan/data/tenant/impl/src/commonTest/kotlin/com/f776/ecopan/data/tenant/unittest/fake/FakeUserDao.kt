package com.f776.ecopan.data.tenant.unittest.fake

import com.f776.unigrid.core.database.dao.UserDao
import com.f776.unigrid.core.database.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FakeUserDao : UserDao {
    private val state = MutableStateFlow<Map<Uuid, UserEntity>>(emptyMap())

    override suspend fun upsertAll(users: List<UserEntity>) {
        state.value = state.value + users.associateBy { it.id }
    }

    override fun observeByTenant(tenantId: Uuid) =
        state.map { byId ->
            byId.values.filter { it.tenantId == tenantId }.sortedBy { it.displayName }
        }

    override suspend fun findById(id: Uuid) = state.value[id]

    override suspend fun clear() {
        state.value = emptyMap()
    }
}

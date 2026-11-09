package com.f776.ecopan.feature.auth.unittest.fake

import com.f776.ecopan.domain.tenant.SessionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal class FakeSessionProvider : SessionProvider {
    val currentUserIdState = MutableStateFlow<Uuid?>(null)
    override val currentUserId = currentUserIdState

    override suspend fun unlock(userId: Uuid) {
        currentUserIdState.value = userId
    }

    override suspend fun lock() {
        currentUserIdState.value = null
    }
}

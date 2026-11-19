package com.f776.ecopan.feature.pos.unittest.fake

import com.f776.ecopan.domain.tenant.AuthRepository
import com.f776.ecopan.domain.tenant.LoginOutcome
import kotlin.uuid.Uuid

/** Records the one call the POS makes -- the reconnect after a device session dies. */
internal class FakeAuthRepository(
    var outcome: LoginOutcome = LoginOutcome.Success(Uuid.random(), Uuid.random()),
) : AuthRepository {
    val logins = mutableListOf<List<String>>()

    override suspend fun login(
        tenantSlug: String,
        username: String,
        password: String,
        deviceName: String,
    ): LoginOutcome {
        logins += listOf(tenantSlug, username, password, deviceName)
        return outcome
    }
}

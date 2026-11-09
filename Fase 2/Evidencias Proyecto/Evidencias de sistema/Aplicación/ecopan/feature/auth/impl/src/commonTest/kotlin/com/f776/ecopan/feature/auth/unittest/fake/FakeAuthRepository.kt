@file:OptIn(ExperimentalUuidApi::class)

package com.f776.ecopan.feature.auth.unittest.fake

import com.f776.ecopan.core.domain.auth.Role
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.tenant.AuthRepository
import com.f776.ecopan.domain.tenant.LoginOutcome
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Mirrors what the real `AuthRepositoryImpl` does on a successful login (`:data:tenant:impl`) --
 * writes into the *same* fakes the test wires up for [com.f776.ecopan.domain.tenant.TenantRepository]
 * / [com.f776.ecopan.domain.tenant.SessionProvider] -- so `AuthViewModel` tests observe the same
 * post-login state a real device would, without needing a real Ktor call.
 */
internal class FakeAuthRepository(
    private val tenantRepository: FakeTenantRepository,
    private val sessionProvider: FakeSessionProvider,
) : AuthRepository {

    /** When non-null, returned as-is instead of the default synthesized [LoginOutcome.Success]. */
    var resultOverride: LoginOutcome? = null
    var loginCallCount = 0
        private set

    override suspend fun login(tenantSlug: String, username: String, password: String, deviceName: String): LoginOutcome {
        loginCallCount++
        resultOverride?.let { return it }

        val tenantId = Uuid.random()
        val userId = Uuid.random()
        tenantRepository.upsertTenant(
            TenantProfile(id = tenantId, slug = tenantSlug, name = "Panadería $tenantSlug", currencyCode = "CLP", enabledModules = setOf(EntitlementModule.POS_SALES)),
        )
        tenantRepository.upsertUsers(
            listOf(UserAccountUpsert(id = userId, tenantId = tenantId, branchId = null, username = username, displayName = username, role = Role.CASHIER, active = true, pinHash = null, serverSeq = 0L)),
        )
        sessionProvider.unlock(userId)
        return LoginOutcome.Success(tenantId = tenantId, userId = userId)
    }
}

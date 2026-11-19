@file:OptIn(ExperimentalUuidApi::class)

package com.f776.ecopan.data.tenant.unittest

import com.f776.ecopan.data.tenant.AuthRepositoryImpl
import com.f776.ecopan.data.tenant.TokenStore
import com.f776.ecopan.data.tenant.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.LoginOutcome
import com.f776.ecopan.domain.tenant.SessionProvider
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AuthRepositoryImplTest {

    private class FakeTenantRepository : TenantRepository {
        val tenant = MutableStateFlow<TenantProfile?>(null)
        val branches = MutableStateFlow<List<Branch>>(emptyList())
        val users = MutableStateFlow<List<UserAccount>>(emptyList())

        override fun observeTenant() = tenant
        override suspend fun upsertTenant(profile: TenantProfile) { tenant.value = profile }
        override fun observeBranches() = branches
        override suspend fun upsertBranches(branches: List<BranchUpsert>) {
            this.branches.value = branches.map { Branch(it.id, it.tenantId, it.name, it.active) }
        }
        override fun observeUsers() = users
        override suspend fun upsertUsers(users: List<UserAccountUpsert>) {
            this.users.value = users.map { UserAccount(it.id, it.tenantId, it.branchId, it.username, it.displayName, it.role, it.active, hasPin = it.pinHash != null) }
        }
        override suspend fun verifyPin(userId: Uuid, pin: String) = false
        override suspend fun clear() { tenant.value = null; branches.value = emptyList(); users.value = emptyList() }
    }

    private class FakeSessionProvider : SessionProvider {
        val currentUserIdState = MutableStateFlow<Uuid?>(null)
        override val currentUserId = currentUserIdState
        override suspend fun unlock(userId: Uuid) { currentUserIdState.value = userId }
        override suspend fun lock() { currentUserIdState.value = null }
    }

    private fun repository(engine: MockEngine, tenantRepository: FakeTenantRepository, sessionProvider: FakeSessionProvider): AuthRepositoryImpl {
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val tokenStore = TokenStore(FakePreferencesDataStore())
        return AuthRepositoryImpl(client, "http://backend", tokenStore, tenantRepository, sessionProvider)
    }

    @Test
    fun `successful login caches tenant branches and this user then unlocks the session`() = runTest {
        val tenantId = Uuid.random()
        val userId = Uuid.random()
        val engine = MockEngine {
            respond(
                content = """
                    {"accessToken":"acc","refreshToken":"ref",
                     "user":{"id":"$userId","displayName":"Camila Reyes","role":"CASHIER","branchId":null},
                     "tenant":{"id":"$tenantId","name":"Panadería La Espiga","currencyCode":"CLP","enabledModules":["POS_SALES"]},
                     "branches":[]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val tenantRepository = FakeTenantRepository()
        val sessionProvider = FakeSessionProvider()
        val repo = repository(engine, tenantRepository, sessionProvider)

        val outcome = repo.login("la-espiga", "cami", "secret", "Caja 1")

        assertEquals(LoginOutcome.Success(tenantId, userId), outcome)
        assertEquals("Panadería La Espiga", tenantRepository.tenant.value?.name)
        assertTrue(tenantRepository.users.value.any { it.id == userId })
        assertEquals(userId, sessionProvider.currentUserIdState.value)
    }

    @Test
    fun `a successful login remembers the name this till was enrolled under`() = runTest {
        val tenantId = Uuid.random()
        val userId = Uuid.random()
        val engine = MockEngine {
            respond(
                content = """
                    {"accessToken":"acc","refreshToken":"ref",
                     "user":{"id":"$userId","displayName":"Camila Reyes","role":"CASHIER","branchId":null},
                     "tenant":{"id":"$tenantId","name":"Panadería La Espiga","currencyCode":"CLP","enabledModules":["POS_SALES"]},
                     "branches":[]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val tokenStore = TokenStore(FakePreferencesDataStore())
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val repo = AuthRepositoryImpl(client, "http://backend", tokenStore, FakeTenantRepository(), FakeSessionProvider())
        assertNull(tokenStore.deviceName())

        repo.login("la-espiga", "cami", "secret", "  Caja 1 ")

        assertEquals("Caja 1", tokenStore.deviceName())
    }

    @Test
    fun `invalid credentials returns InvalidCredentials without caching anything`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.Unauthorized) }
        val tenantRepository = FakeTenantRepository()
        val sessionProvider = FakeSessionProvider()
        val repo = repository(engine, tenantRepository, sessionProvider)

        val outcome = repo.login("la-espiga", "cami", "wrong", "Caja 1")

        assertEquals(LoginOutcome.InvalidCredentials, outcome)
        assertNull(tenantRepository.tenant.value)
        assertNull(sessionProvider.currentUserIdState.value)
    }

    @Test
    fun `a throttled login returns TooManyAttempts, not NetworkError`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.TooManyRequests) }
        val tenantRepository = FakeTenantRepository()
        val repo = repository(engine, tenantRepository, FakeSessionProvider())

        assertEquals(LoginOutcome.TooManyAttempts, repo.login("la-espiga", "cami", "secret", "Caja 1"))
        assertNull(tenantRepository.tenant.value)
    }

    @Test
    fun `a transport failure returns NetworkError -- the offline-fresh-install case`() = runTest {
        val engine = MockEngine { throw kotlinx.io.IOException("no route to host") }
        val tenantRepository = FakeTenantRepository()
        val sessionProvider = FakeSessionProvider()
        val repo = repository(engine, tenantRepository, sessionProvider)

        val outcome = repo.login("la-espiga", "cami", "secret", "Caja 1")

        assertEquals(LoginOutcome.NetworkError, outcome)
        assertNull(tenantRepository.tenant.value)
    }
}

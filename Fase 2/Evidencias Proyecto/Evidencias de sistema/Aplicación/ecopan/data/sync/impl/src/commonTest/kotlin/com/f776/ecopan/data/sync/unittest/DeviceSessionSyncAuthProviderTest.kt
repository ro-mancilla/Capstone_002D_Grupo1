package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.data.sync.transport.DeviceSessionSyncAuthProvider
import com.f776.ecopan.data.sync.transport.SyncRefreshOutcome
import com.f776.ecopan.data.sync.unittest.fake.FakeDeviceSessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceSessionSyncAuthProviderTest {

    private fun clientWith(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json() }
    }

    @Test
    fun `currentAccessToken reads straight through to the device session store`() = runTest {
        val store = FakeDeviceSessionStore(accessToken = "cached-access")
        val provider = DeviceSessionSyncAuthProvider(store, clientWith(MockEngine { respond("") }), "http://backend")

        assertEquals("cached-access", provider.currentAccessToken())
    }

    @Test
    fun `refresh with no refresh token cached fails without a network call`() = runTest {
        var called = false
        val store = FakeDeviceSessionStore()
        val engine = MockEngine {
            called = true
            respond("")
        }
        val provider = DeviceSessionSyncAuthProvider(store, clientWith(engine), "http://backend")

        assertEquals(SyncRefreshOutcome.REJECTED, provider.refresh())
        assertFalse(called)
    }

    @Test
    fun `successful refresh saves the new token pair and reports success`() = runTest {
        val store = FakeDeviceSessionStore(accessToken = "old-access", refreshToken = "old-refresh")
        val engine = MockEngine {
            respond(
                content = """{"accessToken":"new-access","refreshToken":"new-refresh"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val provider = DeviceSessionSyncAuthProvider(store, clientWith(engine), "http://backend")

        assertEquals(SyncRefreshOutcome.REFRESHED, provider.refresh())
        assertEquals("new-access", store.currentAccessToken())
        assertEquals("new-refresh", store.currentRefreshToken())
        assertEquals(1, store.saveTokensCallCount)
    }

    /**
     * The server rotates on refresh: the token posted is invalidated and a new one issued, so a
     * given refresh token works exactly once. Push and pull both 401 the moment the access token
     * expires and each call refresh independently, so without single-flighting the second call
     * posts a token the first has already spent, gets a 401, and reports the device session dead.
     * That is what left a healthy install stuck on "Requiere atención" until someone re-enrolled.
     */
    @Test
    fun `concurrent refreshes make exactly one network call and both succeed`() = runTest {
        val store = FakeDeviceSessionStore(accessToken = "old-access", refreshToken = "old-refresh")
        var calls = 0
        val engine = MockEngine { request ->
            calls += 1
            // A rotating server: only the first token presented is honoured.
            val body = (request.body as io.ktor.http.content.TextContent).text
            if (!body.contains("old-refresh")) {
                respond(content = "", status = HttpStatusCode.Unauthorized)
            } else {
                respond(
                    content = """{"accessToken":"new-access","refreshToken":"new-refresh"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val provider = DeviceSessionSyncAuthProvider(store, clientWith(engine), "http://backend")

        val results = listOf(
            async { provider.refresh() },
            async { provider.refresh() },
        ).awaitAll()

        assertTrue(results.all { it == SyncRefreshOutcome.REFRESHED }, "both callers should end up with a usable token")
        assertEquals(1, calls, "the second caller must reuse the first refresh, not spend the token again")
        assertEquals("new-access", store.currentAccessToken())
    }

    @Test
    fun `refresh rejected by the server fails and does not touch stored tokens`() = runTest {
        val store = FakeDeviceSessionStore(accessToken = "old-access", refreshToken = "old-refresh")
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }
        val provider = DeviceSessionSyncAuthProvider(store, clientWith(engine), "http://backend")

        assertEquals(SyncRefreshOutcome.REJECTED, provider.refresh())
        assertEquals("old-access", store.currentAccessToken())
        assertEquals(0, store.saveTokensCallCount)
    }

    /** #133: a throttled or failing server says nothing about the session -- keep it, retry later. */
    @Test
    fun `a throttled or failing refresh is transient and keeps the stored tokens`() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.InternalServerError, HttpStatusCode.ServiceUnavailable)) {
            val store = FakeDeviceSessionStore(accessToken = "old-access", refreshToken = "old-refresh")
            val provider = DeviceSessionSyncAuthProvider(store, clientWith(MockEngine { respond("", status) }), "http://backend")

            assertEquals(SyncRefreshOutcome.TRANSIENT, provider.refresh(), "HTTP $status")
            assertEquals("old-refresh", store.currentRefreshToken())
        }
    }

    /** The 200 already rotated the token, so an unreadable reply leaves nothing to retry with. */
    @Test
    fun `an unreadable 200 is rejected, not retried with the spent token`() = runTest {
        val store = FakeDeviceSessionStore(accessToken = "old-access", refreshToken = "old-refresh")
        val engine = MockEngine { respond("<html>proxy</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val provider = DeviceSessionSyncAuthProvider(store, clientWith(engine), "http://backend")

        assertEquals(SyncRefreshOutcome.REJECTED, provider.refresh())
    }

    @Test
    fun `any other refusal is rejected`() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.BadRequest)) {
            val store = FakeDeviceSessionStore(accessToken = "old-access", refreshToken = "old-refresh")
            val provider = DeviceSessionSyncAuthProvider(store, clientWith(MockEngine { respond("", status) }), "http://backend")

            assertEquals(SyncRefreshOutcome.REJECTED, provider.refresh(), "HTTP $status")
        }
    }

    @Test
    fun `a transport failure during refresh fails gracefully rather than throwing`() = runTest {
        val store = FakeDeviceSessionStore(accessToken = "old-access", refreshToken = "old-refresh")
        val engine = MockEngine { throw kotlinx.io.IOException("connection reset") }
        val provider = DeviceSessionSyncAuthProvider(store, clientWith(engine), "http://backend")

        assertEquals(SyncRefreshOutcome.TRANSIENT, provider.refresh())
    }
}

package com.f776.ecopan.api

import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureRateLimit
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import com.f776.ecopan.api.feature.auth.controller.authRouter
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Login costs a bcrypt hash on a shared 2-vCPU instance, so it is limited per client address. */
class AuthRateLimitTest {

    private fun config(limit: Int, trustProxy: Boolean) = MapApplicationConfig(
        "proxy.trusted" to trustProxy.toString(),
        "jwt.secret" to "test-secret",
        "jwt.issuer" to "ecopan-test",
        "jwt.audience" to "ecopan-test-clients",
        "jwt.accessTokenTtlMin" to "15",
        "jwt.refreshTokenTtlDays" to "30",
        "device.sessionTtlDays" to "180",
        "reporting.tz" to "America/Santiago",
        "rateLimit.authPerMinute" to limit.toString(),
    )

    private suspend fun HttpClient.badLogin(forwardedFor: String? = null): HttpResponse = post("/auth/login") {
        contentType(ContentType.Application.Json)
        forwardedFor?.let { header("X-Forwarded-For", it) }
        setBody("""{"tenantSlug":"reference","username":"nobody","password":"nope"}""")
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.app(limit: Int, trustProxy: Boolean = true) {
        val config = config(limit, trustProxy)
        environment { this.config = config }
        application {
            installTestKoin(config, FakeTenantRepository(), FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureRateLimit()
            configureAuth()
            configureSerialization()
            authRouter()
        }
    }

    @Test
    fun `attempts over the limit get 429 with Retry-After`() = testApplication {
        app(limit = 3)
        repeat(3) { assertEquals(HttpStatusCode.Unauthorized, client.badLogin().status) }

        val limited = client.badLogin()
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        val wait = assertNotNull(limited.headers[HttpHeaders.RetryAfter]).toLong()
        assertTrue(wait in 1..60, "Retry-After $wait should be within the one-minute window")
    }

    @Test
    fun `refresh shares the limit with login`() = testApplication {
        app(limit = 2)
        repeat(2) { client.badLogin() }
        val refresh = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"x"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, refresh.status)
    }

    @Test
    fun `one client being limited does not limit another behind the proxy`() = testApplication {
        app(limit = 2)
        repeat(2) { client.badLogin(forwardedFor = "203.0.113.7:51000") }
        assertEquals(HttpStatusCode.TooManyRequests, client.badLogin(forwardedFor = "203.0.113.7:51999").status)
        assertEquals(HttpStatusCode.Unauthorized, client.badLogin(forwardedFor = "198.51.100.9:40000").status)
    }

    @Test
    fun `a spoofed first X-Forwarded-For entry does not buy a fresh limit`() = testApplication {
        app(limit = 2)
        // The proxy appends the real address last; whatever the client prepends is not trusted.
        repeat(2) { client.badLogin(forwardedFor = "1.1.1.$it, 203.0.113.7:51000") }
        assertEquals(HttpStatusCode.TooManyRequests, client.badLogin(forwardedFor = "9.9.9.9, 203.0.113.7:51000").status)
    }

    @Test
    fun `bracketed IPv6 with varying ports shares one limit`() = testApplication {
        app(limit = 2)
        repeat(2) { client.badLogin(forwardedFor = "[2001:db8::7]:${50000 + it}") }
        assertEquals(HttpStatusCode.TooManyRequests, client.badLogin(forwardedFor = "[2001:db8::7]:59999").status)
    }

    @Test
    fun `addresses in one IPv6 slash-64 share one limit`() = testApplication {
        app(limit = 2)
        client.badLogin(forwardedFor = "2001:db8:1:2::1")
        client.badLogin(forwardedFor = "[2001:db8:1:2:aaaa::9]:443")
        assertEquals(HttpStatusCode.TooManyRequests, client.badLogin(forwardedFor = "2001:db8:1:2:ffff:ffff:ffff:ffff").status)
        assertEquals(HttpStatusCode.Unauthorized, client.badLogin(forwardedFor = "2001:db8:1:3::1").status)
    }

    @Test
    fun `without a trusted proxy the header is ignored and the peer is the key`() = testApplication {
        app(limit = 2, trustProxy = false)
        client.badLogin(forwardedFor = "203.0.113.1")
        client.badLogin(forwardedFor = "203.0.113.2")
        // A fresh header per request buys nothing: every request comes from the same peer.
        assertEquals(HttpStatusCode.TooManyRequests, client.badLogin(forwardedFor = "203.0.113.3").status)
    }
}

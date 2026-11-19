package com.f776.ecopan.api

import com.f776.ecopan.api.core.configureCors
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import io.ktor.client.request.options
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The web dashboard (`webApp/`) runs on its own origin and calls this API cross-origin, so without
 * CORS the browser rejects every request at preflight -- before any route or auth code runs. The
 * KMP clients never exercise this path (CORS is a browser policy), which is exactly why it can
 * regress unnoticed: the whole backend test suite and all three native clients stay green while the
 * dashboard is completely unable to talk to it.
 */
class CorsTest {

    private fun configWith(origins: String) = MapApplicationConfig(
        "jwt.secret" to "test-secret",
        "jwt.issuer" to "ecopan-test",
        "jwt.audience" to "ecopan-test-clients",
        "jwt.accessTokenTtlMin" to "15",
        "jwt.refreshTokenTtlDays" to "30",
        "device.sessionTtlDays" to "180",
        "reporting.tz" to "America/Santiago",
        "cors.allowedOrigins" to origins,
    )

    private fun testApp(origins: String, block: suspend (io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            val config = configWith(origins)
            environment { this.config = config }
            application {
                installTestKoin(config, FakeTenantRepository(), FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
                configureCors()
                routing { get("/probe") { call.respond(HttpStatusCode.OK) } }
            }
            block(client)
        }

    @Test
    fun `preflight from an allowed origin is approved for the methods and headers the dashboard uses`() =
        testApp("http://localhost:5173") { client ->
            val response = client.options("/probe") {
                header(HttpHeaders.Origin, "http://localhost:5173")
                header(HttpHeaders.AccessControlRequestMethod, "PUT")
                header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("http://localhost:5173", response.headers[HttpHeaders.AccessControlAllowOrigin])

            // PUT/DELETE are not in Ktor's defaults (GET/POST/HEAD), and the admin CRUD needs them.
            val methods = response.headers[HttpHeaders.AccessControlAllowMethods].orEmpty()
            listOf("PUT", "DELETE", "OPTIONS").forEach { assertEquals(true, it in methods, "$it missing from $methods") }

            // Authorization is the whole auth scheme -- without it every authenticated call fails.
            val headers = response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty()
            listOf("Authorization", "Content-Type").forEach { assertEquals(true, it in headers, "$it missing from $headers") }
        }

    @Test
    fun `an actual request from an allowed origin carries the allow-origin header`() =
        testApp("http://localhost:5173") { client ->
            val response = client.get("/probe") { header(HttpHeaders.Origin, "http://localhost:5173") }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("http://localhost:5173", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `an unlisted origin is refused, never wildcarded`() =
        testApp("http://localhost:5173") { client ->
            val response = client.get("/probe") { header(HttpHeaders.Origin, "http://evil.example.com") }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    /** Port is part of an origin: 5173 being allowed must not admit anything else on localhost. */
    @Test
    fun `a different port on an allowed host is still a different origin`() =
        testApp("http://localhost:5173") { client ->
            val response = client.get("/probe") { header(HttpHeaders.Origin, "http://localhost:9999") }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    /** One typo'd entry must not take the others down with it, nor stop the API booting. */
    @Test
    fun `a malformed origin is skipped without discarding the valid ones`() =
        testApp("not-a-url, http://localhost:5173") { client ->
            val response = client.get("/probe") { header(HttpHeaders.Origin, "http://localhost:5173") }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("http://localhost:5173", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    /** Non-browser clients (the KMP till and baker app) send no Origin and must be unaffected. */
    @Test
    fun `a request with no Origin header is untouched`() =
        testApp("http://localhost:5173") { client ->
            assertEquals(HttpStatusCode.OK, client.get("/probe").status)
        }
}

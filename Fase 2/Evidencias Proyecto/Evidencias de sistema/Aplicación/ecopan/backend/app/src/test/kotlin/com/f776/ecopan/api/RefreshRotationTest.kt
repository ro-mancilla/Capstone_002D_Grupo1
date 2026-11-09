package com.f776.ecopan.api

import at.favre.lib.crypto.bcrypt.BCrypt
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureRateLimit
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import com.f776.ecopan.api.feature.auth.controller.authRouter
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.database.repository.model.User
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Role
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/** #141: atomic rotation, refresh-token families and reuse detection. */
class RefreshRotationTest {

    private val tenantId = Uuid.random()
    private val userId = Uuid.random()
    private val refreshTokens = FakeRefreshTokenRepository()

    private fun ApplicationTestBuilder.app(refreshTtlDays: Int = 30) {
        val config = MapApplicationConfig(
            "jwt.secret" to "test-secret",
            "jwt.issuer" to "ecopan-test",
            "jwt.audience" to "ecopan-test-clients",
            "jwt.accessTokenTtlMin" to "15",
            "jwt.refreshTokenTtlDays" to refreshTtlDays.toString(),
            "device.sessionTtlDays" to "180",
            "reporting.tz" to "America/Santiago",
            "rateLimit.authPerMinute" to "1000",
        )
        val tenants = FakeTenantRepository().apply {
            seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        }
        val users = FakeUserRepository().apply {
            seed(User(userId, tenantId, null, "gm", BCrypt.withDefaults().hashToString(4, "pw".toCharArray()), null, "GM", Role.GENERAL_MANAGER, true, 1, Clock.System.now()))
        }
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, users, FakeBranchRepository(), refreshTokens)
            configureRateLimit()
            configureAuth()
            configureSerialization()
            authRouter()
        }
    }

    private suspend fun HttpClient.login(deviceId: String? = null): String {
        val device = deviceId?.let { ""","deviceId":"$it","deviceName":"Till"""" }.orEmpty()
        val response = post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantSlug":"reference","username":"gm","password":"pw"$device}""")
        }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["refreshToken"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.refresh(token: String): HttpResponse = post("/auth/refresh") {
        contentType(ContentType.Application.Json)
        setBody("""{"refreshToken":"$token"}""")
    }

    private suspend fun HttpResponse.refreshToken() =
        Json.parseToJsonElement(bodyAsText()).jsonObject["refreshToken"]!!.jsonPrimitive.content

    @Test
    fun `concurrent refreshes with one token give one success and one refusal`() = testApplication {
        app()
        val token = client.login()
        refreshTokens.holdRotations = true

        val statuses = coroutineScope {
            val calls = List(2) { async { client.refresh(token).status } }
            refreshTokens.releaseRotationsWhenWaiting(2)
            calls.awaitAll()
        }

        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.Unauthorized), statuses.sortedBy { it.value })
    }

    @Test
    fun `reusing a revoked token refuses it and revokes its whole family`() = testApplication {
        app()
        val first = client.login(deviceId = "till-1")
        val unrelated = client.login(deviceId = "till-2")
        val second = client.refresh(first).refreshToken()

        assertEquals(HttpStatusCode.Unauthorized, client.refresh(first).status)

        // The live descendant is gone too: whoever held the stolen copy and whoever held the real
        // one both have to sign in again. Another device's session is untouched.
        assertEquals(HttpStatusCode.Unauthorized, client.refresh(second).status)
        assertEquals(HttpStatusCode.OK, client.refresh(unrelated).status)
    }

    @Test
    fun `the reuse warning names the user and device and never the token`() = testApplication {
        app()
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger("com.f776.ecopan.api.auth") as Logger
        logger.addAppender(appender)
        try {
            val first = client.login(deviceId = "till-7")
            client.refresh(first)
            client.refresh(first)

            val warning = assertNotNull(appender.list.singleOrNull { it.level.levelStr == "WARN" }, "${appender.list}")
            val text = warning.formattedMessage
            assertTrue(userId.toString() in text && "till-7" in text, text)
            assertTrue(first !in text, "the token reached the log: $text")
        } finally {
            logger.detachAppender(appender)
        }
    }

    @Test
    fun `a browser session's refresh token lives for the configured TTL`() = testApplication {
        app(refreshTtlDays = 7)
        client.refresh(client.login())

        val now = Clock.System.now()
        val live = refreshTokens.tokens.values.filter { it.revokedAt == null }
        assertEquals(1, live.size)
        val ttl = live.single().expiresAt - now
        assertTrue(ttl in (7.days - 1.minutes)..7.days, "ttl was $ttl")
    }
}

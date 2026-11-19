package com.f776.ecopan.api

import at.favre.lib.crypto.bcrypt.BCrypt
import com.f776.ecopan.api.core.PermissionRegistry
import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureRateLimit
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.core.mintAccessToken
import com.f776.ecopan.api.core.requireModule
import com.f776.ecopan.api.core.requirePermission
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeRolePermissionRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import com.f776.ecopan.api.feature.auth.controller.authRouter
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.database.repository.model.User
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * 02 §3/§8 AC: login/refresh/guard tests, Koin overridden with fakes -- no real Postgres/RabbitMQ.
 */
class AuthRouterTest {

    private val tenantId = Uuid.random()
    private val cashierId = Uuid.random()
    private val adminId = Uuid.random()

    private fun bcryptHash(password: String) = BCrypt.withDefaults().hashToString(4, password.toCharArray())

    private fun testConfig() = MapApplicationConfig(
        "jwt.secret" to "test-secret",
        "jwt.issuer" to "ecopan-test",
        "jwt.audience" to "ecopan-test-clients",
        "jwt.accessTokenTtlMin" to "15",
        "jwt.refreshTokenTtlDays" to "30",
        "device.sessionTtlDays" to "180",
        "reporting.tz" to "America/Santiago"
    )

    private fun seedTenantAndUsers(tenants: FakeTenantRepository, users: FakeUserRepository) {
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        users.seed(
            User(cashierId, tenantId, null, "cashier1", bcryptHash("pw12345"), null, "Cashier One", Role.CASHIER, true, 1, Clock.System.now())
        )
        users.seed(
            User(adminId, tenantId, null, "admin1", bcryptHash("adminpw"), null, "Admin One", Role.ADMIN, true, 1, Clock.System.now())
        )
    }

    @Test
    fun `login succeeds with correct credentials and 401s with wrong password`() = testApplication {
        val tenants = FakeTenantRepository()
        val users = FakeUserRepository()
        seedTenantAndUsers(tenants, users)
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, users, FakeBranchRepository(), FakeRefreshTokenRepository())
            configureRateLimit()
            configureAuth()
            configureSerialization()
            authRouter()
        }

        val ok = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantSlug":"reference","username":"cashier1","password":"pw12345"}""")
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        val body = Json.parseToJsonElement(ok.bodyAsText()).jsonObject
        assertTrue(body["accessToken"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(body["refreshToken"]!!.jsonPrimitive.content.isNotBlank())

        val wrongPassword = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantSlug":"reference","username":"cashier1","password":"nope"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
    }

    @Test
    fun `refresh rotates the token and rejects reuse of the old one`() = testApplication {
        val tenants = FakeTenantRepository()
        val users = FakeUserRepository()
        seedTenantAndUsers(tenants, users)
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, users, FakeBranchRepository(), FakeRefreshTokenRepository())
            configureRateLimit()
            configureAuth()
            configureSerialization()
            authRouter()
        }

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantSlug":"reference","username":"cashier1","password":"pw12345"}""")
        }
        val firstRefreshToken = Json.parseToJsonElement(login.bodyAsText()).jsonObject["refreshToken"]!!.jsonPrimitive.content

        val refreshed = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$firstRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val secondRefreshToken = Json.parseToJsonElement(refreshed.bodyAsText()).jsonObject["refreshToken"]!!.jsonPrimitive.content
        assertNotEquals(firstRefreshToken, secondRefreshToken)

        // Reuse of the rotated-out token must fail.
        val reused = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$firstRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, reused.status)
    }

    @Test
    fun `requirePermission 403s a role missing the permission and passes ADMIN`() = testApplication {
        PermissionRegistry.refresh(FakeRolePermissionRepository(mapOf(Role.CASHIER to setOf(Permission.SALES_RECORD))))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, FakeTenantRepository(), FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            routing {
                authenticate("auth-jwt") {
                    route("test") {
                        requirePermission(Permission.STOCK_ADJUST)
                        get("guarded") { call.respond(HttpStatusCode.OK) }
                    }
                }
            }
        }

        val settings = Settings(config)
        val cashierToken = mintAccessToken(settings.jwtSettings, cashierId, tenantId, null, Role.CASHIER, "cashier1")
        val adminToken = mintAccessToken(settings.jwtSettings, adminId, tenantId, null, Role.ADMIN, "admin1")

        val forbidden = client.get("/test/guarded") { header(HttpHeaders.Authorization, "Bearer $cashierToken") }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals("FORBIDDEN", Json.parseToJsonElement(forbidden.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)

        val allowed = client.get("/test/guarded") { header(HttpHeaders.Authorization, "Bearer $adminToken") }
        assertEquals(HttpStatusCode.OK, allowed.status)
    }

    @Test
    fun `requireModule 403s with MODULE_DISABLED when the tenant lacks the module`() = testApplication {
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            routing {
                authenticate("auth-jwt") {
                    route("test") {
                        requireModule(Module.INVENTORY_FORMULAS)
                        get("guarded") { call.respond(HttpStatusCode.OK) }
                    }
                }
            }
        }

        val settings = Settings(config)
        val token = mintAccessToken(settings.jwtSettings, adminId, tenantId, null, Role.ADMIN, "admin1")

        val response = client.get("/test/guarded") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val errorBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("MODULE_DISABLED", errorBody["code"]!!.jsonPrimitive.content)
        assertEquals("INVENTORY_FORMULAS", errorBody["module"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a protected route 401s without a token`() = testApplication {
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, FakeTenantRepository(), FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            routing {
                authenticate("auth-jwt") {
                    route("test") {
                        requirePermission(Permission.SALES_RECORD)
                        get("guarded") { call.respond(HttpStatusCode.OK) }
                    }
                }
            }
        }

        val response = client.get("/test/guarded")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}

package com.f776.ecopan.api.core

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.f776.ecopan.data.database.repository.RolePermissionRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

private const val CLAIM_TENANT_ID = "tenantId"
private const val CLAIM_BRANCH_ID = "branchId"
private const val CLAIM_ROLE = "role"
private const val CLAIM_USERNAME = "username"

@Serializable
data class ErrorBody(val code: String, val message: String? = null, val module: String? = null)

/** Parsed, typed shape of the authenticated principal (02 §2). One parse per call. */
data class PrincipalContext(
    val userId: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid?,
    val role: Role
)

/**
 * `null` when the call carries no verified JWT. [principalContext] is the non-null form for
 * handlers, which only run once authentication has passed; the route-scoped guards below hook
 * `AuthenticationChecked`, which fires whether authentication *succeeded or failed*, so they have
 * to ask this instead.
 */
fun ApplicationCall.principalContextOrNull(): PrincipalContext? =
    principal<JWTPrincipal>()?.let { principalContext(it.payload) }

fun ApplicationCall.principalContext(): PrincipalContext =
    principalContext(principal<JWTPrincipal>()!!.payload)

private fun principalContext(payload: com.auth0.jwt.interfaces.Payload): PrincipalContext {
    val userId = Uuid.parse(requireNotNull(payload.subject) { "JWT missing sub claim" })
    val tenantId = Uuid.parse(requireNotNull(payload.getClaim(CLAIM_TENANT_ID).asString()) { "JWT missing tenantId claim" })
    val branchId = payload.getClaim(CLAIM_BRANCH_ID).asString()?.let(Uuid::parse)
    val role = Role.valueOf(requireNotNull(payload.getClaim(CLAIM_ROLE).asString()) { "JWT missing role claim" })
    return PrincipalContext(userId, tenantId, branchId, role)
}

/** Mints the access token minted at login/refresh (02 §2/§3). */
fun mintAccessToken(
    settings: JwtSettings,
    userId: Uuid,
    tenantId: Uuid,
    branchId: Uuid?,
    role: Role,
    username: String
): String {
    val builder = JWT.create()
        .withAudience(settings.audience)
        .withIssuer(settings.issuer)
        .withSubject(userId.toString())
        .withClaim(CLAIM_TENANT_ID, tenantId.toString())
        .withClaim(CLAIM_ROLE, role.name)
        .withClaim(CLAIM_USERNAME, username)
        .withExpiresAt(java.util.Date.from(java.time.Instant.now().plusSeconds(settings.accessTtlMinutes * 60L)))
    branchId?.let { builder.withClaim(CLAIM_BRANCH_ID, it.toString()) }
    return builder.sign(Algorithm.HMAC256(settings.secret))
}

/**
 * `auth-jwt` HS256 provider (02 §2). `authHeader` also accepts `?access_token=` -- browsers'
 * `EventSource` can't set headers, so `GET /events/sse` authenticates the same way.
 */
fun Application.configureAuth() {
    val settings by inject<Settings>()
    val jwt = settings.jwtSettings

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwt.issuer
            verifier(
                JWT.require(Algorithm.HMAC256(jwt.secret))
                    .withAudience(jwt.audience)
                    .withIssuer(jwt.issuer)
                    .build()
            )
            authHeader { call ->
                call.request.parseAuthorizationHeader()
                    ?: call.request.queryParameters["access_token"]?.let {
                        HttpAuthHeader.Single(AuthScheme.Bearer, it)
                    }
            }
            validate { credential ->
                val subject = credential.payload.subject
                val tenantId = credential.payload.getClaim(CLAIM_TENANT_ID).asString()
                val role = credential.payload.getClaim(CLAIM_ROLE).asString()
                val roleKnown = role != null && runCatching { Role.valueOf(role) }.isSuccess
                if (!subject.isNullOrBlank() && !tenantId.isNullOrBlank() && roleKnown) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorBody(code = "TOKEN_INVALID"))
            }
        }
    }
}

/** In-memory role -> permission grid, loaded once at startup (01 §4, 02 §2). */
object PermissionRegistry {
    @Volatile
    private var grid: Map<Role, Set<Permission>> = emptyMap()

    suspend fun refresh(repository: RolePermissionRepository) {
        grid = repository.loadAll()
    }

    fun has(role: Role, permission: Permission): Boolean =
        role == Role.ADMIN || grid[role]?.contains(permission) == true
}

/**
 * 403 `{ "code": "FORBIDDEN" }` when the principal's role lacks [permission] (ADMIN always passes).
 * Hooks `AuthenticationChecked` rather than `onCall` -- the `authenticate("auth-jwt")` wrapper's own
 * check runs in a phase inserted right after `Plugins`, so a plain `onCall` (which is the `Plugins`
 * phase) would run *before* the JWT is actually verified.
 */
fun Route.requirePermission(permission: Permission): Route {
    install(createRouteScopedPlugin("RequirePermission-${permission.name}") {
        on(AuthenticationChecked) { call ->
            // Unauthenticated: the `authenticate` wrapper has already produced its own 401
            // challenge, and this hook still runs. Dereferencing the absent principal here threw
            // an NPE on *every* request with a missing or expired token -- a stack trace per call
            // in the server log, which is how a client stuck refreshing looked like a server
            // fault. Authorization has nothing to say about a caller that was never identified.
            val context = call.principalContextOrNull() ?: return@on
            if (!PermissionRegistry.has(context.role, permission)) {
                call.respond(HttpStatusCode.Forbidden, ErrorBody(code = "FORBIDDEN"))
            }
        }
    })
    return this
}

/**
 * 403 `{ "code": "MODULE_DISABLED", "module": "..." }` when the tenant hasn't enabled [module].
 * `TenantRepository.enabledModules` already caches with a ~30s TTL (02 §2), so this is cheap.
 */
fun Route.requireModule(module: Module): Route {
    install(createRouteScopedPlugin("RequireModule-${module.name}") {
        val tenantRepository by application.inject<TenantRepository>()
        on(AuthenticationChecked) { call ->
            // Same as requirePermission: this hook runs even when authentication failed.
            val context = call.principalContextOrNull() ?: return@on
            val modules = tenantRepository.enabledModules(context.tenantId)
            if (module !in modules) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorBody(code = "MODULE_DISABLED", module = module.name)
                )
            }
        }
    })
    return this
}

/**
 * Handler-level equivalent of [Route.requirePermission] (02 §2/§3). `Route.requirePermission`
 * installs on a whole route node *and every child node beneath it* -- unsafe when sibling HTTP
 * methods (or nested sub-paths) on the very same path need *different* permissions, because Ktor
 * merges repeated `route(path) { ... }` registrations onto the same tree node (verified via
 * context7 docs: "installations for the same route are merged"). Use this inside a handler body
 * instead whenever that's the case (e.g. `catalog/products` GET vs POST). Returns `true` and
 * responds 403 + returns `false` otherwise -- caller must `return@handler` on `false`.
 */
suspend fun ApplicationCall.requirePermissionInline(permission: Permission): Boolean {
    if (!PermissionRegistry.has(principalContext().role, permission)) {
        respond(HttpStatusCode.Forbidden, ErrorBody(code = "FORBIDDEN"))
        return false
    }
    return true
}

/** Handler-level equivalent of [Route.requireModule] -- see [requirePermissionInline]'s doc. */
suspend fun ApplicationCall.requireModuleInline(tenantRepository: TenantRepository, module: Module): Boolean {
    val context = principalContext()
    if (module !in tenantRepository.enabledModules(context.tenantId)) {
        respond(HttpStatusCode.Forbidden, ErrorBody(code = "MODULE_DISABLED", module = module.name))
        return false
    }
    return true
}

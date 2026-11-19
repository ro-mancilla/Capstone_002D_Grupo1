@file:OptIn(ExperimentalUuidApi::class)

package com.f776.ecopan.data.tenant

import com.f776.ecopan.core.domain.auth.Role
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.tenant.AuthRepository
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.LoginOutcome
import com.f776.ecopan.domain.tenant.SessionProvider
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import com.f776.ecopan.domain.tenant.wire.LoginRequestDto
import com.f776.ecopan.domain.tenant.wire.LoginResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * There is no logging facility in this project yet, and enrollment is precisely the place where
 * silence costs the most: it is the only screen that *must* reach the network, and until now every
 * possible cause of failure surfaced as the same "check your connection" message. `println` lands
 * in logcat (`System.out`) on Android and on stdout on desktop, which is enough to name the cause.
 */
private fun log(message: String, error: Throwable? = null) {
    println("AuthRepository: $message${error?.let { " — ${it::class.simpleName}: ${it.message}" } ?: ""}")
    error?.printStackTrace()
}

/**
 * Real `POST /auth/login` call (backend's `AuthRouter`/`AuthDtos` — mirrored field-for-field by
 * `domain.tenant.wire.LoginRequestDto`/`LoginResponseDto`, both `:data:tenant:api`). Replaces the
 * fake local seeding `AuthViewModel.enroll()` used to do (01 §4 / 03 §8).
 *
 * Judgement call (documented, see also `AuthViewModel`'s KDoc): on success this unlocks the
 * session for the just-authenticated user immediately, rather than routing through the local PIN
 * lock screen first. The backend already verified this exact person's password; there is no
 * `pinHash` for them yet (one only arrives later via the `USER` sync-pull applier, not wired as of
 * this work item — see `TenantDataModule`'s KDoc) so there is nothing to verify locally anyway.
 * Other staff still authenticate at the till via local PIN once their own `USER` row has synced.
 */
internal class AuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokenStore: TokenStore,
    private val tenantRepository: TenantRepository,
    private val sessionProvider: SessionProvider,
) : AuthRepository {

    override suspend fun login(tenantSlug: String, username: String, password: String, deviceName: String): LoginOutcome {
        val deviceId = tokenStore.deviceId()
        val response: HttpResponse = try {
            httpClient.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequestDto(tenantSlug = tenantSlug, username = username, password = password, deviceId = deviceId, deviceName = deviceName))
            }
        } catch (e: CancellationException) {
            // Must not be reported as a network failure: the caller's scope is going away, and
            // swallowing it here would break structured concurrency on top of lying to the user.
            throw e
        } catch (e: IOException) {
            log("login: could not reach $baseUrl", e)
            return LoginOutcome.NetworkError
        } catch (e: Exception) {
            // Everything else -- a missing HTTP engine, a serialization failure, a bad URL -- is
            // NOT "you have no internet", and telling the user to check their connection sends
            // them chasing a problem that isn't theirs. Same outcome (the enrollment cannot
            // proceed), but the cause reaches the log instead of dying here silently.
            log("login: request to $baseUrl failed before it could be answered", e)
            return LoginOutcome.NetworkError
        }

        return when {
            response.status.isSuccess() -> {
                val body: LoginResponseDto = response.body()
                applyLoginResponse(tenantSlug, body)
                tokenStore.saveDeviceName(deviceName)
                LoginOutcome.Success(tenantId = Uuid.parse(body.tenant.id), userId = Uuid.parse(body.user.id))
            }
            response.status.value == 401 -> LoginOutcome.InvalidCredentials
            response.status.value == 429 -> LoginOutcome.TooManyAttempts
            else -> LoginOutcome.NetworkError
        }
    }

    private suspend fun applyLoginResponse(tenantSlug: String, body: LoginResponseDto) {
        tokenStore.saveTokens(body.accessToken, body.refreshToken)

        val tenantId = Uuid.parse(body.tenant.id)
        tenantRepository.upsertTenant(
            TenantProfile(
                id = tenantId,
                slug = tenantSlug,
                name = body.tenant.name,
                currencyCode = body.tenant.currencyCode,
                enabledModules = body.tenant.enabledModules.mapNotNullTo(mutableSetOf()) { name ->
                    runCatching { EntitlementModule.valueOf(name) }.getOrNull()
                },
            ),
        )
        tenantRepository.upsertBranches(
            body.branches.map { branch -> BranchUpsert(id = Uuid.parse(branch.id), tenantId = tenantId, name = branch.name, active = true, serverSeq = 0L) },
        )
        val userId = Uuid.parse(body.user.id)
        tenantRepository.upsertUsers(
            listOf(
                UserAccountUpsert(
                    id = userId,
                    tenantId = tenantId,
                    branchId = body.user.branchId?.let(Uuid::parse),
                    username = body.user.displayName,
                    displayName = body.user.displayName,
                    role = Role.valueOf(body.user.role),
                    active = true,
                    // No pinHash yet -- only the USER sync-pull applier carries one (see class KDoc).
                    pinHash = null,
                    serverSeq = 0L,
                ),
            ),
        )
        sessionProvider.unlock(userId)
    }
}

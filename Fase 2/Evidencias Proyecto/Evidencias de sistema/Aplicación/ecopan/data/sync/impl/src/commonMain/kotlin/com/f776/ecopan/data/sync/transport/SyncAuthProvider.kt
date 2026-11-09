package com.f776.ecopan.data.sync.transport

import com.f776.ecopan.domain.tenant.DeviceSessionStore
import com.f776.ecopan.domain.tenant.wire.RefreshRequestDto
import com.f776.ecopan.domain.tenant.wire.RefreshResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException

/**
 * Auth-header source + one-shot refresh for [SyncTransport] (03 §3.4). Per 01 §4, the credential
 * being refreshed is the **device session** — a long-lived token bound to `(tenantId, branchId?,
 * deviceId)`, issued once at first-login enrollment, *not* a per-user login (staff PIN switches
 * never touch this).
 */
internal interface SyncAuthProvider {
    /** `null` means "no token cached" — every request goes out unauthenticated and will 401. */
    suspend fun currentAccessToken(): String?

    /** Attempts one refresh. [SyncRefreshOutcome.REFRESHED] means a (possibly new) token is now available via [currentAccessToken]. */
    suspend fun refresh(): SyncRefreshOutcome
}

/**
 * What a refresh attempt says about the device session (#133). Only [REJECTED] means the session is
 * dead and someone has to sign in again; a throttled (429), failing (5xx) or unreachable server
 * says nothing about the token, so the caller keeps it and retries on its normal schedule.
 */
internal enum class SyncRefreshOutcome { REFRESHED, REJECTED, TRANSIENT }

/** Placeholder used only where no [DeviceSessionStore] is reachable (tests, previously DI wiring). */
internal object NoOpSyncAuthProvider : SyncAuthProvider {
    override suspend fun currentAccessToken(): String? = null
    override suspend fun refresh(): SyncRefreshOutcome = SyncRefreshOutcome.REJECTED
}

/**
 * The real [SyncAuthProvider] (03 work item 9 follow-up: replaces [NoOpSyncAuthProvider] in
 * `SyncDataModule`). Reads the current access token from [DeviceSessionStore] (bound by
 * `:data:tenant:impl`'s `TokenStore` — see that interface's KDoc for the dependency-direction
 * rationale) and, on `refresh()`, calls the real `POST /auth/refresh` directly with its own
 * injected [HttpClient] rather than going through [SyncTransport] — refresh is not itself a sync
 * operation and must not recurse through [SyncTransport]'s own 401 handling.
 */
internal class DeviceSessionSyncAuthProvider(
    private val deviceSessionStore: DeviceSessionStore,
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : SyncAuthProvider {

    /**
     * Refresh is single-flighted. `POST /auth/refresh` **rotates**: the refresh token it consumes
     * is invalidated and a new one is issued, so it succeeds exactly once per token.
     *
     * [PushWorker][com.f776.ecopan.data.sync.push.PushWorker] and
     * [PullWorker][com.f776.ecopan.data.sync.pull.PullWorker] each call [refresh] on their own 401,
     * and the access token expires for both at the same moment — so without this lock the two race,
     * both post the *same* refresh token, one wins and the loser gets a 401 for a token that was
     * valid when it read it. The loser then reports `AuthRequired`, which the engine treats as
     * terminal ("only re-enrollment fixes it"), and a device with a perfectly good session goes
     * dead until someone logs in again.
     */
    private val refreshMutex = Mutex()

    override suspend fun currentAccessToken(): String? = deviceSessionStore.currentAccessToken()

    override suspend fun refresh(): SyncRefreshOutcome {
        val tokenSeenBeforeWaiting = deviceSessionStore.currentAccessToken()
        return refreshMutex.withLock {
            // Somebody refreshed while we queued. Their new access token is the valid one; posting
            // our copy of the refresh token would burn a value the server has already rotated away
            // and report this session dead on a successful refresh.
            if (deviceSessionStore.currentAccessToken() != tokenSeenBeforeWaiting) return@withLock SyncRefreshOutcome.REFRESHED

            val refreshToken = deviceSessionStore.currentRefreshToken() ?: return@withLock SyncRefreshOutcome.REJECTED
            try {
                val response = httpClient.post("$baseUrl/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequestDto(refreshToken))
                }
                val status = response.status
                when {
                    status == HttpStatusCode.TooManyRequests || status.value >= 500 -> SyncRefreshOutcome.TRANSIENT
                    !status.isSuccess() -> SyncRefreshOutcome.REJECTED
                    else -> {
                        // A 200 means the server has already rotated: the token we posted is spent.
                        // If its reply cannot be read, retrying would present a revoked token (and
                        // revoke the whole session family), so this is a rejection, not transient.
                        val body = try {
                            response.body<RefreshResponseDto>()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            return@withLock SyncRefreshOutcome.REJECTED
                        }
                        deviceSessionStore.saveTokens(body.accessToken, body.refreshToken)
                        SyncRefreshOutcome.REFRESHED
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                SyncRefreshOutcome.TRANSIENT
            } catch (e: Exception) {
                // A timeout, a garbled body from a proxy: nothing here proves the token is dead, and
                // calling it rejected would strand the device on "Requiere atención".
                SyncRefreshOutcome.TRANSIENT
            }
        }
    }
}

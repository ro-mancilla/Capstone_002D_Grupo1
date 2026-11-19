package com.f776.ecopan.data.sync.transport

import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.wire.PullResponse
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.ecopan.domain.sync.wire.PushResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.io.IOException

/**
 * The one production [SyncTransport] (03 §3.2/§4.1) -- an injected [HttpClient] carries
 * `ContentNegotiation`/kotlinx-serialization JSON already (see `SyncDataModule`'s `HttpClient`
 * single); this class only knows the two endpoints, the auth header, and how to turn a response
 * into either a parsed DTO or one of [SyncAuthException]/[SyncTransientException]. It never
 * retries or backs off itself -- that policy belongs to `PushWorker`/`PullWorker` (03 §3.3/§3.4),
 * which is also why 401 handling stops at "throw", not "refresh and retry": this class doesn't
 * own a refresh loop, [SyncAuthProvider] does, and the workers own calling it.
 */
internal class KtorSyncTransport(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val authProvider: SyncAuthProvider,
) : SyncTransport {

    override suspend fun push(request: PushRequest): PushResponse = runCatching {
        httpClient.post("$baseUrl/sync/push") {
            contentType(ContentType.Application.Json)
            authorize()
            setBody(request)
        }
    }.fold(
        onSuccess = { it.toResultOrThrow() },
        onFailure = { throw it.toTransportFailure() },
    )

    override suspend fun pull(since: Long, limit: Int, entityTypes: Set<SyncEntityType>?): PullResponse = runCatching {
        httpClient.get("$baseUrl/sync/pull") {
            authorize()
            parameter("since", since)
            parameter("limit", limit)
            entityTypes?.takeIf { it.isNotEmpty() }?.let { types ->
                parameter("entityTypes", types.joinToString(",") { it.name })
            }
        }
    }.fold(
        onSuccess = { it.toResultOrThrow() },
        onFailure = { throw it.toTransportFailure() },
    )

    private suspend fun HttpRequestBuilder.authorize() {
        authProvider.currentAccessToken()?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
    }

    private suspend inline fun <reified T> HttpResponse.toResultOrThrow(): T = when {
        status.isSuccess() -> body()
        status.value == 401 -> throw SyncAuthException("401 Unauthorized")
        status.value in 500..599 -> throw SyncTransientException("server error ${status.value}")
        else -> throw SyncTransientException("unexpected status ${status.value}")
    }

    private fun Throwable.toTransportFailure(): Throwable = when (this) {
        is SyncAuthException, is SyncTransientException -> this
        is IOException -> SyncTransientException("transport IO failure", this)
        else -> SyncTransientException("transport failure", this)
    }
}

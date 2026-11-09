package com.f776.ecopan.data.sync.transport

import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.wire.PullResponse
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.ecopan.domain.sync.wire.PushResponse

/**
 * The fake-transport test seam (03 §1.1/§9): `KtorSyncTransport` is the only production
 * implementation, `FakeSyncTransport` (jvmTest) the only other one. Push/pull *content* failures
 * (a `REJECTED` mutation result, an empty pull page) are ordinary return values, never exceptions
 * — only failures below the wire envelope throw, via [SyncAuthException]/[SyncTransientException],
 * so [PushWorker]/[PullWorker] can tell "the server answered but said no" apart from "we couldn't
 * reach the server at all" (03 §3.3/§3.4).
 */
internal interface SyncTransport {
    /** @throws SyncAuthException on 401. @throws SyncTransientException on IO/5xx/timeout. */
    suspend fun push(request: PushRequest): PushResponse

    /** @throws SyncAuthException on 401. @throws SyncTransientException on IO/5xx/timeout. */
    suspend fun pull(since: Long, limit: Int, entityTypes: Set<SyncEntityType>?): PullResponse
}

/** 401 — the access token this device is sending is no longer valid (03 §3.4). */
internal class SyncAuthException(message: String) : Exception(message)

/** IO failure, timeout, or 5xx — the normal shape of "offline" (03 §3.3), never permanent. */
internal class SyncTransientException(message: String, cause: Throwable? = null) : Exception(message, cause)

package com.f776.ecopan.data.sync.unittest.fake

import com.f776.ecopan.data.sync.transport.SyncAuthException
import com.f776.ecopan.data.sync.transport.SyncTransientException
import com.f776.ecopan.data.sync.transport.SyncTransport
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.wire.PullResponse
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.ecopan.domain.sync.wire.PushResponse

/** Scriptable [SyncTransport] test seam (03 §1.1/§9) wrapping a [FakeServer]. */
internal class FakeSyncTransport(private val server: FakeServer) : SyncTransport {

    /** Throws [SyncTransientException] this many times before delegating to [server] (03 §9 T5). */
    var pushFailuresRemaining = 0

    /** Throws [SyncAuthException] on the next push only (03 §9 T6). */
    var pushUnauthorizedOnce = false

    /**
     * Throws something `PushWorker` does *not* have a branch for, on the next push only -- a
     * serialization bug, a transport that leaks its own exception type. The interesting case is
     * what happens to the batch it had already marked in flight.
     */
    var pushUnexpectedOnce = false

    /** Delivers to [server] (so it's really applied), then throws as if the ack never arrived (03 §9 T3). */
    var dropAckOnNextPush = false

    /** Throws [SyncTransientException] this many times before delegating to [server] (03 §9 T9). */
    var pullFailuresRemaining = 0

    /** 1-based call number (across the transport's whole lifetime) on which [pull] should throw once (03 §9 T9). */
    var failOnPullCallNumber: Int? = null
    private var pullCallCount = 0

    override suspend fun push(request: PushRequest): PushResponse {
        if (pushUnexpectedOnce) {
            pushUnexpectedOnce = false
            throw IllegalStateException("undeclared transport failure (scripted)")
        }
        if (pushUnauthorizedOnce) {
            pushUnauthorizedOnce = false
            throw SyncAuthException("401 (scripted)")
        }
        if (pushFailuresRemaining > 0) {
            pushFailuresRemaining -= 1
            throw SyncTransientException("simulated transient failure (scripted)")
        }
        val response = server.push(request)
        if (dropAckOnNextPush) {
            dropAckOnNextPush = false
            throw SyncTransientException("ack dropped after the server applied it (scripted)")
        }
        return response
    }

    /** Throws [SyncAuthException] on the next pull only -- an expired access token. */
    var pullUnauthorizedOnce = false

    override suspend fun pull(since: Long, limit: Int, entityTypes: Set<SyncEntityType>?): PullResponse {
        pullCallCount += 1
        if (pullUnauthorizedOnce) {
            pullUnauthorizedOnce = false
            throw SyncAuthException("401 (scripted)")
        }
        if (pullFailuresRemaining > 0) {
            pullFailuresRemaining -= 1
            throw SyncTransientException("simulated crash mid-pull (scripted)")
        }
        if (failOnPullCallNumber == pullCallCount) {
            throw SyncTransientException("simulated crash mid-pull (scripted, call #$pullCallCount)")
        }
        return server.pull(since, limit, entityTypes?.map { it.name }?.toSet())
    }
}

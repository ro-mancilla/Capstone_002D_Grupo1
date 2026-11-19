package com.f776.ecopan.data.production.unittest.fake

import com.f776.ecopan.domain.sync.SyncEventStream
import com.f776.ecopan.domain.sync.SyncStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription

/**
 * Scriptable [SyncEventStream]. Counts subscriptions so tests can assert the listener opens exactly
 * one connection — the real stream is a cold flow where each collection is a fresh HTTP connection.
 */
internal class FakeSyncEventStream : SyncEventStream {
    private val events = MutableSharedFlow<SyncStreamEvent>(extraBufferCapacity = 32)

    var subscriptions = 0
        private set

    override fun events(): Flow<SyncStreamEvent> = events.onSubscription { subscriptions += 1 }

    fun emit(type: String, data: String) {
        events.tryEmit(SyncStreamEvent(type, data))
    }
}

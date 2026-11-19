package com.f776.ecopan.data.sync.unittest.fake

import com.f776.ecopan.data.sync.transport.SyncAuthProvider
import com.f776.ecopan.data.sync.transport.SyncRefreshOutcome

internal class FakeSyncAuthProvider(
    private var token: String? = "initial-token",
    refreshSucceeds: Boolean = false,
) : SyncAuthProvider {
    var refreshCallCount = 0
        private set

    /** What the next [refresh] reports. */
    var outcome: SyncRefreshOutcome = if (refreshSucceeds) SyncRefreshOutcome.REFRESHED else SyncRefreshOutcome.REJECTED

    var refreshSucceeds: Boolean
        get() = outcome == SyncRefreshOutcome.REFRESHED
        set(value) {
            outcome = if (value) SyncRefreshOutcome.REFRESHED else SyncRefreshOutcome.REJECTED
        }

    override suspend fun currentAccessToken(): String? = token

    override suspend fun refresh(): SyncRefreshOutcome {
        refreshCallCount += 1
        if (outcome == SyncRefreshOutcome.REFRESHED) token = "refreshed-token-$refreshCallCount"
        return outcome
    }
}

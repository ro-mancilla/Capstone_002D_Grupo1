package com.f776.ecopan.unittest.fake

import com.f776.ecopan.domain.sync.SyncScheduler

internal class FakeSyncScheduler : SyncScheduler {
    var startCalls = 0
        private set
    var stopCalls = 0
        private set

    /** Tracked as state, not `startCalls > stopCalls` — the real scheduler's start/stop are
     *  idempotent, so the counts interleave (a fresh install stops before it ever starts). */
    var running: Boolean = false
        private set

    override fun start() {
        startCalls += 1
        running = true
    }

    override fun stop() {
        stopCalls += 1
        running = false
    }
}

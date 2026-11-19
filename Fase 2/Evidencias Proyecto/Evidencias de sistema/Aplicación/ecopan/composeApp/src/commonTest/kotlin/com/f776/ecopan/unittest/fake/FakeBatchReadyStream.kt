package com.f776.ecopan.unittest.fake

import com.f776.ecopan.domain.production.BatchReadyStream

internal class FakeBatchReadyStream : BatchReadyStream {
    var running: Boolean = false
        private set
    var startCalls = 0
        private set

    override fun start() {
        startCalls += 1
        running = true
    }

    override fun stop() {
        running = false
    }
}

package com.f776.ecopan.data.sync.unittest.fake

import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.ConnectivityStatus
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeConnectivityMonitor(initial: ConnectivityStatus = ConnectivityStatus.ONLINE) : ConnectivityMonitor {
    private val flow = MutableSharedFlow<ConnectivityStatus>(replay = 1)

    init {
        flow.tryEmit(initial)
    }

    override fun observe() = flow

    fun emit(status: ConnectivityStatus) {
        flow.tryEmit(status)
    }
}

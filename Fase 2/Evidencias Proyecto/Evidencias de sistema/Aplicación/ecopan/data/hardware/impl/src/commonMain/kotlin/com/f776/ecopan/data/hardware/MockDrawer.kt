package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.CashDrawer
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.SimulatedDrawer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SIMULATED drawer (04 §2): opens nothing, records an event the till shows -- always CONNECTED. */
internal class MockDrawer : CashDrawer, SimulatedDrawer {

    private val _status = MutableStateFlow(DeviceStatus.CONNECTED)
    override val status = _status.asStateFlow()

    // replay = 1 so a UI that starts collecting after an open already happened still sees it.
    private val _openEvents = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)
    val openEvents: Flow<Unit> = _openEvents

    override suspend fun open() {
        _openEvents.emit(Unit)
    }
}

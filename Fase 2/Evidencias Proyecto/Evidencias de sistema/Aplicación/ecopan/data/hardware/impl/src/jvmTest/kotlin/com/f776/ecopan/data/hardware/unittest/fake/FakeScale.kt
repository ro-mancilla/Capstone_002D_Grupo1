package com.f776.ecopan.data.hardware.unittest.fake

import com.f776.ecopan.data.hardware.ScaleDriverFactory
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.Scale
import com.f776.ecopan.domain.hardware.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal identity-tracking fake — the swap test needs to tell instances apart and see closes. */
internal class FakeScale(val driverId: String, val config: Map<String, String> = emptyMap()) : Scale {
    var started = false
        private set
    var closed = false
        private set

    private val _status = MutableStateFlow(DeviceStatus.DISCONNECTED)
    override val status = _status.asStateFlow()
    override val readings: Flow<WeightReading> = MutableSharedFlow()

    override fun start() {
        started = true
        _status.value = DeviceStatus.CONNECTED
    }

    override fun close() {
        closed = true
    }
}

internal class FakeScaleDriverFactory(private val id: String) : ScaleDriverFactory {
    override val descriptor = DriverDescriptor(id, id, platformOnly = false)
    override fun create(config: Map<String, String>): Scale = FakeScale(id, config)
}

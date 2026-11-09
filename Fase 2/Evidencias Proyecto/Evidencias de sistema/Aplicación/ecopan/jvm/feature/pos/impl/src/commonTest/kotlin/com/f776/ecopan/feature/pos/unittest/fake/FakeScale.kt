package com.f776.ecopan.feature.pos.unittest.fake

import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.ManualWeightEntry
import com.f776.ecopan.domain.hardware.ProductWeightScript
import com.f776.ecopan.domain.hardware.Scale
import com.f776.ecopan.domain.hardware.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

internal class FakeScale : Scale, ManualWeightEntry, ProductWeightScript {
    val selectedProducts = mutableListOf<String>()

    private val _status = MutableStateFlow(DeviceStatus.CONNECTED)
    override val status = _status.asStateFlow()

    private val _readings = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 8)
    override val readings: Flow<WeightReading> = _readings

    var closed = false
        private set

    override fun start() {
        _status.value = DeviceStatus.CONNECTED
    }

    override fun close() {
        closed = true
        _status.value = DeviceStatus.DISCONNECTED
    }

    override fun onProductSelected(productName: String) {
        selectedProducts += productName
    }

    override fun submitWeight(grams: Long) {
        emit(grams, stable = true)
    }

    fun emit(grams: Long, stable: Boolean) {
        _readings.tryEmit(WeightReading(grams, stable, Clock.System.now()))
    }
}

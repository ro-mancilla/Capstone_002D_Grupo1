package com.f776.ecopan.domain.hardware

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * `MockScale`/`ManualScale` are first-class supported modes (04 §2), not stubs — a scale-less
 * till is a fully supported deployment. `SerialScale` (JVM only) is the real-hardware driver.
 */
interface Scale : AutoCloseable {
    /** Hot stream; emits readings while active. POS consumes only `stable == true` for sale lines. */
    val readings: Flow<WeightReading>
    val status: StateFlow<DeviceStatus>

    /** Idempotent — opens the port / starts the script / arms manual input. */
    fun start()
    override fun close()
}

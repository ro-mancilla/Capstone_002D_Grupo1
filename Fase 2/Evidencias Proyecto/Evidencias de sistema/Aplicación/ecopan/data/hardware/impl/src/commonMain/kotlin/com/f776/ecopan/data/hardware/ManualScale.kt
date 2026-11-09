package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.ManualWeightEntry
import com.f776.ecopan.domain.hardware.Scale
import com.f776.ecopan.domain.hardware.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * A real supported mode (04 §2) for un-integrated scales, and the mid-shift fallback when a
 * serial scale disconnects — not a stub. [submitWeight] is the single home for numeric validation
 * (rejects non-positive / implausibly large grams); the kg-string parsing (up to 3 decimals,
 * half-up to grams) happens in the POS UI layer's own calculator, which then calls this with the
 * already-converted integer. Never enters ERROR.
 */
internal class ManualScale(private val config: Map<String, String>) : Scale, ManualWeightEntry {

    private val _status = MutableStateFlow(DeviceStatus.DISCONNECTED)
    override val status = _status.asStateFlow()

    // replay = 1 so a UI that starts observing right as a weight comes in still sees it, rather
    // than racing the subscription against the emission.
    private val _readings = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 64)
    override val readings: Flow<WeightReading> = _readings

    private val maxGrams: Long = config[MAX_GRAMS_KEY]?.toLongOrNull() ?: DEFAULT_MAX_GRAMS

    override fun start() {
        _status.value = DeviceStatus.CONNECTED
    }

    override fun close() {
        _status.value = DeviceStatus.DISCONNECTED
    }

    /** Consumed by the POS weight-entry field when the active driver is manual (05 §3 item 2). */
    override fun submitWeight(grams: Long) {
        require(grams > 0) { "manual weight must be positive, was $grams" }
        require(grams <= maxGrams) { "manual weight $grams exceeds the configured max $maxGrams" }
        _readings.tryEmit(WeightReading(grams, stable = true, at = Clock.System.now()))
    }

    private companion object {
        const val MAX_GRAMS_KEY = "maxGrams"
        const val DEFAULT_MAX_GRAMS = 50_000L
    }
}

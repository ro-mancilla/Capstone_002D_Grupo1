@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.ProductWeightScript
import com.f776.ecopan.domain.hardware.RawTextSource
import com.f776.ecopan.domain.hardware.Scale
import com.f776.ecopan.domain.hardware.WeightReading
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Dev/demo driver (04 §2) — a first-class supported mode, not a stub. Two modes via [config]:
 * `mode=scripted` replays `config["sequence"]` (comma-separated grams, deterministic — for the
 * stage demo and screenshots); anything else (including no config) runs `random` — plausible
 * bakery weights with a ramp-then-settle sequence. Each value settles, then *holds* stable for
 * `holdMs` (default [SETTLE_HOLD_MS], seconds) before the next one, so a cashier can actually sell
 * the reading. Status is always CONNECTED; it never enters ERROR.
 *
 * On top of either mode, a [DemoWeightScript] gives named by-weight products a fixed
 * ramp-then-settle: [onProductSelected] cancels whatever is running, drops the previous product's
 * last reading and plays that product's sequence once. A product the script does not know restarts
 * the mode above. SIMULATED -- not evidence about any physical scale.
 *
 * [loadScript] does blocking file I/O, so it runs lazily, once, on [ioDispatcher] the first time a
 * product is selected -- never on the thread that created the driver. Every run carries a
 * generation number and a reading is only emitted while its run is still current, so a cancelled
 * run (or one outliving [close]) cannot put a stale weight in front of the cashier.
 */
internal class MockScale(
    private val config: Map<String, String>,
    private val defaultDispatcher: CoroutineDispatcher,
    ioDispatcher: CoroutineDispatcher,
    loadScript: () -> DemoWeightScript,
) : Scale, ProductWeightScript, RawTextSource {

    private val _status = MutableStateFlow(DeviceStatus.CONNECTED)
    override val status = _status.asStateFlow()

    // replay = 1 so a UI that starts observing right as a reading comes in still sees it, rather
    // than racing the subscription against the emission.
    private val _readings = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 64)
    override val readings: Flow<WeightReading> = _readings

    // What a real scale would have put on the wire for the latest reading, so the live test panel
    // has something honest to show in demo mode.
    private val _lastRawText = MutableStateFlow<String?>(null)
    override val lastRawText: StateFlow<String?> = _lastRawText.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val script: Deferred<DemoWeightScript> =
        scope.async(ioDispatcher, start = CoroutineStart.LAZY) { loadScript() }
    private var job: Job? = null
    private val holdMs: Long = config[HOLD_KEY]?.toLongOrNull()?.coerceAtLeast(0) ?: SETTLE_HOLD_MS

    @Volatile
    private var generation = 0

    @Volatile
    private var closed = false

    override fun start() {
        if (closed || job?.isActive == true) return
        launchRun { gen -> runDefault(gen) }
    }

    override fun onProductSelected(productName: String) {
        if (closed) return
        // The previous product's last stable weight must not reach whoever observes next.
        _readings.resetReplayCache()
        launchRun { gen ->
            val sequence = script.await().sequenceFor(productName)
            // An unscripted product restarts the default, in case a scripted one had interrupted it.
            if (sequence == null) runDefault(gen) else playScripted(gen, sequence)
        }
    }

    override fun close() {
        closed = true
        generation++
        job?.cancel()
        job = null
        scope.coroutineContext[Job]?.cancel()
    }

    private fun launchRun(body: suspend (gen: Int) -> Unit) {
        val gen = ++generation
        job?.cancel()
        job = scope.launch { body(gen) }
    }

    private suspend fun emitReading(gen: Int, grams: Long, stable: Boolean) {
        if (gen != generation || closed) return
        val raw = frameText(grams, stable)
        _lastRawText.value = raw
        _readings.emit(WeightReading(grams, stable, at = Clock.System.now(), rawText = raw))
    }

    private fun frameText(grams: Long, stable: Boolean): String =
        "${if (stable) "ST" else "US"},GS, ${grams / 1000}.${(grams % 1000).toString().padStart(3, '0')} kg"

    private suspend fun runDefault(gen: Int) {
        if (config[MODE_KEY] == MODE_SCRIPTED) runScripted(gen) else runRandom(gen)
    }

    private suspend fun playScripted(gen: Int, sequence: List<Long>) {
        sequence.forEachIndexed { index, grams ->
            val last = index == sequence.lastIndex
            emitReading(gen, grams, stable = last)
            if (!last) delay(STEP_DELAY_MS)
        }
    }

    private suspend fun runScripted(gen: Int) {
        val sequence = config[SEQUENCE_KEY]
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
        for (grams in sequence) {
            emitRampThenSettle(gen, grams)
            delay(holdMs)
        }
    }

    private suspend fun runRandom(gen: Int) {
        while (true) {
            emitRampThenSettle(gen, Random.nextLong(MIN_GRAMS, MAX_GRAMS))
            delay(holdMs)
        }
    }

    private suspend fun emitRampThenSettle(gen: Int, target: Long) {
        repeat(UNSTABLE_STEPS) {
            val jittered = (target + Random.nextLong(-JITTER_GRAMS, JITTER_GRAMS + 1)).coerceAtLeast(0)
            emitReading(gen, jittered, stable = false)
            delay(STEP_DELAY_MS)
        }
        emitReading(gen, target, stable = true)
    }

    private companion object {
        const val MODE_KEY = "mode"
        const val MODE_SCRIPTED = "scripted"
        const val SEQUENCE_KEY = "sequence"
        const val HOLD_KEY = "holdMs"
        const val MIN_GRAMS = 80L
        const val MAX_GRAMS = 2_500L
        const val JITTER_GRAMS = 40L
        const val UNSTABLE_STEPS = 3
        const val STEP_DELAY_MS = 40L
        const val SETTLE_HOLD_MS = 6_000L
    }
}

package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.DemoWeightScript
import com.f776.ecopan.data.hardware.MockScale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class MockScaleRandomModeTest : ScaleContractTest() {
    override fun createScale() = MockScale(emptyMap(), Dispatchers.Default, Dispatchers.Default) { DemoWeightScript.EMPTY }
}

internal class MockScaleScriptedModeTest : ScaleContractTest() {
    override fun createScale() = MockScale(
        mapOf("mode" to "scripted", "sequence" to "500,842", "holdMs" to "0"),
        Dispatchers.Default,
        Dispatchers.Default,
    ) { DemoWeightScript.EMPTY }
}

/** All timing here is virtual: the test dispatcher stands in for both the default and the IO dispatcher. */
internal class MockScaleTest {

    private fun TestScope.scale(
        script: String = "",
        config: Map<String, String> = emptyMap(),
        loads: MutableList<Unit> = mutableListOf(),
    ): MockScale {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return MockScale(config, dispatcher, dispatcher) {
            loads += Unit
            DemoWeightScript.parse(script)
        }
    }

    @Test
    fun `selecting a scripted product plays its ramp then settles on the last weight`() = runTest {
        val scale = scale("Marraqueta: 300, 600, 842")
        val collected = async(start = CoroutineStart.UNDISPATCHED) { scale.readings.take(3).toList() }

        scale.onProductSelected("Marraqueta")
        advanceUntilIdle()

        val readings = collected.await()
        assertEquals(listOf(300L, 600L, 842L), readings.map { it.grams })
        assertEquals(listOf(false, false, true), readings.map { it.stable })
        scale.close()
    }

    @Test
    fun `a product with no script keeps the ordinary behaviour`() = runTest {
        val scale = scale("Marraqueta: 842")
        scale.start()

        scale.onProductSelected("Berlín")
        val stable = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10.seconds) { scale.readings.first { it.stable } } }
        // The random run never ends, so advance a bounded stretch of virtual time instead of to idle.
        advanceTimeBy(1.seconds)

        assertTrue(stable.await().grams > 0)
        scale.close()
    }

    @Test
    fun `selecting a scripted product discards the previous product's last reading`() = runTest {
        val scale = scale("Marraqueta: 842\nHallulla: 500")
        scale.onProductSelected("Marraqueta")
        advanceUntilIdle()
        // 842 is now the replayed last reading. Switching product must drop it *synchronously*, before
        // anyone observes -- the new run has not even started yet, so only the reset can keep it away.
        scale.onProductSelected("Hallulla")
        val observed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10.seconds) { scale.readings.first { it.stable } } }
        advanceUntilIdle()

        assertEquals(500L, observed.await().grams)
        scale.close()
    }

    @Test
    fun `switching product mid-ramp never emits the old product's remaining weights`() = runTest {
        val scale = scale("Marraqueta: 300, 600, 842\nHallulla: 500")
        val seen = mutableListOf<Long>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { scale.readings.collect { seen += it.grams } }

        scale.onProductSelected("Marraqueta")
        runCurrent()
        scale.onProductSelected("Hallulla")
        advanceUntilIdle()
        runCurrent() // the collector lives in backgroundScope, which advanceUntilIdle does not wait for

        assertEquals(listOf(300L, 500L), seen)
        scale.close()
    }

    @Test
    fun `a closed scale emits nothing more, even for a product selected afterwards`() = runTest {
        val scale = scale("Marraqueta: 300, 600, 842")
        val seen = mutableListOf<Long>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { scale.readings.collect { seen += it.grams } }
        scale.onProductSelected("Marraqueta")
        runCurrent()

        scale.close()
        scale.onProductSelected("Marraqueta")
        advanceUntilIdle()
        runCurrent()

        assertEquals(listOf(300L), seen)
    }

    @Test
    fun `the script is not loaded until a product is selected, and only once`() = runTest {
        val loads = mutableListOf<Unit>()
        val scale = scale("Hallulla: 500", loads = loads)

        scale.start()
        runCurrent()
        assertEquals(0, loads.size)

        scale.onProductSelected("Hallulla")
        advanceUntilIdle()
        scale.onProductSelected("Hallulla")
        advanceUntilIdle()

        assertEquals(1, loads.size)
        scale.close()
    }

    @Test
    fun `scripted mode replays the exact configured sequence in order`() = runTest {
        val scale = scale(config = mapOf("mode" to "scripted", "sequence" to "500, 842"))
        val readings = async(start = CoroutineStart.UNDISPATCHED) { scale.readings.take(8).toList() }

        scale.start()
        advanceUntilIdle()

        // 3 unstable ramp steps + 1 stable settle per scripted value.
        assertEquals(listOf(500L, 842L), readings.await().filter { it.stable }.map { it.grams })
        scale.close()
    }

    @Test
    fun `a settled reading holds stable for seconds before the next value`() = runTest {
        val scale = scale(config = mapOf("mode" to "scripted", "sequence" to "500,842"))
        val stableAt = mutableListOf<Pair<Long, Long>>() // grams to virtual ms
        val collector = launch(StandardTestDispatcher(testScheduler)) {
            scale.readings.collect { if (it.stable) stableAt += it.grams to testScheduler.currentTime }
        }
        scale.start()
        advanceUntilIdle()

        assertEquals(listOf(500L, 842L), stableAt.map { it.first })
        val held = stableAt[1].second - stableAt[0].second
        assertTrue(held >= 3_000, "a stable reading must be sellable for seconds, was ${held}ms")
        collector.cancel()
        scale.close()
    }

    @Test
    fun `exposes the last frame it would have sent, verbatim, for the live test`() = runTest {
        val scale = MockScale(mapOf("mode" to "scripted", "sequence" to "842"), Dispatchers.Default, Dispatchers.Default) { DemoWeightScript.EMPTY }
        scale.start()

        scale.readings.take(4).toList()

        assertEquals("ST,GS, 0.842 kg", scale.lastRawText.value)
        scale.close()
    }
}

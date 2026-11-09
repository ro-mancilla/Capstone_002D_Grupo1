package com.f776.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How much the Baseline Profile buys at cold start: the same cold start of an enrolled device with
 * [CompilationMode.None] (nothing precompiled, what a fresh install without a profile runs like) and
 * with [CompilationMode.Partial] requiring the profile the app ships.
 *
 * ```
 * ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
 * ```
 *
 * It runs on the app's `benchmarkRelease` build (minified like release, profileable, debug-signed),
 * against the same local backend as the generator (see BaselineProfileGenerator): the device is
 * enrolled once, outside the measurement, through the dev quick-login row, and every measured start is
 * that device's, ending when batch entry has drawn. A physical device gives numbers worth quoting;
 * an emulator shares the host's resources.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    private val targetAppId: String = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId not passed as instrumentation runner arg")

    @Before
    fun enrolTheDevice() = ensureEnrolled(targetAppId)

    @Test
    fun startupCompilationNone() = benchmark(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfile() = benchmark(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun benchmark(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // Past the splash, to content: an enrolled device opens on batch entry.
        device.waitFor(SYNC_INDICATOR)
    }

    private companion object {
        const val ITERATIONS = 10
    }
}

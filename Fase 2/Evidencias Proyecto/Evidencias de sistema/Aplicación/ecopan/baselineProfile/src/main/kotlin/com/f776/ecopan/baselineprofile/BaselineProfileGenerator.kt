package com.f776.ecopan.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baker app's Baseline Profile and Startup Profile from its critical journeys: cold
 * start, enrolling a device and logging a production batch, and a sync.
 *
 * ```
 * ./gradlew :android:app:generateBaselineProfile
 * ```
 *
 * The result lands in android/app/src/main/generated/baselineProfiles (baseline-prof.txt and
 * startup-prof.txt), which is committed, so every build ships it. It needs a device on API 33 or later,
 * or a rooted one, and a backend:
 *
 *  - the seeded stack on http://localhost:8080 (the app's default backend), the `reference` tenant,
 *    with opening stock and a forecast (`scripts/demo-seed.py opening-stock|forecast`). A stack
 *    listening on another port is reached with `adb reverse tcp:8080 tcp:<port>`.
 *  - the dev quick-login row, which the journeys use to enrol. It is on by default
 *    (`-Pecopan.devQuickLogin`) and the generator builds the `nonMinifiedRelease` variant with the
 *    BuildKonfig defaults, so no flags are needed. Do not pass `-Pecopan.backendUrl` or turn quick login off:
 *    the journeys would not find the enrolment shortcut, or would not reach the backend.
 *
 * Those two defaults are exactly what `checkReleaseConfiguration` refuses for a release, and the guard
 * does not apply here on purpose: `nonMinifiedRelease` and `benchmarkRelease` are debug-signed local
 * builds that never leave the machine, and only `assembleRelease` / `bundleRelease` are guarded. The
 * committed profile is a list of method names; it carries neither the backend address nor a password.
 *
 * Only [startup] goes into the Startup Profile, which lays out the dex so that what starting the app
 * needs sits together in the first file: the other journeys, which reach into enrolment and the
 * production screens, would dilute it.
 *
 * The selectors are Compose test tags, exposed as resource ids by the app's MainActivity
 * (`testTagsAsResourceId`). Each journey starts from the same enrolled device: [ensureEnrolled] enrols
 * it once, outside the profile.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val targetAppId: String = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId not passed as instrumentation runner arg")

    @Before
    fun enrolTheDevice() = ensureEnrolled(targetAppId)

    /** Journey 1: cold start of an enrolled device, to the screen a baker works on. */
    @Test
    fun startup() = rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
        coldStartToBatchEntry()
    }

    /** Journey 2: enrolling a device with the quick login, then logging the first batch. */
    @Test
    fun enrolAndLogBatch() = rule.collect(packageName = targetAppId, includeInStartupProfile = false) {
        enrolFromScratch()
        logBatch(weightKeys = "25")
    }

    /** Journey 3: a batch waiting in the outbox, pushed by a sync, and the stock it moved. */
    @Test
    fun sync() = rule.collect(packageName = targetAppId, includeInStartupProfile = false) {
        coldStartToBatchEntry()
        logBatch(weightKeys = "40")
        syncNow()
        openStockGlanceAndBack()
    }

    /** Picks the first product, keys in [weightKeys] and confirms; an unusual weight needs a second tap. */
    private fun MacrobenchmarkScope.logBatch(weightKeys: String) {
        device.waitFor(BATCH_PRODUCT).click()
        weightKeys.forEach { key -> device.waitFor("batch_key_$key").click() }
        repeat(CONFIRM_ATTEMPTS) {
            device.waitFor(BATCH_CONFIRM).click()
            if (device.wait(Until.hasObject(By.res(BATCH_LOGGED)), LOGGED_WAIT_MS)) return
        }
        error("$BATCH_LOGGED never showed")
    }

    /** The sync pill in the header runs a sync when tapped. */
    private fun MacrobenchmarkScope.syncNow() {
        device.waitFor(SYNC_INDICATOR).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openStockGlanceAndBack() {
        // Below the keypad, and only offered while the branch has the stock module on. Scrolled to the very
        // end, where it sits clear of the navigation bar: a batch just logged has made the page longer.
        repeat(2) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, SWIPE_STEPS)
            device.waitForIdle()
        }
        val link = device.wait(Until.findObject(By.res(BATCH_STOCK_GLANCE_LINK)), UI_TIMEOUT_MS) ?: return
        link.click()
        check(device.wait(Until.gone(By.res(BATCH_STOCK_GLANCE_LINK)), UI_TIMEOUT_MS)) { "Stock glance never opened" }
        device.waitForIdle()
        device.pressBack()
        // Back on batch entry, which does not keep its scroll position.
        device.waitFor(SYNC_INDICATOR)
    }

    private companion object {
        const val CONFIRM_ATTEMPTS = 2
        const val LOGGED_WAIT_MS = 4_000L
        const val SWIPE_STEPS = 30
    }
}

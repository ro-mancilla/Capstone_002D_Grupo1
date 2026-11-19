package com.f776.ecopan.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/*
 * A copy of this file lives in :benchmark (Journeys.kt there); keep the two alike. They are separate
 * modules that cannot depend on each other, and neither can see the app's screens, so the selectors
 * below are the Compose test tags those screens set (BatchEntryScreen, AuthScreen), spelled out.
 *
 * What these steps lean on:
 *  - a backend on http://localhost:8080 with the seeded `reference` tenant (`adb reverse tcp:8080
 *    tcp:<port>` when the stack listens elsewhere), because the app is offline-first but a device is
 *    enrolled by an online login;
 *  - the build having the dev quick-login row (BuildKonfig `ecopan.devQuickLogin`, default ON), which
 *    enrols as the seeded baker in one tap;
 *  - the baker's seeded PIN, which is what unlocks a device that is already enrolled.
 */

/** The seeded baker's PIN (README, "Sign in"). */
private const val BAKER_PIN = "2222"

const val QUICK_LOGIN_BAKER = "dev_quick_login_BAKER"
const val STAFF_CARD_BAKER = "staff_card_BAKER"
const val SYNC_INDICATOR = "sync_indicator"
const val BATCH_CONFIRM = "batch_confirm"
const val BATCH_LOGGED = "batch_logged"
const val BATCH_STOCK_GLANCE_LINK = "batch_stock_glance_link"
val BATCH_PRODUCT: Pattern = Pattern.compile("batch_product_.*")

const val UI_TIMEOUT_MS = 10_000L

/** Enrolment is a login plus the first full pull, so it gets longer than a tap does. */
const val ENROL_TIMEOUT_MS = 60_000L

/**
 * Cold-starts the app and gets to the batch-entry screen, which is where an enrolled device opens (the
 * session survives the process; the PIN lock shows only when there is no signed-in user, and is handled).
 */
fun MacrobenchmarkScope.coldStartToBatchEntry() {
    pressHome()
    startActivityAndWait()
    device.unlockIfLocked()
}

/**
 * Enrols this device as the seeded baker from scratch: the app's data is wiped first, so the journey
 * always starts at the enrolment screen, and the quick-login row does the login and the first pull.
 */
fun MacrobenchmarkScope.enrolFromScratch() {
    // Wiping the data also stops the process, so the launch below is a cold one.
    device.executeShellCommand("pm clear $packageName")
    pressHome()
    startActivityAndWait()
    device.enrolAsBaker()
}

/** From the enrolment screen: one tap on the baker's quick login, then waits out the bootstrap. */
fun UiDevice.enrolAsBaker() {
    val quickLogin = wait(Until.findObject(By.res(QUICK_LOGIN_BAKER)), UI_TIMEOUT_MS)
        ?: error("The enrolment screen never showed $QUICK_LOGIN_BAKER: is this a build with -Pecopan.devQuickLogin=true?")
    quickLogin.click()
    // Past the bootstrap screen the new device is signed in as the baker already: no PIN.
    waitFor(SYNC_INDICATOR, ENROL_TIMEOUT_MS)
}

/** Waits for the batch-entry screen; on the lock screen, first picks the baker and types the PIN. */
fun UiDevice.unlockIfLocked() {
    val first = waitFor(Pattern.compile("$SYNC_INDICATOR|$STAFF_CARD_BAKER"))
    if (first.resourceName == SYNC_INDICATOR) return
    first.click()
    BAKER_PIN.forEach { digit -> waitFor("pin_key_$digit").click() }
    waitFor(SYNC_INDICATOR)
}

fun UiDevice.waitFor(tag: String, timeoutMs: Long = UI_TIMEOUT_MS): UiObject2 =
    wait(Until.findObject(By.res(tag)), timeoutMs) ?: error("$tag never showed")

fun UiDevice.waitFor(tag: Pattern, timeoutMs: Long = UI_TIMEOUT_MS): UiObject2 =
    wait(Until.findObject(By.res(tag)), timeoutMs) ?: error("$tag never showed")

/**
 * Makes sure the device under test is enrolled, outside any measurement: starts the app, enrols if it
 * shows the enrolment screen, and stops it again. What the journeys then do is a cold start of an
 * enrolled device, which is every start but the first one ever.
 */
fun ensureEnrolled(packageName: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.pressHome()
    // monkey launches the launcher activity without needing the package to be visible to this process.
    device.executeShellCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    // The enrolment screen, or the screen of an enrolled device, whichever the app opens on.
    val first = device.waitFor(Pattern.compile("$QUICK_LOGIN_BAKER|$SYNC_INDICATOR|$STAFF_CARD_BAKER"))
    if (first.resourceName == QUICK_LOGIN_BAKER) device.enrolAsBaker()
    device.executeShellCommand("am force-stop $packageName")
}

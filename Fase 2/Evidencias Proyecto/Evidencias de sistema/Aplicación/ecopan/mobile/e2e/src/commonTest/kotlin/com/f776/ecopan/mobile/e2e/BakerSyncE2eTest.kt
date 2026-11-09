@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.mobile.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Stories 78, 80-82: the baker's phone and a backend that goes away, throttles, or refuses a batch. The runner's
 * control server stops and starts the throwaway API; the phone only ever sees a network that does not answer.
 */
class BakerSyncE2eTest {

    private fun journey(timeout: Int = 400, block: BakerHarness.() -> Unit) = runComposeUiTest(testTimeout = timeout.seconds) {
        val baker = BakerHarness(this)
        try {
            baker.launch()
            baker.ensureEnrolled()
            baker.block()
        } finally {
            runBlocking { runCatching { BakerBackend.startApi() } }
            baker.close()
        }
    }

    @Test
    fun aBatchLoggedOfflineQueuesAndSyncsWhenThePhoneIsBackOnline() = journey {
        val before = runBlocking { BakerBackend.batchCount() }
        waitForText("Sincronizado", substring = true, timeoutMs = 90_000)
        runBlocking { BakerBackend.stopApi() }
        logBatch("Marraqueta", "20")
        // Queued on the phone: the batch is listed as pending and the indicator says so; the backend knows nothing.
        assertText("Pendiente")
        // The network is up but the server does not answer: the pill asks for attention rather than claiming "Sincronizado".
        await("the indicator to stop saying it is synced", 60_000) {
            has("Requiere atención") || has("Sin conexión", substring = true)
        }
        runBlocking { BakerBackend.startApi() }
        // It goes out on its own once the backend is back, exactly once.
        await("no batch left pending", 120_000) { !has("Pendiente") }
        assertEquals(before + 1, runBlocking { BakerBackend.awaitBatches(before + 1) })
    }

    @Test
    fun aThrottledBackendNeverMakesTheBakerSignInAgain() = journey(timeout = 500) {
        // Sign-in is throttled for longer than the one-minute access token lives. Whatever the phone does meanwhile
        // (refresh, push) the throttle says nothing about the session: it stays signed in as the baker, never asks to
        // enroll or reconnect, and its batch reaches the backend.
        val before = runBlocking { BakerBackend.batchCount() }
        val refusedBefore = runBlocking { BakerBackend.refreshesThrottled() }
        var hammer: Job? = null
        try {
            // The bucket refills in one go every minute, so "throttled right now" is a race; "throttled at some point
            // while the access token expired" is the fact.
            var throttled = false
            val drainUntil = currentMillis() + THROTTLE_WINDOW_MS
            while (currentMillis() < drainUntil) {
                if (runBlocking { BakerBackend.wrongLogin() } == 429) throttled = true
                assertNoText("Registrar este dispositivo")
                sleep(1_000)
            }
            check(throttled) { "the sign-in throttle never engaged" }
            // The phone's access token is a minute old by now, so logging a batch makes it refresh. Keep the bucket empty
            // through the push (twice the refill rate) so that refresh is the one that gets refused.
            hammer = CoroutineScope(Dispatchers.Default).launch {
                while (isActive) { runCatching { BakerBackend.wrongLogin() }; delay(500) }
            }
            logBatch("Marraqueta", "20")
            assertNoText("Registrar este dispositivo")
            assertNoText("Reconectar", substring = true)
            await("the backend to refuse the phone's token refresh (429)", 60_000) {
                runBlocking { BakerBackend.refreshesThrottled() } > refusedBefore
            }
            // Refused, and the phone neither signed out nor offers to reconnect: the batch waits. (The pill may say
            // "Requiere atención" while the push backs off; that is how every failed push with the network up reads. The
            // dead-session signs are the enroll screen and the reconnect form.)
            assertNoText("Registrar este dispositivo")
            assertNoText("Reconectar", substring = true)
        } finally {
            hammer?.cancel()
            awaitThrottleRelease() // leave the bucket empty for whoever runs next
        }
        await("no batch left pending", 120_000) { !has("Pendiente") }
        assertEquals(before + 1, runBlocking { BakerBackend.awaitBatches(before + 1) })
        assertNoText("Requiere atención")
        assertNoText("Registrar este dispositivo")
    }

    private fun awaitThrottleRelease() {
        val deadline = currentMillis() + THROTTLE_WINDOW_MS
        while (runBlocking { BakerBackend.wrongLogin() } == 429) {
            check(currentMillis() < deadline) { "the sign-in throttle never released" }
            sleep(10_000) // each probe is itself a failed sign-in: probe rarely so the bucket can drain
        }
    }

    @Test
    fun aRejectedBatchIsListedInSettingsAndCanBeRetriedOrDiscarded() = journey {
        if (!settingsAvailable) return@journey // see settingsAvailable: iOS has no app bundle in a test process
        // Marraqueta is re-filed under Almacén behind the phone's back (its catalog is not refreshed until the next
        // sync); the server refuses batches of what is bought in. Put back afterwards, whatever happens.
        val name = "Marraqueta"
        try {
            runBlocking { BakerBackend.setOrigin(name, "ALMACEN") }
            logBatch(name, "20")
            // Today's list marks it as refused by the server, not pending and not synced.
            waitForText("Rechazado", timeoutMs = 90_000)

            click("Ajustes")
            waitForText("Operaciones rechazadas", timeoutMs = 90_000)
            assertText("Lote de producción")
            assertText("Motivo", substring = true)
            // Retrying sends it again; the cause is still there, so it is refused again and stays listed.
            click("Reintentar")
            waitForText("Operaciones rechazadas", timeoutMs = 60_000)
            // The batch and the four ingredient movements that hang off it are refused together. Discarding asks first,
            // then removes one at a time until the section is gone.
            repeat(8) {
                if (has("Operaciones rechazadas")) {
                    click("Descartar")
                    click("Descartar operación")
                }
            }
            await("every rejected operation discarded") { !has("Operaciones rechazadas") }
        } finally {
            runBlocking { BakerBackend.setOrigin(name, "PANADERIA") }
        }
    }
}

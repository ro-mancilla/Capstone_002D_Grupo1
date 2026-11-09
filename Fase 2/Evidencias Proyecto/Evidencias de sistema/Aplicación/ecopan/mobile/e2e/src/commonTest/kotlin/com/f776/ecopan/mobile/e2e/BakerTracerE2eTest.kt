@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.mobile.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

/** Stories 73 and 74: enroll the phone as the baker, log a batch from the keypad, and the batch reaches the backend. */
class BakerTracerE2eTest {

    @Test
    fun enrollThePhoneAndLogABatch() = runComposeUiTest(testTimeout = 300.seconds) {
        val baker = BakerHarness(this)
        try {
            val before = runBlocking { BakerBackend.batchCount() }
            baker.launch()
            baker.ensureEnrolled()

            // Enrolling signs the baker in; the first pull brings the catalog, then a product, the keypad, confirm.
            baker.clickTagStartingWith("batch_product_")
            "25".forEach { baker.clickTag("batch_key_$it") }
            // An unusual weight needs a second tap to confirm.
            repeat(2) { if (!baker.hasTag("batch_logged", 1_000)) baker.clickTag("batch_confirm") }
            baker.waitForTag("batch_logged", 10_000)

            assertEquals(before + 1, runBlocking { BakerBackend.awaitBatches(before + 1) })
        } finally {
            baker.close()
        }
    }
}

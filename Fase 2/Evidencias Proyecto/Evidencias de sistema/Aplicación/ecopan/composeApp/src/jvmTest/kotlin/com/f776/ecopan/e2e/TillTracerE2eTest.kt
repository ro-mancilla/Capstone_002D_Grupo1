@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Stories 46, 54, 58: enroll the till, sell one unit product in cash. */
class TillTracerE2eTest {

    @Test
    fun enrollUnlockAndSellInCash() = runComposeUiTest(testTimeout = 300.seconds) {
        val till = TillHarness(this, TillInstall())
        val cashBefore = E2eBackend.salesCount("CASH")
        try {
            till.launch()

            // Enroll with a branch manager's credentials.
            till.waitForText("Registrar este dispositivo")
            till.type("Panadería (identificador)", "reference")
            till.type("Usuario", "jefelocal")
            till.type("Contraseña", "jefelocal123")
            till.type("Nombre del dispositivo", "Caja e2e")
            till.click("Registrar dispositivo")

            // Enrolling signs the enroller in; the catalog arrives with the first sync. (Picking a cashier by
            // PIN is the lock screen's journey, #156.)
            till.waitForText("Berlín", timeoutMs = 90_000)
            till.click("Berlín")
            till.click("Cobrar", substring = true)
            till.waitForText("Efectivo")
            till.click("Efectivo")
            till.click("\$1.000") // the quick-cash chip: tendered $1.000 for $900, change $100
            till.waitForText("VUELTO")
            till.click("Confirmar pago")
            till.waitForText("Venta registrada", substring = true)

            // The sale reaches the backend through the till's own sync, not through a test shortcut.
            var cashAfter = cashBefore
            val deadline = System.currentTimeMillis() + 60_000
            while (cashAfter == cashBefore && System.currentTimeMillis() < deadline) {
                Thread.sleep(1_000)
                cashAfter = E2eBackend.salesCount("CASH")
            }
            assertEquals(cashBefore + 1, cashAfter, "the sale the till rang up did not reach the backend")
        } catch (e: Throwable) {
            till.saveFailureArtifacts("till-tracer")
            throw e
        } finally {
            till.close()
        }
    }
}

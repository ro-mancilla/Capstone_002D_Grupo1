@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** Stories 47 and 48: what enrollment says when it cannot proceed, and while it works. */
class EnrollmentE2eTest {

    private fun TillHarness.fill(password: String = "jefelocal123") {
        waitForText("Registrar este dispositivo")
        type("Panadería (identificador)", "reference")
        type("Usuario", "jefelocal")
        type("Contraseña", password)
        type("Nombre del dispositivo", "Caja e2e")
    }

    @Test
    fun wrongCredentialsShowAnAlertAndTheFormStays() = runComposeUiTest(testTimeout = 120.seconds) {
        tillJourney("enroll-wrong") {
            fill(password = "no-es-la-clave")
            click("Registrar dispositivo")
            waitForText("No pudimos iniciar sesión", substring = true)
            assertText("Registrar dispositivo")
        }
    }

    @Test
    fun withoutAConnectionEnrollmentSaysSo() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("enroll-offline") {
            fill()
            E2eStack.stop("web")
            try {
                click("Registrar dispositivo")
                waitForText("Se requiere conexión a internet", substring = true)
            } finally {
                E2eStack.start("web")
            }
        }
    }

    @Test
    fun tooManyAttemptsSaysToWaitAMinute() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("enroll-throttled") {
            fill()
            // The till and this test reach the backend from the same address, so they share the sign-in bucket.
            var status = 0
            repeat(60) { if (status != 429) status = E2eBackend.wrongLogin() }
            check(status == 429) { "the sign-in limit never engaged" }
            try {
                click("Registrar dispositivo")
                waitForText("Demasiados intentos seguidos", substring = true)
            } finally {
                // Leave the bucket as found for the next journey: wait out the window.
                val deadline = System.currentTimeMillis() + 90_000
                while (E2eBackend.wrongLogin() == 429 && System.currentTimeMillis() < deadline) Thread.sleep(2_000)
            }
        }
    }

    @Test
    fun enrollmentShowsItsFirstSyncProgress() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("enroll-progress") {
            fill()
            click("Registrar dispositivo")
            waitForText("PREPARANDO TU TIENDA", timeoutMs = 30_000)
            waitForText("Sincronización inicial")
            waitForText("Berlín", timeoutMs = 90_000)
        }
    }
}

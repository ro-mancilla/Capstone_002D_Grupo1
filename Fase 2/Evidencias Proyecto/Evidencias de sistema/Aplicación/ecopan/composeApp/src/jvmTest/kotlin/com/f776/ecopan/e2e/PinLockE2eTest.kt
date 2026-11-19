@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Stories 49-51: the lock screen. Nothing in the app locks the till today (see [TillHarness.lockTill]), so each
 * journey locks it explicitly and then drives the real screen.
 */
class PinLockE2eTest {

    private fun TillHarness.enterPin(pin: String) = pin.forEach { clickTag("pin_key_$it") }

    @Test
    fun staffUnlockWithTheirPinAndSwitchWithoutReEnrolling() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("pin-switch") {
            enroll()

            // The cashier picks themselves and unlocks with their PIN.
            lockTill()
            waitForText("¿Quién está en la caja?", timeoutMs = 30_000)
            clickTag("staff_card_CASHIER")
            enterPin("1111")
            waitForText("Berlín", timeoutMs = 60_000)

            // Next shift: someone else on the same till, with no re-enrollment (the enrollment form never shows).
            lockTill()
            waitForText("¿Quién está en la caja?", timeoutMs = 30_000)
            assertNoText("Registrar este dispositivo")
            clickTag("staff_card_BRANCH_MANAGER")
            enterPin("3333")
            waitForText("Berlín", timeoutMs = 60_000)
        }
    }

    @Test
    fun aWrongPinErrorsAndFiveWrongOnesLockTheCashierOut() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("pin-lockout") {
            enroll()
            lockTill()
            waitForText("¿Quién está en la caja?", timeoutMs = 30_000)
            clickTag("staff_card_CASHIER")
            enterPin("9999")
            waitForText("PIN incorrecto", substring = true)
            repeat(4) { enterPin("9999") }
            waitForText("Demasiados intentos", substring = true)
            waitForText("Bloqueado", substring = true)
            // Even the right PIN is refused while locked out.
            enterPin("1111")
            assertNoText("Berlín")
        }
    }
}

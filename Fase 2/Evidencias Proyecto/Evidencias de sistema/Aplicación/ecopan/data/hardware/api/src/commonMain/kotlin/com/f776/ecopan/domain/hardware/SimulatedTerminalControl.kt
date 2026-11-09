package com.f776.ecopan.domain.hardware

/**
 * Discoverable via `terminal as? SimulatedTerminalControl` (mirrors [ManualWeightEntry]) — lets
 * POS's payment dialog play the role of the physical terminal for the simulated driver (real
 * terminal integration is out of scope, scope spec §10): auto-approve after a short delay, or a
 * "decline" demo control, per plan 05 §7.
 */
interface SimulatedTerminalControl {
    fun resolve(result: PaymentResult)
}

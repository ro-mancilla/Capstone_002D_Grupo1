package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.PaymentResult
import com.f776.ecopan.domain.hardware.PaymentTerminal
import com.f776.ecopan.domain.hardware.SimulatedTerminalControl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * `request` suspends until the POS-rendered simulated-terminal dialog resolves it via [resolve]
 * (approve/decline/cancel) — real terminal integration is out of scope (scope spec §10). The
 * perceived "authorizing" delay is a UI/ViewModel concern (05 §5's `terminalPhase`), not baked in
 * here, so this driver adds no artificial latency of its own.
 */
internal class SimulatedTerminal : PaymentTerminal, SimulatedTerminalControl {
    private val pending = MutableStateFlow<CompletableDeferred<PaymentResult>?>(null)

    override suspend fun request(amountMinor: Long): PaymentResult {
        val deferred = CompletableDeferred<PaymentResult>()
        pending.value = deferred
        return try {
            deferred.await()
        } finally {
            pending.value = null
        }
    }

    override fun resolve(result: PaymentResult) {
        pending.value?.complete(result)
    }
}

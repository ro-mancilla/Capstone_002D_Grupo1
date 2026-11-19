package com.f776.ecopan.domain.hardware

/** Simulated only — real terminal integration is out of scope (scope spec §10). */
interface PaymentTerminal {
    suspend fun request(amountMinor: Long): PaymentResult
}

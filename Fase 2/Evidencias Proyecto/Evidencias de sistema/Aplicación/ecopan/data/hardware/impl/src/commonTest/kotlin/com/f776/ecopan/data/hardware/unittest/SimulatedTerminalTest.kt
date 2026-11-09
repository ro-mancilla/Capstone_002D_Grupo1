@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.data.hardware.SimulatedTerminal
import com.f776.ecopan.domain.hardware.PaymentResult
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class SimulatedTerminalTest {

    @Test
    fun `approve resolves the pending request as Approved`() = runTest {
        val terminal = SimulatedTerminal()

        val pending = async { terminal.request(1010) }
        runCurrent()
        terminal.resolve(PaymentResult.Approved(PaymentMethod.CARD))

        val result = assertIs<PaymentResult.Approved>(pending.await())
        assertEquals(PaymentMethod.CARD, result.method)
    }

    @Test
    fun `decline resolves the pending request as Declined`() = runTest {
        val terminal = SimulatedTerminal()

        val pending = async { terminal.request(1010) }
        runCurrent()
        terminal.resolve(PaymentResult.Declined("insufficient_funds"))

        val result = assertIs<PaymentResult.Declined>(pending.await())
        assertEquals("insufficient_funds", result.reason)
    }

    @Test
    fun `cancel resolves the pending request as Cancelled`() = runTest {
        val terminal = SimulatedTerminal()

        val pending = async { terminal.request(1010) }
        runCurrent()
        terminal.resolve(PaymentResult.Cancelled)

        assertIs<PaymentResult.Cancelled>(pending.await())
    }
}

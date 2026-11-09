package com.f776.ecopan.feature.pos.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.feature.pos.screens.pos.Payment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The hardware policy, stated once. It used to live in three `finalizeSale(...)` call sites as a
 * pair of booleans each, so adding a method meant remembering all three.
 */
class PaymentTest {

    @Test
    fun `cash opens the drawer and prints`() {
        val cash = Payment.Cash(tenderedMinor = 5_000, changeMinor = 2_200)

        assertEquals(PaymentMethod.CASH, cash.method)
        assertTrue(cash.kicksDrawer)
        assertTrue(cash.prints)
        assertEquals(5_000, cash.tenderedMinor)
        assertEquals(2_200, cash.changeMinor)
    }

    @Test
    fun `card prints without opening the drawer`() {
        assertEquals(PaymentMethod.CARD, Payment.Card.method)
        assertFalse(Payment.Card.kicksDrawer)
        assertTrue(Payment.Card.prints)
    }

    @Test
    fun `transfer and other touch no hardware`() {
        for (method in listOf(PaymentMethod.TRANSFER, PaymentMethod.OTHER)) {
            val payment = Payment.Other(method)

            assertEquals(method, payment.method)
            assertFalse(payment.kicksDrawer, "$method must not open the till")
            assertFalse(payment.prints, "$method has no paper receipt")
        }
    }

    @Test
    fun `only cash carries tendered and change`() {
        assertNull(Payment.Card.tenderedMinor)
        assertNull(Payment.Card.changeMinor)
        assertNull(Payment.Other(PaymentMethod.TRANSFER).tenderedMinor)
        assertNull(Payment.Other(PaymentMethod.TRANSFER).changeMinor)
    }

    @Test
    fun `the drawer opens for exactly one method`() {
        val payments = listOf(
            Payment.Cash(tenderedMinor = 0, changeMinor = 0),
            Payment.Card,
            Payment.Other(PaymentMethod.TRANSFER),
            Payment.Other(PaymentMethod.OTHER),
        )

        assertEquals(listOf(PaymentMethod.CASH), payments.filter { it.kicksDrawer }.map { it.method })
    }
}

package com.f776.ecopan.core.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `weight sale rounds a fraction above half up`() {
        // 1234 g at 1900 minor/kg = 2344.6 -> rounds to 2345
        assertEquals(2345L, lineTotalForWeight(weightGrams = 1234, pricePerKgMinor = 1900))
    }

    @Test
    fun `weight sale rounds a fraction below half down`() {
        // 1234 g at 1899 minor/kg = 2343.366 -> rounds to 2343
        assertEquals(2343L, lineTotalForWeight(weightGrams = 1234, pricePerKgMinor = 1899))
    }

    @Test
    fun `weight sale rounds an exact half tie up`() {
        // 1 g at 500 minor/kg = 0.5 exactly -> half-up -> 1
        assertEquals(1L, lineTotalForWeight(weightGrams = 1, pricePerKgMinor = 500))
        // 1500 g at 1899 minor/kg = 2848.5 exactly -> half-up -> 2849
        assertEquals(2849L, lineTotalForWeight(weightGrams = 1500, pricePerKgMinor = 1899))
    }

    @Test
    fun `unit sale total is exact quantity times price`() {
        assertEquals(3600L, lineTotalForUnits(quantity = 4, priceMinor = 900))
    }

    @Test
    fun `adding money of mismatched currencies is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Money(100, "CLP") + Money(100, "USD")
        }
    }

    @Test
    fun `adding money of the same currency sums the minor units`() {
        assertEquals(Money(300, "CLP"), Money(100, "CLP") + Money(200, "CLP"))
    }
}

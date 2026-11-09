package com.f776.ecopan.feature.pos.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.feature.pos.screens.pos.TillSaleUi
import com.f776.ecopan.feature.pos.screens.pos.toSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TillSummaryTest {
    private fun sale(n: Int, method: PaymentMethod, total: Long) =
        TillSaleUi(Uuid.random(), n, "10:0$n", method, total)

    @Test
    fun `every payment method has a column and the columns add up to the total`() {
        val sales = listOf(
            sale(1, PaymentMethod.CASH, 5_000),
            sale(2, PaymentMethod.CARD, 3_507),
            sale(3, PaymentMethod.TRANSFER, 1_200),
            sale(4, PaymentMethod.OTHER, 800),
        )
        val summary = sales.toSummary()

        assertEquals(PaymentMethod.entries.toSet(), summary.byMethodMinor.keys)
        assertEquals(1_200L, summary.byMethodMinor.getValue(PaymentMethod.TRANSFER))
        assertEquals(summary.totalMinor, summary.byMethodMinor.values.sum())
        assertEquals(10_507L, summary.totalMinor)
        assertEquals(4, summary.salesCount)
    }

    @Test
    fun `an empty day is all zeros`() {
        val summary = emptyList<TillSaleUi>().toSummary()
        assertEquals(0L, summary.totalMinor)
        assertEquals(0L, summary.byMethodMinor.values.sum())
    }
}

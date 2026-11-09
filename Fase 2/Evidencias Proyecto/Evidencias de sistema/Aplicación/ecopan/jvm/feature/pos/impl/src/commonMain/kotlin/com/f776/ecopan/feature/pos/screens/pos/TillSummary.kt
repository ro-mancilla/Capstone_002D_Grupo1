package com.f776.ecopan.feature.pos.screens.pos

import com.f776.ecopan.core.domain.catalog.PaymentMethod

/**
 * The totals row of "Caja del día". Every [PaymentMethod] gets its own column, so the columns
 * always add up to [totalMinor]: a method with no column would be money in the total that the
 * cashier cannot find in the drawer reconciliation.
 */
internal data class TillSummary(
    val salesCount: Int,
    val byMethodMinor: Map<PaymentMethod, Long>,
    val totalMinor: Long,
)

internal fun List<TillSaleUi>.toSummary(): TillSummary {
    val byMethod = PaymentMethod.entries.associateWith { method ->
        filter { it.paymentMethod == method }.sumOf { it.totalMinor }
    }
    return TillSummary(salesCount = size, byMethodMinor = byMethod, totalMinor = sumOf { it.totalMinor })
}

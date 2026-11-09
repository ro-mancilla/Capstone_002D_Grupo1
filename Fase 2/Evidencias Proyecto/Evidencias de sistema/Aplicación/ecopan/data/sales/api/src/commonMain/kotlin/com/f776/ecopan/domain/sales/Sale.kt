package com.f776.ecopan.domain.sales

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A completed sale, append-only (01 §6.1) — no update/delete path exists anywhere in this module.
 * [totalMinor] is the sum of each line's already-stored [SaleLine.lineTotalMinor]; it is computed
 * once by the caller (POS) and never recomputed here, even if catalog prices change later (01 §5).
 */
data class Sale(
    val id: Uuid,
    val branchId: Uuid,
    val cashierId: Uuid,
    val timestamp: Instant,
    val paymentMethod: PaymentMethod,
    val totalMinor: Long,
    val cashTenderedMinor: Long?,
    val changeMinor: Long?,
    val lines: List<SaleLine>,
    /**
     * The till's sequential sale number ("Venta N.º 0001"). Assigned by [SaleRepository.recordSale];
     * `0` on a sale that has not been recorded yet.
     */
    val receiptNumber: Int = 0,
)

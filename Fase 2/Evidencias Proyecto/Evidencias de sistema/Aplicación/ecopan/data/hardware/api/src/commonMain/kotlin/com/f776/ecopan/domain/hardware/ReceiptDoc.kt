package com.f776.ecopan.domain.hardware

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import kotlin.uuid.Uuid

/**
 * Structured receipt — POS never formats ESC/POS itself; money in minor units (01 §5).
 *
 * [receiptNumber] is the till's own sequential sale number ("Venta N.º 0001"); it is what the
 * paper and the screen show. [saleId] is the durable identifier and is deliberately never printed
 * by [ReceiptLayout]: a customer cannot use a UUID, and nothing on screen may show one.
 * [cashTenderedMinor]/[changeMinor] are set for cash sales only. [issuedAtLabel] is pre-formatted
 * for the same reason as [ReceiptLine.quantityLabel].
 */
data class ReceiptDoc(
    val header: List<String>,
    val lines: List<ReceiptLine>,
    val totalMinor: Long,
    /**
     * The method itself, not a rendered label. It used to be a `String`, which forced whoever
     * built the document to resolve the label first — and the only builder is `PosViewModel`,
     * whose resolver is a Compose Resources lookup. That made completing a sale suspend on the
     * resource loader, so checkout stopped being deterministic under a test scheduler. The
     * printer renders it instead, which is where the paper's wording belongs.
     */
    val paymentMethod: PaymentMethod,
    val footer: List<String>,
    val saleId: Uuid,
    val receiptNumber: Int = 0,
    val issuedAtLabel: String = "",
    val cashTenderedMinor: Long? = null,
    val changeMinor: Long? = null,
)

/**
 * The Spanish word for a payment method as it appears on paper. Receipts are es-CL only, like the
 * rest of the product, so this is a plain mapping rather than a resource lookup — printing
 * `PaymentMethod.name` would put `CASH` on a customer's receipt.
 */
fun PaymentMethod.receiptLabel(): String = when (this) {
    PaymentMethod.CASH -> "Efectivo"
    PaymentMethod.CARD -> "Tarjeta"
    PaymentMethod.TRANSFER -> "Transferencia"
    PaymentMethod.OTHER -> "Otro"
}

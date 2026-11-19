package com.f776.ecopan.feature.pos.screens.pos

import com.f776.ecopan.core.domain.catalog.PaymentMethod

/**
 * How a sale was paid for, and what that implies for the hardware.
 *
 * `finalizeSale` used to take the method plus separate `kickDrawer`/`print` booleans, so each of
 * the three places that complete a sale restated the policy: cash opens the drawer and prints,
 * card prints only, transfer and other do neither. Adding a payment method meant remembering all
 * three call sites, and a wrong flag was silent -- a till that never opened, or a receipt that
 * never came. Here the policy travels with the method, so the caller says only what the customer
 * paid with.
 */
internal sealed interface Payment {
    val method: PaymentMethod

    /** Cash is the only method that puts notes in the till. */
    val kicksDrawer: Boolean

    /** A paper receipt is offered where there is something to reconcile against. */
    val prints: Boolean

    /** Cash only — every other method tenders the exact total. */
    val tenderedMinor: Long? get() = null
    val changeMinor: Long? get() = null

    data class Cash(
        override val tenderedMinor: Long,
        override val changeMinor: Long,
    ) : Payment {
        override val method = PaymentMethod.CASH
        override val kicksDrawer = true
        override val prints = true
    }

    data object Card : Payment {
        override val method = PaymentMethod.CARD
        override val kicksDrawer = false
        override val prints = true
    }

    /** Transfer and anything else: the customer has their own record, and the till stays shut. */
    data class Other(override val method: PaymentMethod) : Payment {
        override val kicksDrawer = false
        override val prints = false
    }
}

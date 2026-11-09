package com.f776.ecopan.domain.hardware

import com.f776.ecopan.core.domain.catalog.PaymentMethod

sealed interface PaymentResult {
    data class Approved(val method: PaymentMethod) : PaymentResult
    data class Declined(val reason: String) : PaymentResult
    data object Cancelled : PaymentResult
}

package com.f776.unigrid.core.database.converter

import androidx.room.TypeConverter
import com.f776.ecopan.core.domain.catalog.PaymentMethod

internal class PaymentMethodConverter {
    @TypeConverter
    fun fromPaymentMethod(paymentMethod: PaymentMethod): String = paymentMethod.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)
}

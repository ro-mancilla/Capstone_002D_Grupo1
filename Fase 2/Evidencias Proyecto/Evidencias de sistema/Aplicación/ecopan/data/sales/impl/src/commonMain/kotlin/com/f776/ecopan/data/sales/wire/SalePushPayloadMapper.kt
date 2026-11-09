package com.f776.ecopan.data.sales.wire

import com.f776.ecopan.domain.sales.Sale
import com.f776.ecopan.domain.sales.SaleLine

internal fun Sale.toPushPayload() = SalePushPayload(
    id = id.toString(),
    branchId = branchId.toString(),
    cashierId = cashierId.toString(),
    soldAt = timestamp.toEpochMilliseconds(),
    paymentMethod = paymentMethod.name,
    totalMinor = totalMinor,
    cashTenderedMinor = cashTenderedMinor,
    changeMinor = changeMinor,
    lines = lines.map { it.toPushPayload() },
)

internal fun SaleLine.toPushPayload() = SaleLinePushPayload(
    id = id.toString(),
    productId = productId.toString(),
    soldBy = soldBy.name,
    weightGrams = weightGrams,
    quantity = quantity,
    unitPriceMinor = unitPriceMinorSnapshot,
    lineTotalMinor = lineTotalMinor,
)

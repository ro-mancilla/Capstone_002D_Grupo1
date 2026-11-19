package com.f776.ecopan.data.sales.local.mapper

import com.f776.ecopan.domain.sales.Sale
import com.f776.ecopan.domain.sales.SaleLine
import com.f776.unigrid.core.database.entity.SaleEntity
import com.f776.unigrid.core.database.entity.SaleLineEntity
import kotlin.uuid.Uuid

internal fun Sale.toEntity() = SaleEntity(
    id = id,
    branchId = branchId,
    cashierId = cashierId,
    timestamp = timestamp,
    paymentMethod = paymentMethod,
    totalMinor = totalMinor,
    cashTenderedMinor = cashTenderedMinor,
    changeMinor = changeMinor,
)

internal fun SaleLine.toEntity(saleId: Uuid) = SaleLineEntity(
    id = id,
    saleId = saleId,
    productId = productId,
    productTypeSnapshot = productTypeSnapshot,
    soldBy = soldBy,
    weightGrams = weightGrams,
    quantity = quantity,
    unitPriceMinorSnapshot = unitPriceMinorSnapshot,
    lineTotalMinor = lineTotalMinor,
)

internal fun SaleLineEntity.toDomain() = SaleLine(
    id = id,
    productId = productId,
    productTypeSnapshot = productTypeSnapshot,
    soldBy = soldBy,
    weightGrams = weightGrams,
    quantity = quantity,
    unitPriceMinorSnapshot = unitPriceMinorSnapshot,
    lineTotalMinor = lineTotalMinor,
)

internal fun SaleEntity.toDomain(lines: List<SaleLineEntity>) = Sale(
    id = id,
    branchId = branchId,
    cashierId = cashierId,
    timestamp = timestamp,
    paymentMethod = paymentMethod,
    totalMinor = totalMinor,
    cashTenderedMinor = cashTenderedMinor,
    changeMinor = changeMinor,
    lines = lines.map { it.toDomain() },
    receiptNumber = receiptNumber,
)

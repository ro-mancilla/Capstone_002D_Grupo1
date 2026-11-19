package com.f776.ecopan.domain.sales

import com.f776.ecopan.core.domain.catalog.SoldBy
import kotlin.uuid.Uuid

/**
 * One cart line, frozen at add-time (01 §5): [productTypeSnapshot], [soldBy] and
 * [unitPriceMinorSnapshot] all survive later catalog edits because they are copied here rather
 * than looked up again. Exactly one of [weightGrams]/[quantity] is
 * set, matching [soldBy]. [lineTotalMinor] is computed once by the caller and stored as-is —
 * historical totals are never recomputed.
 */
data class SaleLine(
    val id: Uuid,
    val productId: Uuid,
    val productTypeSnapshot: String,
    val soldBy: SoldBy,
    val weightGrams: Long?,
    val quantity: Int?,
    val unitPriceMinorSnapshot: Long,
    val lineTotalMinor: Long,
)

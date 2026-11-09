package com.f776.ecopan.domain.catalog

import kotlin.uuid.Uuid

/**
 * Write-side counterpart to [Formula]: [lines] always replaces the formula's entire line set
 * wholesale (02 §1: "Lines replaced wholesale on formula PUT") — there is no per-line upsert.
 */
data class FormulaUpsert(
    val id: Uuid,
    val tenantId: Uuid,
    val productId: Uuid,
    val active: Boolean,
    val serverSeq: Long,
    val lines: List<FormulaLine>,
)

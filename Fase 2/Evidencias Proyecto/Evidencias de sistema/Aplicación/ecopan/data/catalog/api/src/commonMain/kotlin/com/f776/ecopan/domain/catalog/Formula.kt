package com.f776.ecopan.domain.catalog

import kotlin.uuid.Uuid

/** A formula line: [ingredientId] at [basisPoints] relative to flour (01 §5; flour's own line is 10000). */
data class FormulaLine(
    val ingredientId: Uuid,
    val basisPoints: Int,
)

/** One active formula per product (02 §1 `formulas`/`formula_lines`). */
data class Formula(
    val id: Uuid,
    val tenantId: Uuid,
    val productId: Uuid,
    val active: Boolean,
    val lines: List<FormulaLine>,
)

package com.f776.ecopan.domain.catalog

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface FormulaRepository {
    fun observeFormulaForProduct(productId: Uuid): Flow<Formula?>

    /** Replaces the formula and its entire line set wholesale, in one transaction (02 §1). */
    suspend fun upsertFormula(formula: FormulaUpsert)
}

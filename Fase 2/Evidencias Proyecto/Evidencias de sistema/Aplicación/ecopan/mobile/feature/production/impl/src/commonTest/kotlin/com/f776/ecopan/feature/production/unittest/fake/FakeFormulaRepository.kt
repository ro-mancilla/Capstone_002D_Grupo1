package com.f776.ecopan.feature.production.unittest.fake

import com.f776.ecopan.domain.catalog.Formula
import com.f776.ecopan.domain.catalog.FormulaRepository
import com.f776.ecopan.domain.catalog.FormulaUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class FakeFormulaRepository : FormulaRepository {
    val formulasByProduct = MutableStateFlow<Map<Uuid, Formula>>(emptyMap())

    override fun observeFormulaForProduct(productId: Uuid) =
        formulasByProduct.map { it[productId] }

    override suspend fun upsertFormula(formula: FormulaUpsert) = Unit
}

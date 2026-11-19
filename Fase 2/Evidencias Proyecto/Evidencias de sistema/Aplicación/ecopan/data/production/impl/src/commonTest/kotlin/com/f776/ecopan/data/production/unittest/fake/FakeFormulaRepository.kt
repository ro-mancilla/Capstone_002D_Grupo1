package com.f776.ecopan.data.production.unittest.fake

import com.f776.ecopan.domain.catalog.Formula
import com.f776.ecopan.domain.catalog.FormulaRepository
import com.f776.ecopan.domain.catalog.FormulaUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal class FakeFormulaRepository : FormulaRepository {
    private val state = MutableStateFlow<Map<Uuid, Formula>>(emptyMap())

    fun seed(formula: Formula) {
        state.value = state.value + (formula.productId to formula)
    }

    override fun observeFormulaForProduct(productId: Uuid) = MutableStateFlow(state.value[productId])
    override suspend fun upsertFormula(formula: FormulaUpsert) = throw NotImplementedError("unused by :data:production")
}

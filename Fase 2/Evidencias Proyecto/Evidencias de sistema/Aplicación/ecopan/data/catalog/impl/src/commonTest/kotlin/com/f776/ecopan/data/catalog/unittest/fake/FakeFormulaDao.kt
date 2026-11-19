package com.f776.ecopan.data.catalog.unittest.fake

import com.f776.unigrid.core.database.dao.FormulaDao
import com.f776.unigrid.core.database.entity.FormulaEntity
import com.f776.unigrid.core.database.entity.FormulaLineEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FakeFormulaDao : FormulaDao {
    private val formulas = MutableStateFlow<Map<Uuid, FormulaEntity>>(emptyMap())
    private val lines = MutableStateFlow<Map<Uuid, FormulaLineEntity>>(emptyMap())

    override suspend fun upsertFormula(formula: FormulaEntity) {
        formulas.value = formulas.value + (formula.id to formula)
    }

    override suspend fun deleteLines(formulaId: Uuid) {
        lines.value = lines.value.filterValues { it.formulaId != formulaId }
    }

    override suspend fun insertLines(lines: List<FormulaLineEntity>) {
        this.lines.value = this.lines.value + lines.associateBy { it.id }
    }

    override fun observeByProduct(productId: Uuid) =
        formulas.map { byId -> byId.values.firstOrNull { it.productId == productId && it.active } }

    override fun observeLines(formulaId: Uuid) =
        lines.map { byId -> byId.values.filter { it.formulaId == formulaId } }

    override suspend fun clearFormulas() {
        formulas.value = emptyMap()
    }

    override suspend fun clearLines() {
        lines.value = emptyMap()
    }
}

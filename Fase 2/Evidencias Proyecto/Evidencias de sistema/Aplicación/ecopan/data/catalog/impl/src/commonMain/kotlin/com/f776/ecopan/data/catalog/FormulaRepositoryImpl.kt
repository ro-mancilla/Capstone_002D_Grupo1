@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.catalog

import com.f776.ecopan.data.catalog.local.mapper.lineEntities
import com.f776.ecopan.data.catalog.local.mapper.toDomain
import com.f776.ecopan.data.catalog.local.mapper.toEntity
import com.f776.ecopan.domain.catalog.Formula
import com.f776.ecopan.domain.catalog.FormulaRepository
import com.f776.ecopan.domain.catalog.FormulaUpsert
import com.f776.unigrid.core.database.dao.FormulaDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FormulaRepositoryImpl(
    private val formulaDao: FormulaDao,
) : FormulaRepository {

    override fun observeFormulaForProduct(productId: Uuid): Flow<Formula?> =
        formulaDao.observeByProduct(productId).flatMapLatest { formula ->
            if (formula == null) {
                flowOf(null)
            } else {
                formulaDao.observeLines(formula.id).map { lines -> toDomain(formula, lines) }
            }
        }

    override suspend fun upsertFormula(formula: FormulaUpsert) {
        formulaDao.replaceFormula(formula.toEntity(), formula.lineEntities())
    }
}

package com.f776.ecopan.data.catalog.unittest

import com.f776.ecopan.data.catalog.FormulaRepositoryImpl
import com.f776.ecopan.data.catalog.unittest.fake.FakeFormulaDao
import com.f776.ecopan.domain.catalog.FormulaLine
import com.f776.ecopan.domain.catalog.FormulaUpsert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class FormulaRepositoryImplTest {

    private val tenantId = Uuid.random()
    private val productId = Uuid.random()
    private val flourId = Uuid.random()
    private val waterId = Uuid.random()
    private val saltId = Uuid.random()
    private val repository = FormulaRepositoryImpl(FakeFormulaDao())

    @Test
    fun `no formula for a product observes null`() = runTest {
        assertNull(repository.observeFormulaForProduct(productId).first())
    }

    @Test
    fun `upserting a formula makes it and its lines observable together`() = runTest {
        val formulaId = Uuid.random()
        repository.upsertFormula(
            FormulaUpsert(
                id = formulaId,
                tenantId = tenantId,
                productId = productId,
                active = true,
                serverSeq = 1,
                lines = listOf(FormulaLine(flourId, 10_000), FormulaLine(waterId, 6_500)),
            )
        )

        val formula = repository.observeFormulaForProduct(productId).first()

        assertEquals(formulaId, formula?.id)
        assertEquals(setOf(flourId, waterId), formula?.lines?.map { it.ingredientId }?.toSet())
    }

    @Test
    fun `a later PUT replaces the line set wholesale dropping lines the new payload omits`() = runTest {
        val formulaId = Uuid.random()
        repository.upsertFormula(
            FormulaUpsert(formulaId, tenantId, productId, true, 1, listOf(FormulaLine(flourId, 10_000), FormulaLine(waterId, 6_500)))
        )

        repository.upsertFormula(
            FormulaUpsert(formulaId, tenantId, productId, true, 2, listOf(FormulaLine(flourId, 10_000), FormulaLine(saltId, 200)))
        )

        val lines = repository.observeFormulaForProduct(productId).first()?.lines
        assertEquals(setOf(flourId, saltId), lines?.map { it.ingredientId }?.toSet())
    }
}

package com.f776.ecopan.core.domain.formula

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class FormulaSplitTest {

    private val flour = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val water = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val salt = Uuid.parse("00000000-0000-0000-0000-000000000003")
    private val yeast = Uuid.parse("00000000-0000-0000-0000-000000000004")

    @Test
    fun `worked example from the bakery-mobile spec derives the exact published grams`() {
        // 10 kg dough, flour 10000bp + water 6500bp + salt 200bp + yeast 100bp (06-baker-mobile.md).
        val formula = mapOf(flour to 10_000, water to 6_500, salt to 200, yeast to 100)

        val grams = deriveIngredientGrams(mixGrams = 10_000, basisPointsByIngredient = formula)

        assertEquals(5952L, grams.getValue(flour))
        assertEquals(3869L, grams.getValue(water))
        assertEquals(119L, grams.getValue(salt))
        assertEquals(60L, grams.getValue(yeast))
        assertEquals(10_000L, grams.values.sum())
    }

    @Test
    fun `a single flour-only formula is trivially exact`() {
        val grams = deriveIngredientGrams(mixGrams = 7_777, basisPointsByIngredient = mapOf(flour to 10_000))

        assertEquals(mapOf(flour to 7_777L), grams)
    }

    @Test
    fun `results always sum exactly to mixGrams across a range of weights`() {
        val formula = mapOf(flour to 10_000, water to 6_500, salt to 200, yeast to 100)

        for (mixGrams in 1L..2_000L) {
            val grams = deriveIngredientGrams(mixGrams, formula)
            assertEquals(mixGrams, grams.values.sum(), "mismatch for mixGrams=$mixGrams")
        }
    }

    @Test
    fun `zero-gram lines are dropped from the result`() {
        // mixGrams smaller than the ingredient count guarantees some lines floor to zero grams.
        val formula = mapOf(flour to 10_000, water to 6_500, salt to 200, yeast to 100)

        val grams = deriveIngredientGrams(mixGrams = 2, basisPointsByIngredient = formula)

        assertEquals(2L, grams.values.sum())
        for (value in grams.values) {
            assert(value > 0L)
        }
    }

    @Test
    fun `equal remainders are broken deterministically by ascending ingredient id`() {
        // Equal basis points -> equal remainders; the lower-id ingredient must win the spare gram
        // every time, so every device derives an identical movement set from the same formula.
        val formula = mapOf(flour to 10_000, water to 10_000) // mixGrams odd -> one spare gram

        val grams = deriveIngredientGrams(mixGrams = 1, basisPointsByIngredient = formula)

        assertEquals(mapOf(flour to 1L), grams) // flour's id sorts before water's
    }

    @Test
    fun `an empty formula is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            deriveIngredientGrams(mixGrams = 1_000, basisPointsByIngredient = emptyMap())
        }
    }

    @Test
    fun `a non-positive basis point is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            deriveIngredientGrams(mixGrams = 1_000, basisPointsByIngredient = mapOf(flour to 10_000, water to 0))
        }
    }
}

package com.f776.ecopan.worker.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FormulaMathTest {

    @Test
    fun `worked example 10kg 16800bp`() {
        val flour = Uuid.random()
        val water = Uuid.random()
        val salt = Uuid.random()
        val yeast = Uuid.random()

        val lines = listOf(
            FormulaMath.Line(flour, 10_000),
            FormulaMath.Line(water, 6_500),
            FormulaMath.Line(salt, 200),
            FormulaMath.Line(yeast, 100)
        )

        val result = FormulaMath.derive(10_000, lines)!!.associate { it.ingredientId to it.grams }

        assertEquals(5952L, result[flour])
        assertEquals(3869L, result[water])
        assertEquals(119L, result[salt])
        assertEquals(60L, result[yeast])
        assertEquals(10_000L, result.values.sum())
    }

    @Test
    fun `derivation sums exactly for random formulas`() {
        repeat(200) {
            val n = Random.nextInt(1, 13)
            val lines = (0 until n).map { FormulaMath.Line(Uuid.random(), Random.nextInt(1, 15_000)) }
            val mix = Random.nextLong(1, 200_000)

            val result = FormulaMath.derive(mix, lines)
            assertTrue(result != null, "expected a derivation for $n lines, mix=$mix")
            assertEquals(mix, result.sumOf { it.grams }, "sum mismatch for $lines mix=$mix")
            assertTrue(result.all { it.grams >= 0 })
        }
    }

    @Test
    fun `largest remainder correction is deterministic under tie`() {
        // Two lines with identical basis points -> identical remainders; the ingredientId with the
        // lexicographically smaller string wins the +1g bump, deterministically.
        val a = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val b = Uuid.parse("00000000-0000-0000-0000-000000000002")
        val lines = listOf(FormulaMath.Line(a, 3), FormulaMath.Line(b, 3), FormulaMath.Line(Uuid.random(), 4))

        val first = FormulaMath.derive(10, lines)!!.associate { it.ingredientId to it.grams }
        val second = FormulaMath.derive(10, lines)!!.associate { it.ingredientId to it.grams }

        assertEquals(first, second, "derivation must be deterministic across repeated calls")
        assertEquals(10L, first.values.sum())
    }

    @Test
    fun `flour-only formula returns mix intact`() {
        val flour = Uuid.random()
        val result = FormulaMath.derive(7_500, listOf(FormulaMath.Line(flour, 10_000)))!!

        assertEquals(1, result.size)
        assertEquals(7_500L, result.single().grams)
    }

    @Test
    fun `zero-gram lines are dropped and sum still exact`() {
        val big = Uuid.random()
        val tiny = Uuid.random()
        // mix so small relative to totalBp that `tiny`'s floor share rounds to zero grams.
        val lines = listOf(FormulaMath.Line(big, 9_999), FormulaMath.Line(tiny, 1))

        val result = FormulaMath.derive(3, lines)!!
        assertTrue(result.all { it.grams > 0 }, "zero-gram lines must be dropped: $result")
        assertEquals(3L, result.sumOf { it.grams })
    }

    @Test
    fun `invalid bp yields no derivation`() {
        val id = Uuid.random()
        assertNull(FormulaMath.derive(1_000, emptyList()))
        assertNull(FormulaMath.derive(1_000, listOf(FormulaMath.Line(id, 0))))
        assertNull(FormulaMath.derive(1_000, listOf(FormulaMath.Line(id, -5))))
        assertNull(FormulaMath.derive(0, listOf(FormulaMath.Line(id, 10_000))))
    }
}

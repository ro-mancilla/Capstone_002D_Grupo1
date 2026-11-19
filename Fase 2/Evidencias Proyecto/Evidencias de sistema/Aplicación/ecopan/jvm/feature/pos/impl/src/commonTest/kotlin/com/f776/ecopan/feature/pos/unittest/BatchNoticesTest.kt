@file:OptIn(ExperimentalUuidApi::class)

package com.f776.ecopan.feature.pos.unittest

import com.f776.ecopan.feature.pos.screens.pos.BatchFact
import com.f776.ecopan.feature.pos.screens.pos.BatchNoticeUi
import com.f776.ecopan.feature.pos.screens.pos.NoticeText
import com.f776.ecopan.feature.pos.screens.pos.formatMassKg
import com.f776.ecopan.feature.pos.screens.pos.resolve
import com.f776.ecopan.feature.pos.screens.pos.text
import com.f776.ecopan.feature.pos.screens.pos.withBatch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The words and grouping of the "lote listo" notice (spec 3d / copy deck "Aviso de lote"). */
class BatchNoticesTest {

    private val now = Clock.System.now()

    private fun notice(vararg facts: BatchFact) = BatchNoticeUi(Uuid.random(), now, facts.toList())

    private suspend fun BatchNoticeUi.words(): Pair<String, String> {
        val words: NoticeText = text()
        return words.title.resolve() to words.supporting.map { it.resolve() }.joinToString(" · ")
    }

    @Test
    fun `product, mass and branch known`() = runTest {
        val (title, supporting) = notice(BatchFact("Marraqueta", 12_000, "Sucursal Principal")).words()

        assertEquals("Lote de Marraqueta listo · 12 kg de masa", title)
        assertEquals("Sucursal Principal", supporting)
    }

    @Test
    fun `product known but no branch drops the branch instead of inventing one`() = runTest {
        val (title, supporting) = notice(BatchFact("Marraqueta", 12_500, null)).words()

        assertEquals("Lote de Marraqueta listo · 12,5 kg de masa", title)
        assertEquals("", supporting)
    }

    @Test
    fun `product without a mass reads without the mass`() = runTest {
        assertEquals("Lote de Marraqueta listo", notice(BatchFact("Marraqueta", 0, "Sucursal Principal")).words().first)
    }

    @Test
    fun `no product but a branch`() = runTest {
        assertEquals("Lote listo en Sucursal Principal", notice(BatchFact(null, 0, "Sucursal Principal")).words().first)
        assertEquals("Lote listo en Sucursal Principal", notice(BatchFact("", 0, "Sucursal Principal")).words().first)
    }

    @Test
    fun `nothing known at all`() = runTest {
        val (title, supporting) = notice(BatchFact(null, null, null)).words()

        assertEquals("Hay un lote nuevo listo", title)
        assertEquals("", supporting)
    }

    @Test
    fun `an identifier is never shown as a name`() = runTest {
        val leaked = Uuid.random().toString()

        val (title, supporting) = notice(BatchFact(leaked, 5_000, leaked)).words()

        assertEquals("Hay un lote nuevo listo", title)
        assertFalse(leaked in title || leaked in supporting)
    }

    @Test
    fun `several batches name the branch and up to two products and count the rest`() = runTest {
        val (title, supporting) = notice(
            BatchFact("Marraqueta", 12_000, "Sucursal Principal"),
            BatchFact("Hallulla", 8_000, "Sucursal Principal"),
            BatchFact("Pan de molde", 9_000, "Sucursal Principal"),
        ).words()

        assertEquals("3 lotes nuevos listos", title)
        assertEquals("Sucursal Principal · Marraqueta, Hallulla y 1 más", supporting)
    }

    @Test
    fun `several batches of the same product name it once`() = runTest {
        val (title, supporting) = notice(
            BatchFact("Marraqueta", 12_000, "Sucursal Principal"),
            BatchFact("Marraqueta", 12_000, "Sucursal Principal"),
        ).words()

        assertEquals("2 lotes nuevos listos", title)
        assertEquals("Sucursal Principal · Marraqueta", supporting)
    }

    @Test
    fun `two products are joined with y`() = runTest {
        assertEquals(
            "Sucursal Principal · Marraqueta y Hallulla",
            notice(BatchFact("Marraqueta", 1, "Sucursal Principal"), BatchFact("Hallulla", 1, "Sucursal Principal")).words().second,
        )
    }

    @Test
    fun `several batches with nothing known say only how many`() = runTest {
        val (title, supporting) = notice(BatchFact(null, null, null), BatchFact(null, null, null)).words()

        assertEquals("2 lotes nuevos listos", title)
        assertEquals("", supporting)
    }

    @Test
    fun `batches within ten seconds are grouped into one notice`() {
        val first = Uuid.random()
        val notices = emptyList<BatchNoticeUi>()
            .withBatch(first, now, BatchFact("Marraqueta", 1, null))
            .withBatch(Uuid.random(), now + 4.seconds, BatchFact("Hallulla", 1, null))
            .withBatch(Uuid.random(), now + 10.seconds, BatchFact("Pan de molde", 1, null))

        assertEquals(1, notices.size)
        assertEquals(first, notices.single().id)
        assertEquals(3, notices.single().batches.size)
    }

    @Test
    fun `a batch more than ten seconds after the first starts a new notice`() {
        val notices = emptyList<BatchNoticeUi>()
            .withBatch(Uuid.random(), now, BatchFact("Marraqueta", 1, null))
            .withBatch(Uuid.random(), now + 11.seconds, BatchFact("Hallulla", 1, null))

        assertEquals(listOf(1, 1), notices.map { it.batches.size })
    }

    @Test
    fun `mass is trimmed kilos with a comma`() {
        assertEquals("12", formatMassKg(12_000))
        assertEquals("12,5", formatMassKg(12_500))
        assertEquals("12,345", formatMassKg(12_345))
        assertEquals("0,45", formatMassKg(450))
    }
}

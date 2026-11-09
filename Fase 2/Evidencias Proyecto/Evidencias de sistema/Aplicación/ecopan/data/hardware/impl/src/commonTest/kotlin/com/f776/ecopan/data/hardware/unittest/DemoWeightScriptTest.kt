package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.DemoWeightScript
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class DemoWeightScriptTest {

    @Test
    fun `parses a product line into its weight sequence`() {
        val script = DemoWeightScript.parse("Marraqueta: 300, 600, 842")

        assertEquals(listOf(300L, 600L, 842L), script.sequenceFor("Marraqueta"))
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val script = DemoWeightScript.parse(
            """
            # a comment

            Hallulla: 500
            """.trimIndent(),
        )

        assertEquals(listOf(500L), script.sequenceFor("Hallulla"))
    }

    @Test
    fun `lookup ignores case and surrounding spaces`() {
        val script = DemoWeightScript.parse("  Pan Amasado :  400,700 ")

        assertEquals(listOf(400L, 700L), script.sequenceFor("pan amasado"))
    }

    @Test
    fun `unknown product and malformed lines give nothing`() {
        val script = DemoWeightScript.parse("Rota: abc\nSin dos puntos 500\nMarraqueta: 500")

        assertNull(script.sequenceFor("Rota"))
        assertNull(script.sequenceFor("Sin dos puntos 500"))
        assertNull(script.sequenceFor("Berlín"))
    }

    @Test
    fun `loads the file named by the script config key`() {
        val path = Path(SystemFileSystem.resolve(Path(".")), "demo-weights-test-${Random.nextLong()}.txt")
        SystemFileSystem.sink(path).buffered().use { it.writeString("Marraqueta: 111, 222\n") }
        try {
            val script = DemoWeightScript.fromConfig(mapOf("script" to path.toString()))

            assertEquals(listOf(111L, 222L), script.sequenceFor("Marraqueta"))
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun `a missing script file is no script rather than a failure`() {
        val script = DemoWeightScript.fromConfig(mapOf("script" to "/no/such/demo-weights.txt"))

        assertNull(script.sequenceFor("Marraqueta"))
    }

    @Test
    fun `a leading byte order mark does not hide the first product`() {
        val script = DemoWeightScript.parse("\uFEFFMarraqueta: 500, 842")

        assertEquals(listOf(500L, 842L), script.sequenceFor("Marraqueta"))
    }

    @Test
    fun `a trailing comma is tolerated`() {
        val script = DemoWeightScript.parse("Marraqueta: 500, 842,")

        assertEquals(listOf(500L, 842L), script.sequenceFor("Marraqueta"))
    }

    @Test
    fun `a product name may contain colons - the weights follow the last one`() {
        val script = DemoWeightScript.parse("Pan: especial: 300, 640")

        assertEquals(listOf(300L, 640L), script.sequenceFor("Pan: especial"))
    }

    @Test
    fun `a duplicate name keeps the later line`() {
        val script = DemoWeightScript.parse("Hallulla: 100\nhallulla: 200, 300")

        assertEquals(listOf(200L, 300L), script.sequenceFor("Hallulla"))
    }

    @Test
    fun `a good line beside bad ones still loads`() {
        val script = DemoWeightScript.parse("Rota: abc\nMarraqueta: 500, 842\nVacia:\n: 300\nNegativa: -5")

        assertEquals(listOf(500L, 842L), script.sequenceFor("Marraqueta"))
        assertNull(script.sequenceFor("Rota"))
        assertNull(script.sequenceFor("Vacia"))
        assertNull(script.sequenceFor("Negativa"))
    }

    @Test
    fun `skipped lines are reported with their line numbers`() {
        val skipped = mutableListOf<String>()

        DemoWeightScript.parse("Marraqueta: 500\nRota: abc\nsin dos puntos", onSkipped = { skipped += it })

        assertEquals(2, skipped.size)
        assertEquals(true, skipped[0].contains("line 2"))
        assertEquals(true, skipped[1].contains("line 3"))
    }
}

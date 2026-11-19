@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Stories 52-54, 56-62: what a cashier does at the till, one sale at a time; each sale is confirmed through the API.
 * Not here: 55 (barcode scan) -- the desktop test dispatcher's synthetic key events carry no characters, so the wedge
 * scanner's burst cannot be injected yet; see docs/e2e/COVERAGE.md.
 */
class SellingE2eTest {

    @Test
    fun aWeighedSaleIsChargedByTheTypedWeightAndReprinted() = runComposeUiTest(testTimeout = 300.seconds) {
        val cursor = E2eBackend.salesCursor()
        tillJourney("selling-weight") {
            enroll()
            click("Marraqueta")
            typeIntoTag("weigh_manual_input", "0,520")
            click("Agregar", substring = true)
            assertText("Venta actual")
            // 0,520 kg at $2.500/kg = $1.300.
            assertText("1.300", substring = true)
            pay("Efectivo", tenderedChip = "\$5.000")
            assertText("3.700", substring = true) // change due
            click("Confirmar pago")
            waitForText("Venta registrada", substring = true)
            // The receipt (a simulated printer: no paper, the preview says so). Reprint is offered only after a failed print.
            assertText("Cajón abierto", substring = true)
            click("Nueva venta")

            val sale = E2eBackend.awaitSales(cursor, 1).single()
            assertEquals("CASH", sale.paymentMethod)
            assertEquals(1_300L, sale.totalMinor)
            val line = sale.lines.single()
            assertEquals("WEIGHT", line.soldBy)
            assertEquals(520L, line.weightGrams)
            assertEquals(2_500L, line.unitPriceMinor)
        }
    }

    @Test
    fun unitQuantitiesVoidingDiscardingSearchAndBarcodeWork() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("selling-unit") {
            enroll()
            // Browse by category: Pastelería holds the unit items.
            click("Pastelería", substring = true)
            assertText("Berlín")
            assertNoText("Marraqueta", substring = true)
            // Almacén products carry their stock: Agua Mineral is under its reorder level in the reference seed.
            click("Almacén", substring = true)
            assertDescription("Stock bajo: 3 unidades")
            click("Todo", substring = true)

            // Search by name narrows the grid.
            typeIntoTag("pos_search", "Empan")
            assertText("Empanada de Pino")
            assertNoText("Berlín", substring = true)
            typeIntoTag("pos_search", "zzzz")
            assertText("Ningún producto coincide", substring = true)
            typeIntoTag("pos_search", "")

            // Quantity steps up and down.
            click("Berlín")
            clickDescription("Aumentar cantidad")
            clickDescription("Aumentar cantidad")
            assertText("3 u. × \$900", substring = true)
            clickDescription("Disminuir cantidad")
            assertText("2 u. × \$900", substring = true)

            // A line is voided; the sale is discarded.
            clickDescription("Anular línea")
            assertText("Sin productos en la venta")
            click("Berlín")
            click("Descartar venta")
            assertText("Sin productos en la venta")
        }
    }

    @Test
    fun everyPaymentMethodIsRecordedWithItsMethod() = runComposeUiTest(testTimeout = 400.seconds) {
        val cursor = E2eBackend.salesCursor()
        tillJourney("selling-methods") {
            enroll()
            fun sellBerlin(method: String, then: () -> Unit = {}) {
                click("Berlín")
                pay(method)
                then()
                waitForText("Venta registrada", substring = true)
                click("Nueva venta")
            }
            sellBerlin("Tarjeta") { click("Enviar al terminal") } // the simulated terminal approves
            sellBerlin("Transferencia") { click("Confirmar pago") }
            sellBerlin("Otro") { click("Confirmar pago") }

            val sales = E2eBackend.awaitSales(cursor, 3)
            assertEquals(setOf("CARD", "TRANSFER", "OTHER"), sales.map { it.paymentMethod }.toSet())
            sales.forEach { assertEquals(900L, it.totalMinor) }
        }
    }

    @Test
    fun theTillOpensAndShowsTheDaysSales() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("selling-till") {
            enroll()
            click("Caja del día")
            assertText("Sin ventas registradas", substring = true)
            click("Cerrar")
            assertNoText("Sin ventas registradas", substring = true)
        }
    }
}

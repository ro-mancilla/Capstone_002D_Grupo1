@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.mobile.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

/**
 * Stories 75-77, 79 and 83: what the baker sees and what the backend does with the batch. One app graph serves the
 * whole class (see [BakerHarness.launch]); the first journey to run enrolls the phone.
 */
class BakerJourneysE2eTest {

    private fun journey(block: BakerHarness.() -> Unit) = runComposeUiTest(testTimeout = 300.seconds) {
        val baker = BakerHarness(this)
        try {
            baker.launch()
            baker.ensureEnrolled()
            baker.block()
        } finally {
            baker.close()
        }
    }

    @Test
    fun onlyProducedItemsAreOfferedAndTomorrowsSuggestionExpands() = journey {
        // Almacén products (Agua Mineral...) are bought in, not baked: not on the picker, not in the suggestion.
        waitForText("Marraqueta")
        assertNoText("Agua Mineral", substring = true)
        // "Producción sugerida": tomorrow's expected sales per product, grouped by how each is sold, and expandable.
        assertText("Para mañana", substring = true)
        assertText("Ventas esperadas. No es peso de masa.")
        assertText("Pan · kg")
        assertText("Pastelería · unidades")
        click("Ver los 5 productos")
        assertText("Ver menos")
        assertNoText("Agua Mineral", substring = true)
        click("Ver menos")
        assertText("Ver los 5 productos", substring = true)
    }

    @Test
    fun theStockGlanceShowsIngredientBalancesWithTheirStates() = journey {
        click("Ver stock")
        assertText("Saldos al gramo", substring = true)
        // The demo's opening stock has every ingredient above its reorder level; each shows where it would be restocked.
        assertText("Con stock", substring = true)
        assertText("Harina")
        assertText("Reponer en 20,000 kg")
        assertText("Sin punto de reposición")
        clickDescription("Volver")
        waitForText("¿Qué estás amasando?")
    }

    @Test
    fun aLoggedBatchDecrementsTheIngredientsByTheFormula() = journey {
        val flourBefore = runBlocking { BakerBackend.stockGrams("Harina") }
        val batches = runBlocking { BakerBackend.batchCount() }
        click("Marraqueta")
        // The seeded formula: 10 kg of flour per 16,8 kg of dough, so 25 kg of dough uses about 14,9 kg.
        "25".forEach { clickTag("batch_key_$it") }
        assertText("Descuento de materias primas")
        repeat(2) { if (!hasTag("batch_logged", 1_000)) clickTag("batch_confirm") }
        waitForTag("batch_logged", 10_000)
        // It appears under today's batches on the phone, then on the backend and in the ingredient stock.
        assertText("Hoy")
        runBlocking { BakerBackend.awaitBatches(batches + 1) }
        val flourAfter = runBlocking { BakerBackend.awaitStockBelow("Harina", flourBefore, byAtLeast = 14_000) }
        assertTrue(flourBefore - flourAfter in 14_800..14_950, "flour used was ${flourBefore - flourAfter} g")
    }

    @Test
    fun settingsShowThemePermissionsAndAbout() = journey {
        if (!settingsAvailable) return@journey // see settingsAvailable: iOS has no app bundle in a test process
        click("Ajustes")
        assertText("Tema", substring = true)
        click("Oscuro")
        click("Claro")
        assertText("Permisos")
        assertText("Notificaciones")
        // About: this install's version and which bakery, branch and device it is enrolled as.
        assertText("Versión")
        assertText("Panadería El Sol")
        assertText("Sucursal Principal")
        assertText("Teléfono e2e")
        click("Acerca de Ecopan")
        assertText("Ilustraciones por", substring = true)
        click("Cerrar")
        assertNoText("Ilustraciones por", substring = true)
    }
}

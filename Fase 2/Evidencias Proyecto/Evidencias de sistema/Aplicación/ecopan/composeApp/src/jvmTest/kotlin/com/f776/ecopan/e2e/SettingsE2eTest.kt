@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** Stories 70-72: hardware configuration, the demo drivers, theme and About. */
class SettingsE2eTest {

    @Test
    fun theSimulatedScaleDrivesTheWeighPanelAndSerialIsOnlyConfiguration() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("settings-scale") {
            enroll()
            click("Ajustes")
            waitForText("Balanza")
            // Serial is configuration only here: a draft that is edited, parsed against a sample and discarded.
            // It is never applied, so no port on the machine running the test is opened.
            click("Balanza serial")
            waitForText("Marca de la balanza")
            click("CAS")
            click("Toledo")
            waitForText("Cambios sin aplicar", substring = true)
            click("Avanzado: trama y estabilidad")
            waitForText("Probar un texto")
            typeIntoTag("scale_probe_input", "ST,GS,   1.234 kg")
            waitForText("Se lee", substring = true)
            click("Descartar")
            assertNoText("Cambios sin aplicar", substring = true)

            // Simulated: the POS weigh panel reads the demo scale.
            click("Simulada")
            click("Aplicar y probar") // safe: the simulated scale opens no port
            waitForText("Lecturas de demostración", substring = true)
            click("Caja")
            typeIntoTag("pos_search", "Marraqueta")
            click("Marraqueta")
            waitForText("Balanza simulada", substring = true)
            // The demo scale reads on its own: a stable reading arms the Agregar button with its weight. (Clicking it
            // does not add the line yet: see the note on story 53 in docs/e2e/COVERAGE.md.)
            waitForText("Estable")
            assertEnabled("Agregar", substring = true)
        }
    }

    @Test
    fun themeAndAboutShowTheTillsIdentity() = runComposeUiTest(testTimeout = 300.seconds) {
        tillJourney("settings-about") {
            enroll(deviceName = "Caja e2e about")
            click("Ajustes")
            waitForText("Tema")
            click("Oscuro")
            click("Claro")
            // About names the version, the bakery, the branch and this device.
            click("Acerca de Ecopan")
            waitForText("Versión")
            assertText("Panadería El Sol")
            assertText("Sucursal Principal")
            assertText("Caja e2e about")
        }
    }
}

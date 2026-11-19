package com.f776.ecopan.feature.settings.unittest

import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.feature.settings.screens.settings.devices.ProbeResult
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleBrand
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleDraft
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleField
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleFieldError
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleModeOption
import com.f776.ecopan.feature.settings.screens.settings.devices.SerialDraft
import com.f776.ecopan.feature.settings.screens.settings.devices.copy
import com.f776.ecopan.feature.settings.screens.settings.devices.formatKg
import com.f776.ecopan.feature.settings.screens.settings.devices.formatThousands
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScaleDraftTest {

    private val valid = SerialDraft(port = "COM3")

    private suspend fun message(error: ScaleFieldError): String {
        val copy = error.copy()
        return getString(copy.resource, *copy.args.toTypedArray())
    }

    @Test
    fun `the default serial draft with a port has nothing to correct`() {
        assertTrue(valid.validate().isEmpty())
    }

    @Test
    fun `no port is a basic error`() {
        val errors = SerialDraft().validate()
        assertEquals(setOf(ScaleField.Port), errors.keys)
        assertEquals(false, ScaleField.Port.advanced)
    }

    @Test
    fun `a number outside its range names the real range and is rejected`() = runTest {
        val errors = valid.copy(baud = "10").validate()
        assertEquals(ScaleFieldError.OutOfRange(50, 4_000_000), errors[ScaleField.Baud])
        assertEquals("Ingresa un número entero entre 50 y 4.000.000.", message(errors.getValue(ScaleField.Baud)))
    }

    @Test
    fun `each advanced number shows its own range`() = runTest {
        val errors = valid.copy(stabilityCount = "0", toleranceGrams = "x", windowMs = "99999").validate()
        assertEquals("Ingresa un número entero entre 1 y 50.", message(errors.getValue(ScaleField.StabilityCount)))
        assertEquals("Ingresa un número entero entre 0 y 1.000.", message(errors.getValue(ScaleField.ToleranceGrams)))
        assertEquals("Ingresa un número entero entre 10 y 10.000.", message(errors.getValue(ScaleField.WindowMs)))
    }

    @Test
    fun `a frame that does not compile says the weight cannot be found, without regex jargon`() = runTest {
        val errors = valid.copy(frameRegex = "[").validate()
        assertEquals(ScaleFieldError.FrameInvalid, errors[ScaleField.FrameRegex])
        val text = message(ScaleFieldError.FrameInvalid)
        assertEquals(
            "Con esta trama no se encuentra el peso. Usa \"Restablecer valores\" o pega un ejemplo del texto que envía tu balanza.",
            text,
        )
        assertTrue(listOf("regular", "regex", "grupo", "(?<").none { it in text.lowercase() })
    }

    @Test
    fun `an empty frame is invalid`() {
        assertEquals(ScaleFieldError.FrameInvalid, valid.copy(frameRegex = "  ").validate()[ScaleField.FrameRegex])
    }

    @Test
    fun `a frame that never says where the weight or the unit is is told so`() = runTest {
        assertEquals(ScaleFieldError.FrameGroupsMissing, valid.copy(frameRegex = """^(?<value>[0-9.]+)$""").validate()[ScaleField.FrameRegex])
        assertEquals(ScaleFieldError.FrameGroupsMissing, valid.copy(frameRegex = """^(?<unit>kg)$""").validate()[ScaleField.FrameRegex])
        assertEquals("A la trama le falta indicar dónde está el peso o la unidad.", message(ScaleFieldError.FrameGroupsMissing))
    }

    @Test
    fun `a frame with nested repetition is too complex`() = runTest {
        val slow = """^(?<value>(\d+)+)(?<unit>kg)$"""
        assertEquals(ScaleFieldError.FrameTooComplex, valid.copy(frameRegex = slow).validate()[ScaleField.FrameRegex])
        assertEquals("Esta trama es demasiado compleja y podría trabar la lectura. Simplifícala.", message(ScaleFieldError.FrameTooComplex))
    }

    @Test
    fun `frame, units, stable values and stability are all advanced fields`() {
        val advanced = ScaleField.entries.filter { it.advanced }.toSet()
        assertEquals(
            setOf(
                ScaleField.Delimiter, ScaleField.FrameRegex, ScaleField.UnitFactors, ScaleField.StableValues, ScaleField.PollCommand,
                ScaleField.PollInterval, ScaleField.StabilityCount, ScaleField.ToleranceGrams, ScaleField.WindowMs,
            ),
            advanced,
        )
    }

    @Test
    fun `units must name at least one unit`() {
        assertEquals(ScaleFieldError.UnitsInvalid, valid.copy(unitFactors = "kilos").validate()[ScaleField.UnitFactors])
    }

    @Test
    fun `only the serial mode has errors to show`() {
        val broken = SerialDraft(baud = "x", frameRegex = "[")
        assertTrue(ScaleDraft(ScaleModeOption.Manual, broken).validate().isEmpty())
        assertTrue(ScaleDraft(ScaleModeOption.Simulated, broken).validate().isEmpty())
        assertTrue(ScaleDraft(ScaleModeOption.Serial, broken).validate().isNotEmpty())
    }

    @Test
    fun `choosing a brand fills baud, bits and protocol and leaves the port and frame alone`() {
        val custom = valid.copy(frameRegex = "custom")
        val toledo = custom.withBrand(ScaleBrand.Toledo)

        assertEquals(ScaleBrand.Toledo, toledo.brand)
        assertEquals(SerialDraft.POLL, toledo.protocol)
        assertEquals("COM3", toledo.port)
        assertEquals("custom", toledo.frameRegex)
        assertEquals(SerialDraft.CONTINUOUS, toledo.withBrand(ScaleBrand.Cas).protocol)
    }

    @Test
    fun `resetting the advanced values restores the frame, units and stability only`() {
        val edited = valid.copy(baud = "4800", frameRegex = "x", unitFactors = "x", stableValues = "x", stabilityCount = "9", toleranceGrams = "9", windowMs = "999")
        val reset = edited.withAdvancedDefaults()

        assertEquals(SerialDraft().copy(port = "COM3", baud = "4800"), reset)
    }

    @Test
    fun `the probe reads the documented example with the real parser`() {
        assertEquals(ProbeResult.Read(1234, stable = true), valid.probe("ST,GS, 1.234 kg"))
        assertEquals(ProbeResult.Read(842, stable = false), valid.probe("US,GS, 0.842 kg"))
    }

    @Test
    fun `the probe says nothing was found for text the frame does not match`() {
        assertEquals(ProbeResult.NothingFound, valid.probe("hola"))
    }

    @Test
    fun `the probe refuses to guess when the frame itself is wrong`() {
        assertEquals(ProbeResult.FrameBroken, valid.copy(frameRegex = "[").probe("ST,GS, 1.234 kg"))
    }

    @Test
    fun `a serial draft survives the round trip through the saved driver choice`() {
        val draft = ScaleDraft(ScaleModeOption.Serial, valid.withBrand(ScaleBrand.Toledo).copy(stabilityCount = "5"))
        assertEquals(draft, ScaleDraft.fromChoice(draft.toChoice()))
        assertEquals("serial", draft.toChoice().driverId)
        assertEquals("COM3", draft.toChoice().config["port"])
    }

    @Test
    fun `manual and simulated carry no config`() {
        assertEquals(DriverChoice("manual"), ScaleDraft(ScaleModeOption.Manual, valid).toChoice())
        assertEquals(DriverChoice("mock"), ScaleDraft(ScaleModeOption.Simulated, valid).toChoice())
    }

    @Test
    fun `an unknown saved driver reads as manual entry`() {
        assertEquals(ScaleModeOption.Manual, ScaleDraft.fromChoice(DriverChoice("teleporter")).mode)
        assertNull(ScaleDraft.fromChoice(DriverChoice("manual")).serial.port.takeIf { it.isNotEmpty() })
    }

    @Test
    fun `numbers are written the way the till writes them`() {
        assertEquals("4.000.000", formatThousands(4_000_000))
        assertEquals("50", formatThousands(50))
        assertEquals("0,842 kg", formatKg(842))
        assertEquals("12,005 kg", formatKg(12_005))
    }
}

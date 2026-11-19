package com.f776.ecopan.feature.settings.screens.settings.devices

import ecopan.feature.settings.impl.generated.resources.Res
import ecopan.feature.settings.impl.generated.resources.scale_error_frame_groups
import ecopan.feature.settings.impl.generated.resources.scale_error_frame_invalid
import ecopan.feature.settings.impl.generated.resources.scale_error_frame_slow
import ecopan.feature.settings.impl.generated.resources.scale_error_number
import ecopan.feature.settings.impl.generated.resources.scale_error_port
import ecopan.feature.settings.impl.generated.resources.scale_error_required
import ecopan.feature.settings.impl.generated.resources.scale_error_stable_values
import ecopan.feature.settings.impl.generated.resources.scale_error_units
import org.jetbrains.compose.resources.StringResource

/**
 * A sentence of the copy deck as a resource plus its arguments, so the same mapping serves the
 * composables (`stringResource`) and the tests (`getString`).
 */
internal class DevicesCopy(val resource: StringResource, val args: List<Any> = emptyList())

/** The sentence under a wrong field. Plain language: no "expresión regular", no "grupo". */
internal fun ScaleFieldError.copy(): DevicesCopy = when (this) {
    is ScaleFieldError.OutOfRange -> DevicesCopy(Res.string.scale_error_number, listOf(formatThousands(min), formatThousands(max)))
    ScaleFieldError.PortRequired -> DevicesCopy(Res.string.scale_error_port)
    ScaleFieldError.Required -> DevicesCopy(Res.string.scale_error_required)
    ScaleFieldError.FrameInvalid -> DevicesCopy(Res.string.scale_error_frame_invalid)
    ScaleFieldError.FrameGroupsMissing -> DevicesCopy(Res.string.scale_error_frame_groups)
    ScaleFieldError.FrameTooComplex -> DevicesCopy(Res.string.scale_error_frame_slow)
    ScaleFieldError.UnitsInvalid -> DevicesCopy(Res.string.scale_error_units)
    ScaleFieldError.StableValuesEmpty -> DevicesCopy(Res.string.scale_error_stable_values)
}

/** `4000000` -> `4.000.000`, the way the till writes numbers. */
internal fun formatThousands(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(".").reversed()

/** `842` -> `0,842 kg`. */
internal fun formatKg(grams: Long): String = "${grams / 1000},${(grams % 1000).toString().padStart(3, '0')} kg"

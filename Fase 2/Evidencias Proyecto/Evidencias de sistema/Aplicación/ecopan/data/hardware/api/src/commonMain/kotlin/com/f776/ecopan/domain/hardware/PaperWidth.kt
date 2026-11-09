package com.f776.ecopan.domain.hardware

/**
 * Thermal paper widths. 58 mm prints 32 characters per line, 80 mm prints 48. Stored as the
 * `paperWidth` entry of the printer [DriverChoice.config]; the preview, the mock printer and the
 * ESC/POS printer all read it from there, so what is previewed is what is printed.
 */
enum class PaperWidth(val millimetres: Int, val columns: Int) {
    Mm58(58, 32),
    Mm80(80, 48),
    ;

    companion object {
        const val CONFIG_KEY = "paperWidthMm"

        fun fromConfig(config: Map<String, String>): PaperWidth =
            entries.firstOrNull { it.millimetres.toString() == config[CONFIG_KEY] } ?: Mm58
    }
}

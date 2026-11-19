package com.f776.ecopan.domain.hardware

/** Printing never blocks a sale (05 §3 item 7) — a failure is a display fallback, not an error state. */
sealed interface PrintResult {
    data object Ok : PrintResult
    data class Failed(val reason: String) : PrintResult
}

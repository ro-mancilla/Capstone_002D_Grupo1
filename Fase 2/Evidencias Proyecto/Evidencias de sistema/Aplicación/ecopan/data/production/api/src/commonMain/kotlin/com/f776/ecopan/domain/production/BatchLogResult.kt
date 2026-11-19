package com.f776.ecopan.domain.production

/** Never a failure mode by itself — logging always succeeds; only the decrement can be skipped. */
sealed interface BatchLogResult {
    data class Logged(val batch: ProductionBatch, val derivedMovements: List<DerivedIngredient>) : BatchLogResult
    data class LoggedWithoutDecrement(val batch: ProductionBatch, val reason: NoDecrementReason) : BatchLogResult
}

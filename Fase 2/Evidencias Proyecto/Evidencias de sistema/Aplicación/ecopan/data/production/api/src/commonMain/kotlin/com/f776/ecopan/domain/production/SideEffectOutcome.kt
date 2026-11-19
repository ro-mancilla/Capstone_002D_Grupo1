package com.f776.ecopan.domain.production

import com.f776.ecopan.domain.inventory.StockMovement

sealed interface SideEffectOutcome {
    data class Applied(val movements: List<StockMovement>, val derived: List<DerivedIngredient>) : SideEffectOutcome
    data class Skipped(val reason: NoDecrementReason) : SideEffectOutcome
}

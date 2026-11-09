package com.f776.ecopan.data.production.sideeffects

import com.f776.ecopan.domain.production.BatchSideEffects
import com.f776.ecopan.domain.production.NoDecrementReason
import com.f776.ecopan.domain.production.ProductionBatch
import com.f776.ecopan.domain.production.SideEffectOutcome

/**
 * Bound when `INVENTORY_FORMULAS` is disabled (06 §5) — the cut-line. Deleting the
 * [FormulaDecrementSideEffect] binding and always using this one is the "amputate the module"
 * demo (11); nothing else in this module needs to change for that to work.
 */
internal class NoOpBatchSideEffects : BatchSideEffects {
    override suspend fun computeMovements(batch: ProductionBatch): SideEffectOutcome =
        SideEffectOutcome.Skipped(NoDecrementReason.MODULE_DISABLED)
}

package com.f776.ecopan.data.production.sideeffects

import com.f776.ecopan.data.production.domain.FormulaPreview
import com.f776.ecopan.domain.inventory.MovementKind
import com.f776.ecopan.domain.inventory.StockMovement
import com.f776.ecopan.domain.production.BatchSideEffects
import com.f776.ecopan.domain.production.NoDecrementReason
import com.f776.ecopan.domain.production.ProductionBatch
import com.f776.ecopan.domain.production.SideEffectOutcome
import kotlin.uuid.Uuid

/** Bound when `INVENTORY_FORMULAS` is entitled (06 §5). */
internal class FormulaDecrementSideEffect(
    private val formulaPreview: FormulaPreview,
) : BatchSideEffects {

    override suspend fun computeMovements(batch: ProductionBatch): SideEffectOutcome {
        val derived = formulaPreview.derive(batch.productId, batch.mixWeightGrams)
            ?: return SideEffectOutcome.Skipped(NoDecrementReason.NO_FORMULA)

        val movements = derived.map { ingredient ->
            StockMovement(
                id = Uuid.random(),
                tenantId = batch.tenantId,
                branchId = batch.branchId,
                ingredientId = ingredient.ingredientId,
                deltaGrams = -ingredient.grams,
                kind = MovementKind.FORMULA_DECREMENT,
                sourceBatchId = batch.id,
                note = null,
                recordedAt = batch.loggedAt,
                recordedBy = null,
            )
        }
        return SideEffectOutcome.Applied(movements, derived)
    }
}

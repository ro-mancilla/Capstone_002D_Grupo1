package com.f776.ecopan.domain.production

/**
 * The cut-line (06 §5) — MUST remain amputable: disabling `INVENTORY_FORMULAS`, or a product
 * having no formula, still records the batch; only the movements are absent. No `txScope`
 * parameter (unlike the plan's illustrative sketch): this only *computes* what to write —
 * `ProductionRepository.logBatch` is what performs the actual atomic write, since Room's KMP
 * `@Transaction` default-body pattern only joins abstract methods declared on the same Dao (same
 * reasoning as `:data:sales:impl`'s `SaleDao.recordSale`), so a cross-module "transaction scope"
 * object wouldn't actually help here.
 */
interface BatchSideEffects {
    suspend fun computeMovements(batch: ProductionBatch): SideEffectOutcome
}

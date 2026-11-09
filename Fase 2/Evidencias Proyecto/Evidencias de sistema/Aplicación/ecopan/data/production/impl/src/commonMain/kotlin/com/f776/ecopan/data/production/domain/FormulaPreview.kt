package com.f776.ecopan.data.production.domain

import com.f776.ecopan.core.domain.formula.deriveIngredientGrams
import com.f776.ecopan.domain.catalog.FormulaRepository
import com.f776.ecopan.domain.inventory.IngredientRepository
import com.f776.ecopan.domain.production.DerivedIngredient
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * The single code path both `previewDerivation` and `FormulaDecrementSideEffect` call (06 §3: "the
 * same object the preview and the commit call, so preview always equals what gets written"). The
 * actual integer math lives in `:core:domain`'s `FormulaSplit.deriveIngredientGrams` (already
 * built + exhaustively tested there, per 01 §5's basis-point convention) — this class only adds
 * the formula/ingredient lookup and the "no formula" edge cases (06 §9) around it.
 */
internal class FormulaPreview(
    private val formulaRepository: FormulaRepository,
    private val ingredientRepository: IngredientRepository,
) {
    /** Null = no formula for [productId], an invalid formula, or a line referencing an unknown ingredient. */
    suspend fun derive(productId: Uuid, mixWeightGrams: Long): List<DerivedIngredient>? {
        val formula = formulaRepository.observeFormulaForProduct(productId).first() ?: return null
        if (formula.lines.isEmpty() || formula.lines.any { it.basisPoints <= 0 }) return null

        val namesById = ingredientRepository.observeActiveIngredients().first().associate { it.id to it.name }
        if (formula.lines.any { it.ingredientId !in namesById }) return null

        val grams = deriveIngredientGrams(mixWeightGrams, formula.lines.associate { it.ingredientId to it.basisPoints })
        // The split returns its entries in remainder order, which shifts with the weight. Walk the
        // formula's own lines instead so the same product always lists its ingredients the same way
        // (zero-gram lines were dropped by the split and are skipped here too).
        return formula.lines.mapNotNull { line ->
            grams[line.ingredientId]?.let { g -> DerivedIngredient(line.ingredientId, namesById.getValue(line.ingredientId), g) }
        }
    }
}

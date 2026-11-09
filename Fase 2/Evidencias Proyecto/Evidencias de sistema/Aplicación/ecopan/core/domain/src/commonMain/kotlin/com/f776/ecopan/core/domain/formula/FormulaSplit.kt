package com.f776.ecopan.core.domain.formula

import kotlin.uuid.Uuid

/**
 * Splits [mixGrams] of total dough weight across ingredients by basis points relative to flour
 * (01 §5): `gramsᵢ = mixGrams * bpᵢ / totalBp`, largest-remainder corrected so the results sum to
 * exactly [mixGrams]. Ties broken by (remainder desc, ingredient id asc) so every device derives
 * identical movements from the same formula (06-baker-mobile.md worked example). Zero-gram lines
 * are dropped from the result — they are ledger noise, not movements.
 *
 * [basisPointsByIngredient] must be non-empty with every value positive (flour's own line is
 * 10000); an invalid formula is a caller-level "no formula" decision, not this function's concern.
 */
fun deriveIngredientGrams(
    mixGrams: Long,
    basisPointsByIngredient: Map<Uuid, Int>,
): Map<Uuid, Long> {
    require(mixGrams >= 0) { "mixGrams must be non-negative, was $mixGrams" }
    require(basisPointsByIngredient.isNotEmpty()) { "basisPointsByIngredient must not be empty" }
    require(basisPointsByIngredient.values.all { it > 0 }) {
        "all basis points must be positive, was $basisPointsByIngredient"
    }

    val totalBp = basisPointsByIngredient.values.sumOf { it.toLong() }

    val floors = basisPointsByIngredient.mapValues { (_, bp) ->
        val raw = mixGrams * bp
        raw / totalBp to raw % totalBp
    }

    val deficit = mixGrams - floors.values.sumOf { it.first }

    return floors.entries
        .sortedWith(compareByDescending<Map.Entry<Uuid, Pair<Long, Long>>> { it.value.second }.thenBy { it.key.toString() })
        .mapIndexed { index, entry -> entry.key to entry.value.first + if (index < deficit) 1L else 0L }
        .filter { (_, grams) -> grams > 0L }
        .toMap()
}

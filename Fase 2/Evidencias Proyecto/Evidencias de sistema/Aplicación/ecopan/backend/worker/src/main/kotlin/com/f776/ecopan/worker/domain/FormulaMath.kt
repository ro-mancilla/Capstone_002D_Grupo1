package com.f776.ecopan.worker.domain

import kotlin.uuid.Uuid

/**
 * Pure, directly-unit-testable formula derivation (01 §5, worked example in `06-baker-mobile.md`
 * §3): baker's percentages are basis points relative to flour (10000 bp = 100%); the baker logs
 * *total dough* weight. Largest-remainder integer math guarantees `Σ gramsᵢ == mixWeightGrams`
 * exactly. This is the worker's fallback-path twin of the client's `FormulaDerivation` (06 §3) --
 * same algorithm, independently implemented and tested server-side.
 */
object FormulaMath {

    data class Line(val ingredientId: Uuid, val basisPoints: Int)
    data class Result(val ingredientId: Uuid, val grams: Long)

    /**
     * Returns `null` when the formula is invalid: no lines, or any non-positive basis-point value
     * (06 §3's "invalid bp yields no derivation" edge case -- treated as "no formula" upstream).
     * Zero-gram lines are dropped from the result (ledger noise -- 06 §3), but still counted while
     * distributing the largest-remainder deficit so the exact-sum postcondition holds regardless.
     */
    fun derive(mixWeightGrams: Long, lines: List<Line>): List<Result>? {
        if (lines.isEmpty()) return null
        if (lines.any { it.basisPoints <= 0 }) return null
        if (mixWeightGrams <= 0) return null

        val totalBp = lines.sumOf { it.basisPoints.toLong() }
        if (totalBp <= 0) return null

        // raw = mixGrams * bp; floor = raw / totalBp; remainder = raw % totalBp.
        val floors = lines.map { line ->
            val raw = mixWeightGrams * line.basisPoints
            Triple(line, raw / totalBp, raw % totalBp)
        }

        val deficit = mixWeightGrams - floors.sumOf { it.second }

        // Distribute `deficit` x 1g to the largest remainders; deterministic tie-break by
        // (remainder desc, ingredientId asc) so every caller derives identical movements.
        val bumped = floors
            .sortedWith(
                compareByDescending<Triple<Line, Long, Long>> { it.third }
                    .thenBy { it.first.ingredientId.toString() }
            )
            .take(deficit.toInt())
            .map { it.first.ingredientId }
            .toSet()

        return floors
            .map { (line, floor, _) -> Result(line.ingredientId, floor + if (line.ingredientId in bumped) 1 else 0) }
            .filter { it.grams > 0 }
    }
}

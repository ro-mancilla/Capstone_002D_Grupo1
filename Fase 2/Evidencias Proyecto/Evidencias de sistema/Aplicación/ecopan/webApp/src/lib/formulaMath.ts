/**
 * Pure, integer-only formula derivation -- the web display's independent port of
 * `FormulaMath.kt` (`backend/worker/src/main/kotlin/com/f776/ecopan/worker/domain/FormulaMath.kt`),
 * which is itself the worker's twin of the KMP client's `FormulaDerivation` (`06 §3`, restated
 * `01 §5`, binding). All three implementations share the same worked example so "web display and
 * KMP ledger provably agree" (plan 07 §10) -- this file must never drift from that algorithm.
 *
 * Baker's percentages are stored as basis points relative to flour (flour itself = 10000 bp).
 * Given a total dough weight, largest-remainder integer math guarantees `Σ gramsᵢ ===
 * mixWeightGrams` exactly. This is display-only here (plan 07 §5: "illustrative" preview) -- the
 * real ledger-writing derivation runs on the baker client / worker fallback.
 *
 * No floats: `mixWeightGrams` and every `basisPoints` value must be integers; all intermediate
 * arithmetic (`raw`, floor division, remainder) stays within `Number.isSafeInteger` range for any
 * sane bakery mix size (well under 2^53).
 */

export interface FormulaMathLine {
  ingredientId: string
  basisPoints: number
}

export interface FormulaMathResult {
  ingredientId: string
  grams: number
}

/**
 * Returns `null` when the formula is invalid: no lines, any non-positive basis-point value, a
 * non-positive/non-integer `mixWeightGrams`, or a non-positive total (06 §3's "invalid bp yields
 * no derivation" edge case). Zero-gram lines are dropped from the result (ledger noise) but still
 * counted while distributing the largest-remainder deficit, so the exact-sum postcondition holds
 * regardless of how many lines end up filtered out.
 */
export function deriveFormula(
  mixWeightGrams: number,
  lines: readonly FormulaMathLine[]
): FormulaMathResult[] | null {
  if (lines.length === 0) return null
  if (lines.some((line) => !Number.isInteger(line.basisPoints) || line.basisPoints <= 0)) {
    return null
  }
  if (!Number.isInteger(mixWeightGrams) || mixWeightGrams <= 0) return null

  const totalBp = lines.reduce((sum, line) => sum + line.basisPoints, 0)
  if (totalBp <= 0) return null

  // raw = mixGrams * bp; floor = raw / totalBp; remainder = raw % totalBp -- all exact integer
  // arithmetic since every operand is a non-negative integer.
  const floors = lines.map((line) => {
    const raw = mixWeightGrams * line.basisPoints
    return {
      line,
      floor: Math.floor(raw / totalBp),
      remainder: raw % totalBp,
    }
  })

  const deficit = mixWeightGrams - floors.reduce((sum, f) => sum + f.floor, 0)

  // Distribute `deficit` x 1g to the largest remainders; deterministic tie-break by
  // (remainder desc, ingredientId asc) so every caller derives identical movements.
  const bumped = new Set(
    [...floors]
      .sort((a, b) => {
        if (b.remainder !== a.remainder) return b.remainder - a.remainder
        return a.line.ingredientId < b.line.ingredientId ? -1 : 1
      })
      .slice(0, deficit)
      .map((f) => f.line.ingredientId)
  )

  return floors
    .map(({ line, floor }) => ({
      ingredientId: line.ingredientId,
      grams: floor + (bumped.has(line.ingredientId) ? 1 : 0),
    }))
    .filter((result) => result.grams > 0)
}

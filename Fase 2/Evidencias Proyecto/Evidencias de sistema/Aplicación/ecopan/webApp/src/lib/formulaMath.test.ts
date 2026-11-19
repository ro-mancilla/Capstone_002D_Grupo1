import { describe, expect, it } from "vitest"
import { deriveFormula } from "./formulaMath"

/**
 * Plan 07 §10 mandated test surface 1 (math half): asserts the exact worked example from
 * `06-baker-mobile.md` §3 literally, plus its edge cases, so this TS port and the two Kotlin
 * implementations (`FormulaMath.kt`, KMP `FormulaDerivation`) provably agree.
 */
describe("deriveFormula (plan 06 §3 worked example)", () => {
  it("derives the exact worked example: 10kg dough, flour 10000bp + water 6500bp + salt 200bp + yeast 100bp", () => {
    const result = deriveFormula(10_000, [
      { ingredientId: "flour", basisPoints: 10000 },
      { ingredientId: "water", basisPoints: 6500 },
      { ingredientId: "salt", basisPoints: 200 },
      { ingredientId: "yeast", basisPoints: 100 },
    ])

    expect(result).not.toBeNull()
    const byIngredient = Object.fromEntries(result!.map((r) => [r.ingredientId, r.grams]))
    expect(byIngredient).toEqual({
      flour: 5952,
      water: 3869,
      salt: 119,
      yeast: 60,
    })

    // Postcondition: sum of derived grams === mix weight, exactly.
    const sum = result!.reduce((acc, r) => acc + r.grams, 0)
    expect(sum).toBe(10_000)
  })

  it("single-line (flour-only) formula is trivially exact", () => {
    const result = deriveFormula(5_000, [{ ingredientId: "flour", basisPoints: 10000 }])
    expect(result).toEqual([{ ingredientId: "flour", grams: 5_000 }])
  })

  it("drops zero-gram lines but keeps the exact-sum postcondition over the non-zero subset", () => {
    // mixGrams smaller than the number of lines -- some lines legitimately floor to 0g.
    const result = deriveFormula(2, [
      { ingredientId: "flour", basisPoints: 10000 },
      { ingredientId: "water", basisPoints: 6500 },
      { ingredientId: "salt", basisPoints: 200 },
      { ingredientId: "yeast", basisPoints: 100 },
    ])

    expect(result).not.toBeNull()
    expect(result!.every((r) => r.grams > 0)).toBe(true)
    const sum = result!.reduce((acc, r) => acc + r.grams, 0)
    expect(sum).toBe(2)
  })

  it("returns null for an empty line list", () => {
    expect(deriveFormula(1000, [])).toBeNull()
  })

  it("returns null when any bp is non-positive (invalid bp -> no derivation)", () => {
    expect(
      deriveFormula(1000, [
        { ingredientId: "flour", basisPoints: 10000 },
        { ingredientId: "water", basisPoints: 0 },
      ])
    ).toBeNull()
    expect(
      deriveFormula(1000, [
        { ingredientId: "flour", basisPoints: 10000 },
        { ingredientId: "water", basisPoints: -500 },
      ])
    ).toBeNull()
  })

  it("returns null for a non-positive or non-integer mix weight", () => {
    const lines = [{ ingredientId: "flour", basisPoints: 10000 }]
    expect(deriveFormula(0, lines)).toBeNull()
    expect(deriveFormula(-10, lines)).toBeNull()
    expect(deriveFormula(10.5, lines)).toBeNull()
  })

  it("is deterministic: identical input always derives identical output (tie-break by ingredientId asc)", () => {
    // Two lines with equal basis points can tie on remainder; tie-break must be deterministic.
    const lines = [
      { ingredientId: "b-ingredient", basisPoints: 5000 },
      { ingredientId: "a-ingredient", basisPoints: 5000 },
    ]
    const first = deriveFormula(9999, lines)
    const second = deriveFormula(9999, lines)
    expect(first).toEqual(second)
  })
})

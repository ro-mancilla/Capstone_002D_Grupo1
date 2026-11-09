import { describe, expect, it } from "vitest"
import type { ProductionSummaryDto } from "@/api/types"
import { totalsByProductAndBranch } from "./productionAggregate"
import { productBars, summarize } from "./productionSegments"

function row(overrides: Partial<ProductionSummaryDto>): ProductionSummaryDto {
  return {
    date: "2026-01-10",
    branchId: "b1",
    productId: "p1",
    productName: "Marraqueta",
    productType: "PAN",
    batchCount: 1,
    mixWeightGrams: 1000,
    ...overrides,
  }
}

describe("productBars", () => {
  const totals = totalsByProductAndBranch([
    row({ productId: "a", productName: "Hallulla", branchId: "b1", mixWeightGrams: 84_000, batchCount: 6 }),
    row({ productId: "a", productName: "Hallulla", branchId: "b2", mixWeightGrams: 36_000, batchCount: 3 }),
    row({ productId: "m", productName: "Marraqueta", branchId: "b1", mixWeightGrams: 81_000, batchCount: 6 }),
    row({ productId: "m", productName: "Marraqueta", branchId: "b2", mixWeightGrams: 54_000, batchCount: 4 }),
    row({ productId: "z", productName: "Berlín", branchId: "b2", mixWeightGrams: 28_500, batchCount: 2 }),
  ])

  it("sorts by total dough weight and keeps one segment per branch in series order", () => {
    const bars = productBars(totals, ["b1", "b2"], "other")
    expect(bars.map((b) => b.productName)).toEqual(["Marraqueta", "Hallulla", "Berlín"])
    expect(bars[0].segments.map((s) => [s.seriesKey, s.grams])).toEqual([
      ["b1", 81_000],
      ["b2", 54_000],
    ])
    expect(bars[0]).toMatchObject({ totalGrams: 135_000, batchCount: 10 })
  })

  it("carries the previous period's total per product, null when it had none", () => {
    const previous = totalsByProductAndBranch([row({ productId: "m", productName: "Marraqueta", mixWeightGrams: 120_000 })])
    const bars = productBars(totals, ["b1", "b2"], "other", previous)
    expect(bars.find((b) => b.productId === "m")!.previousGrams).toBe(120_000)
    expect(bars.find((b) => b.productId === "z")!.previousGrams).toBeNull()
  })

  it("folds branches outside the series into the other bucket", () => {
    const bars = productBars(totals, ["b1"], "other")
    expect(bars.find((b) => b.productId === "z")!.segments).toEqual([{ seriesKey: "other", grams: 28_500, batchCount: 2 }])
  })
})

describe("summarize", () => {
  it("adds dough grams and batches", () => {
    const totals = totalsByProductAndBranch([row({ mixWeightGrams: 2000, batchCount: 2 }), row({ productId: "x", mixWeightGrams: 500 })])
    expect(summarize(totals)).toEqual({ doughGrams: 2500, batchCount: 3 })
  })
})

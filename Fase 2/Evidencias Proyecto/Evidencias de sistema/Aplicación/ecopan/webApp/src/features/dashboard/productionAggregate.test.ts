import { describe, expect, it } from "vitest"
import type { ProductionSummaryDto } from "@/api/types"
import { foldBranchSeries, totalsByProductAndBranch } from "./productionAggregate"

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

describe("totalsByProductAndBranch", () => {
  it("sums batches and dough grams across days for the same product and branch", () => {
    const totals = totalsByProductAndBranch([
      row({ date: "2026-01-10", batchCount: 2, mixWeightGrams: 18_000 }),
      row({ date: "2026-01-11", batchCount: 1, mixWeightGrams: 7_000 }),
      row({ branchId: "b2", batchCount: 1, mixWeightGrams: 5_000 }),
    ])

    expect(totals).toHaveLength(2)
    expect(totals.find((t) => t.branchId === "b1")).toMatchObject({ batchCount: 3, mixWeightGrams: 25_000 })
    expect(totals.find((t) => t.branchId === "b2")).toMatchObject({ batchCount: 1, mixWeightGrams: 5_000 })
  })

  it("orders by product name then branch", () => {
    const totals = totalsByProductAndBranch([
      row({ productId: "p2", productName: "Croissant", branchId: "b2" }),
      row({ productId: "p1", productName: "Marraqueta", branchId: "b1" }),
      row({ productId: "p2", productName: "Croissant", branchId: "b1" }),
    ])

    expect(totals.map((t) => `${t.productName}/${t.branchId}`)).toEqual([
      "Croissant/b1",
      "Croissant/b2",
      "Marraqueta/b1",
    ])
  })
})

describe("foldBranchSeries", () => {
  const totals = ["b1", "b2", "b3", "b4", "b5", "b6", "b7"].map((branchId, index) => ({
    productId: "p1",
    productName: "Marraqueta",
    productType: "PAN",
    branchId,
    batchCount: 1,
    mixWeightGrams: (index + 1) * 1000,
  }))

  it("keeps up to five branches as-is", () => {
    const { seriesBranchIds, chartData } = foldBranchSeries(totals.slice(0, 5), "OTHER")
    expect(seriesBranchIds).toHaveLength(5)
    expect(seriesBranchIds).not.toContain("OTHER")
    expect(chartData).toHaveLength(1)
  })

  it("folds the smallest branches past the fifth into one bucket, never cycling a hue", () => {
    const { seriesBranchIds, chartData } = foldBranchSeries(totals, "OTHER")

    expect(seriesBranchIds).toHaveLength(6)
    expect(seriesBranchIds.at(-1)).toBe("OTHER")
    // b1 (1 kg) + b2 (2 kg) are the two smallest: folded together.
    expect(chartData[0]).toMatchObject({ productName: "Marraqueta", OTHER: 3000, b7: 7000 })
  })
})

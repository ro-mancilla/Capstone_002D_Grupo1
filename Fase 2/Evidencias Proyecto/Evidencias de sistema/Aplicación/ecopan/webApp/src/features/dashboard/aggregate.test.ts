import { describe, expect, it } from "vitest"
import type { SalesByDayDto } from "@/api/types"
import {
  fillDayTotals,
  stackedRevenueByDayAndType,
  totalsByBranch,
  totalsByDate,
  totalsByProductType,
} from "./aggregate"
import { enumerateDays } from "./filters"

function row(overrides: Partial<SalesByDayDto>): SalesByDayDto {
  return {
    date: "2026-07-01",
    branchId: "branch-1",
    productType: "PAN",
    salesCount: 1,
    weightGrams: 0,
    units: 0,
    revenueMinor: 0,
    ...overrides,
  }
}

/**
 * Plan 07 §10's mandated sales-KPI-rendering test surface (#2): the aggregation helpers
 * `SalesPage`/`OverviewPage`/`BranchesPage` build their charts/tables from. Split out as pure-
 * function tests (in addition to `SalesPage.test.tsx`'s rendering test) because "empty-day gaps
 * rendered as zero, not skipped" and "per-type stacking totals" are exact-value assertions best
 * made directly against the data shape, not by parsing rendered SVG.
 */
describe("dashboard aggregate helpers", () => {
  it("totalsByDate sums rows for the same date across branches/types", () => {
    const rows = [
      row({ date: "2026-07-01", branchId: "b1", productType: "PAN", revenueMinor: 1000, salesCount: 1, weightGrams: 500, units: 0 }),
      row({ date: "2026-07-01", branchId: "b2", productType: "EMPANADA", revenueMinor: 2000, salesCount: 2, weightGrams: 0, units: 10 }),
      row({ date: "2026-07-02", branchId: "b1", productType: "PAN", revenueMinor: 500, salesCount: 1, weightGrams: 250, units: 0 }),
    ]
    expect(totalsByDate(rows)).toEqual([
      { date: "2026-07-01", revenueMinor: 3000, salesCount: 3, weightGrams: 500, units: 10 },
      { date: "2026-07-02", revenueMinor: 500, salesCount: 1, weightGrams: 250, units: 0 },
    ])
  })

  it("fillDayTotals fills every day in the range at zero -- gaps are not silently skipped", () => {
    const from = new Date("2026-07-01T00:00:00").getTime()
    const to = new Date("2026-07-05T00:00:00").getTime()
    const rows = [row({ date: "2026-07-01", revenueMinor: 1000, salesCount: 1, weightGrams: 100, units: 0 })]

    const filled = fillDayTotals(from, to, totalsByDate(rows))

    expect(filled.map((r) => r.date)).toEqual(enumerateDays(from, to))
    expect(filled).toHaveLength(5)
    // The one day with data keeps its values...
    expect(filled[0]).toEqual({ date: "2026-07-01", revenueMinor: 1000, salesCount: 1, weightGrams: 100, units: 0 })
    // ...every other day is an explicit zero row, not omitted from the array.
    for (const day of filled.slice(1)) {
      expect(day.revenueMinor).toBe(0)
      expect(day.salesCount).toBe(0)
      expect(day.weightGrams).toBe(0)
      expect(day.units).toBe(0)
    }
  })

  it("totalsByProductType sums per type and preserves weight-sold vs unit-sold shape", () => {
    const rows = [
      row({ date: "2026-07-01", productType: "PAN", weightGrams: 500, units: 0, revenueMinor: 1000, salesCount: 1 }),
      row({ date: "2026-07-02", productType: "PAN", weightGrams: 300, units: 0, revenueMinor: 600, salesCount: 1 }),
      row({ date: "2026-07-01", productType: "EMPANADA", weightGrams: 0, units: 12, revenueMinor: 2400, salesCount: 3 }),
    ]
    const totals = totalsByProductType(rows)
    const pan = totals.find((t) => t.productType === "PAN")
    const empanada = totals.find((t) => t.productType === "EMPANADA")
    expect(pan).toEqual({ productType: "PAN", revenueMinor: 1600, salesCount: 2, weightGrams: 800, units: 0 })
    expect(empanada).toEqual({ productType: "EMPANADA", revenueMinor: 2400, salesCount: 3, weightGrams: 0, units: 12 })
  })

  it("stackedRevenueByDayAndType sums to the same grand total as totalsByProductType (per-type stacking totals)", () => {
    const rows = [
      row({ date: "2026-07-01", productType: "PAN", revenueMinor: 1000 }),
      row({ date: "2026-07-01", productType: "EMPANADA", revenueMinor: 500 }),
      row({ date: "2026-07-02", productType: "PAN", revenueMinor: 700 }),
    ]
    const days = ["2026-07-01", "2026-07-02"]
    const types = ["PAN", "EMPANADA"]
    const stacked = stackedRevenueByDayAndType(rows, days, types)

    // Every (day, type) combination is present, including the zero for EMPANADA on 07-02.
    expect(stacked).toEqual([
      { date: "2026-07-01", PAN: 1000, EMPANADA: 500 },
      { date: "2026-07-02", PAN: 700, EMPANADA: 0 },
    ])

    const stackedTotal = stacked.reduce(
      (sum, day) => sum + types.reduce((typeSum, type) => typeSum + Number(day[type]), 0),
      0
    )
    const perTypeTotal = totalsByProductType(rows).reduce((sum, t) => sum + t.revenueMinor, 0)
    expect(stackedTotal).toBe(perTypeTotal)
  })

  it("totalsByBranch aggregates K1 rows per branch (the branches page's data source)", () => {
    const rows = [
      row({ date: "2026-07-01", branchId: "b1", revenueMinor: 1000, salesCount: 1 }),
      row({ date: "2026-07-02", branchId: "b1", revenueMinor: 500, salesCount: 1 }),
      row({ date: "2026-07-01", branchId: "b2", revenueMinor: 300, salesCount: 1 }),
    ]
    expect(totalsByBranch(rows)).toEqual([
      { branchId: "b1", revenueMinor: 1500, salesCount: 2 },
      { branchId: "b2", revenueMinor: 300, salesCount: 1 },
    ])
  })
})

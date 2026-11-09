import type { SalesByDayDto } from "@/api/types"
import { enumerateDays } from "./filters"

/**
 * `SalesByDayDto` rows are keyed by `(date, branchId, productType?)` even when the caller didn't
 * filter to one branch (`AnalyticsServiceImpl.salesByDay` groups by the *fact's* branch, not the
 * query filter) -- every dashboard page that reads K1 re-aggregates these rows for its own view,
 * rather than re-deriving the same reduce logic per page.
 */

export interface DayTotal {
  date: string
  revenueMinor: number
  salesCount: number
  weightGrams: number
  units: number
}

/** Sums every row for the same `date`, discarding branch/type -- the overview cards + sparkline's
 * shape. */
export function totalsByDate(rows: SalesByDayDto[]): DayTotal[] {
  const byDate = new Map<string, DayTotal>()
  for (const row of rows) {
    const existing = byDate.get(row.date)
    const next: DayTotal = existing
      ? {
          date: row.date,
          revenueMinor: existing.revenueMinor + row.revenueMinor,
          salesCount: existing.salesCount + row.salesCount,
          weightGrams: existing.weightGrams + row.weightGrams,
          units: existing.units + row.units,
        }
      : {
          date: row.date,
          revenueMinor: row.revenueMinor,
          salesCount: row.salesCount,
          weightGrams: row.weightGrams,
          units: row.units,
        }
    byDate.set(row.date, next)
  }
  return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date))
}

/** `totalsByDate` only has entries for days with at least one sale -- this fills in every other
 * day in `[from, to]` at zero so line/area/sparkline charts render a real gap instead of
 * silently connecting across it (plan 07 §10 AC). */
export function fillDayTotals(from: number, to: number, totals: DayTotal[]): DayTotal[] {
  return fillDaysTotals(enumerateDays(from, to), totals)
}

/** Same as `fillDayTotals`, for an explicit list of `yyyy-MM-dd` days (e.g. reporting-zone days). */
export function fillDaysTotals(days: string[], totals: DayTotal[]): DayTotal[] {
  const byDate = new Map(totals.map((total) => [total.date, total]))
  return days.map(
    (date) => byDate.get(date) ?? { date, revenueMinor: 0, salesCount: 0, weightGrams: 0, units: 0 }
  )
}

export function sumDayTotals(rows: DayTotal[]): Omit<DayTotal, "date"> {
  return rows.reduce(
    (acc, row) => ({
      revenueMinor: acc.revenueMinor + row.revenueMinor,
      salesCount: acc.salesCount + row.salesCount,
      weightGrams: acc.weightGrams + row.weightGrams,
      units: acc.units + row.units,
    }),
    { revenueMinor: 0, salesCount: 0, weightGrams: 0, units: 0 }
  )
}

export interface ProductTypeTotal {
  productType: string
  revenueMinor: number
  salesCount: number
  weightGrams: number
  units: number
}

/** Sums every row for the same `productType` across dates/branches -- the sales page's totals
 * table (weight-sold rows show kg, unit-sold rows show pieces, per plan 07 §4). */
export function totalsByProductType(rows: SalesByDayDto[]): ProductTypeTotal[] {
  const byType = new Map<string, ProductTypeTotal>()
  for (const row of rows) {
    const type = row.productType ?? "—"
    const existing = byType.get(type)
    byType.set(
      type,
      existing
        ? {
            productType: type,
            revenueMinor: existing.revenueMinor + row.revenueMinor,
            salesCount: existing.salesCount + row.salesCount,
            weightGrams: existing.weightGrams + row.weightGrams,
            units: existing.units + row.units,
          }
        : {
            productType: type,
            revenueMinor: row.revenueMinor,
            salesCount: row.salesCount,
            weightGrams: row.weightGrams,
            units: row.units,
          }
    )
  }
  return [...byType.values()].sort((a, b) => b.revenueMinor - a.revenueMinor)
}

/** One row per day, one column per product type's revenue -- the sales page's stacked bar chart
 * data shape. Missing (day, type) combinations are left at 0 (Recharts' stacked `Bar` treats an
 * absent key as 0 automatically, but filling it explicitly keeps the row shape uniform for the
 * table/test assertions too). */
export function stackedRevenueByDayAndType(
  rows: SalesByDayDto[],
  days: string[],
  productTypes: string[]
): Array<Record<string, string | number>> {
  const key = (date: string, type: string) => `${date}::${type}`
  const revenueByKey = new Map<string, number>()
  for (const row of rows) {
    const type = row.productType ?? "—"
    const k = key(row.date, type)
    revenueByKey.set(k, (revenueByKey.get(k) ?? 0) + row.revenueMinor)
  }
  return days.map((date) => {
    const entry: Record<string, string | number> = { date }
    for (const type of productTypes) {
      entry[type] = revenueByKey.get(key(date, type)) ?? 0
    }
    return entry
  })
}

export interface BranchTotal {
  branchId: string
  revenueMinor: number
  salesCount: number
}

/** Sums every row for the same `branchId` across dates/types -- the `branches` page's bar chart +
 * table (plan 07 §9: "no dedicated endpoint ... K1's per-branchId rows aggregated
 * presentationally"). */
export function totalsByBranch(rows: SalesByDayDto[]): BranchTotal[] {
  const byBranch = new Map<string, BranchTotal>()
  for (const row of rows) {
    if (!row.branchId) continue
    const existing = byBranch.get(row.branchId)
    byBranch.set(
      row.branchId,
      existing
        ? {
            branchId: row.branchId,
            revenueMinor: existing.revenueMinor + row.revenueMinor,
            salesCount: existing.salesCount + row.salesCount,
          }
        : { branchId: row.branchId, revenueMinor: row.revenueMinor, salesCount: row.salesCount }
    )
  }
  return [...byBranch.values()].sort((a, b) => b.revenueMinor - a.revenueMinor)
}

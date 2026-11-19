import type { ProductionSummaryDto } from "@/api/types"

/** Categorical palette slots available for stacked series -- see `index.css`'s `--chart-*`. */
export const MAX_BRANCH_SERIES = 5

export interface ProductBranchTotal {
  productId: string
  productName: string
  productType: string
  branchId: string
  batchCount: number
  /** Dough (mix) weight in grams -- not finished bread. */
  mixWeightGrams: number
}

/** K6 returns one row per day x branch x product; the page's table and chart want the whole
 * selected range collapsed to one row per product x branch. */
export function totalsByProductAndBranch(rows: ProductionSummaryDto[]): ProductBranchTotal[] {
  const byKey = new Map<string, ProductBranchTotal>()
  for (const row of rows) {
    const key = `${row.productId}|${row.branchId}`
    const existing = byKey.get(key)
    if (existing) {
      existing.batchCount += row.batchCount
      existing.mixWeightGrams += row.mixWeightGrams
    } else {
      byKey.set(key, {
        productId: row.productId,
        productName: row.productName,
        productType: row.productType,
        branchId: row.branchId,
        batchCount: row.batchCount,
        mixWeightGrams: row.mixWeightGrams,
      })
    }
  }
  return [...byKey.values()].sort(
    (a, b) => a.productName.localeCompare(b.productName, "es") || a.branchId.localeCompare(b.branchId)
  )
}

export type ProductChartRow = { productId: string; productName: string } & Record<string, number | string>

/**
 * Chart shape: one x-category per product, one stacked series per branch. The categorical
 * palette has five slots and is never cycled, so past five branches the smallest (by total dough
 * weight) fold into a single `otherKey` series. Kept branches are listed in id order; a colour is
 * its position in that list, so it can shift when the filter changes which branches are kept.
 */
export function foldBranchSeries(totals: ProductBranchTotal[], otherKey: string) {
  const byBranch = new Map<string, number>()
  for (const total of totals) {
    byBranch.set(total.branchId, (byBranch.get(total.branchId) ?? 0) + total.mixWeightGrams)
  }
  const ranked = [...byBranch.entries()].sort((a, b) => b[1] - a[1]).map(([id]) => id)
  const folded = ranked.length > MAX_BRANCH_SERIES
  const keptIds = ranked.slice(0, MAX_BRANCH_SERIES)
  const kept = new Set(keptIds)
  const seriesBranchIds = folded ? [...keptIds.sort(), otherKey] : keptIds.sort()

  const rowsByProduct = new Map<string, ProductChartRow>()
  for (const total of totals) {
    const row = rowsByProduct.get(total.productId) ?? { productId: total.productId, productName: total.productName }
    const key = folded && !kept.has(total.branchId) ? otherKey : total.branchId
    row[key] = ((row[key] as number | undefined) ?? 0) + total.mixWeightGrams
    rowsByProduct.set(total.productId, row)
  }
  return { seriesBranchIds, chartData: [...rowsByProduct.values()] }
}

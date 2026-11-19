import type { ProductBranchTotal } from "./productionAggregate"

export interface BarSegment {
  /** A branch id, or the `otherKey` bucket. */
  seriesKey: string
  grams: number
  batchCount: number
}

export interface ProductBar {
  productId: string
  productName: string
  segments: BarSegment[]
  totalGrams: number
  batchCount: number
  /** Dough grams over the comparison period; `null` when that period has no batches of this product. */
  previousGrams: number | null
}

/**
 * One bar per product (design 3a): a segment per branch that mixed it, sorted by total dough
 * weight, most first. A product with no batches never appears. Branches outside `seriesKeys`
 * (past the palette's five slots) collapse into `otherKey`, matching `foldBranchSeries`.
 * `previous` is the same product's total over the comparison period (any branch, since the mark
 * compares totals).
 */
export function productBars(
  totals: ProductBranchTotal[],
  seriesKeys: string[],
  otherKey: string,
  previous: ProductBranchTotal[] = []
): ProductBar[] {
  const known = new Set(seriesKeys)
  const previousByProduct = new Map<string, number>()
  for (const total of previous) {
    previousByProduct.set(total.productId, (previousByProduct.get(total.productId) ?? 0) + total.mixWeightGrams)
  }
  const bars = new Map<string, ProductBar>()
  for (const total of totals) {
    if (total.mixWeightGrams <= 0 && total.batchCount <= 0) continue
    const bar = bars.get(total.productId) ?? {
      productId: total.productId,
      productName: total.productName,
      segments: [],
      totalGrams: 0,
      batchCount: 0,
      previousGrams: previousByProduct.get(total.productId) ?? null,
    }
    const seriesKey = known.has(total.branchId) ? total.branchId : otherKey
    const segment = bar.segments.find((s) => s.seriesKey === seriesKey)
    if (segment) {
      segment.grams += total.mixWeightGrams
      segment.batchCount += total.batchCount
    } else {
      bar.segments.push({ seriesKey, grams: total.mixWeightGrams, batchCount: total.batchCount })
    }
    bar.totalGrams += total.mixWeightGrams
    bar.batchCount += total.batchCount
    bars.set(total.productId, bar)
  }
  for (const bar of bars.values()) {
    bar.segments.sort((a, b) => seriesKeys.indexOf(a.seriesKey) - seriesKeys.indexOf(b.seriesKey))
  }
  return [...bars.values()].sort((a, b) => b.totalGrams - a.totalGrams)
}

export interface PeriodSummary {
  doughGrams: number
  batchCount: number
}

export function summarize(totals: ProductBranchTotal[]): PeriodSummary {
  return totals.reduce(
    (sum, row) => ({ doughGrams: sum.doughGrams + row.mixWeightGrams, batchCount: sum.batchCount + row.batchCount }),
    { doughGrams: 0, batchCount: 0 }
  )
}

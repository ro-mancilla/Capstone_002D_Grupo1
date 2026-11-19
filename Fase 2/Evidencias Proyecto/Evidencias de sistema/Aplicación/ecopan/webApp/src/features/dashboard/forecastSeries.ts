import type { ForecastVsActualDto } from "@/api/types"
import type { ModelKind } from "@/lib/labels"

/** Grams (by-weight products) or whole units (resold, by-unit products). The two are never added. */
export type ForecastUnit = "WEIGHT" | "UNIT"

export interface ForecastPoint {
  date: string
  predicted: number
  actual: number
}

export interface ProductPoint {
  date: string
  predicted: number | null
  actual: number
}

/** One forecast series: a (branch, Panadería product). The type is only for grouping. */
export interface ForecastSeriesInfo {
  productId: string
  name: string
  productType: string
}

export interface ForecastUnitGroup {
  unit: ForecastUnit
  /** Products (series) in this group, sorted by name. */
  series: ForecastSeriesInfo[]
  /** Group total per day: the sum of its products, which share one unit. */
  rows: ForecastPoint[]
  /** Per product id, its own day points (predicted stays null on days without a forecast). */
  points: Record<string, ProductPoint[]>
  /** Per product id, the estimator kinds seen across its forecast days (both when a series mixes them). */
  models: Record<string, ModelKind[]>
}

/** `lgbm-*` is the trained model, `baseline-*` a labelled cold-start baseline. Anything else
 * (including a missing version) is UNKNOWN: the page claims neither a model nor a lack of history
 * it cannot confirm. */
export function modelKind(modelVersion: string | null | undefined): ModelKind {
  if (modelVersion?.startsWith("lgbm-")) return "MODEL"
  if (modelVersion?.startsWith("baseline-")) return "BASELINE"
  return "UNKNOWN"
}

/**
 * The unit a product is forecast in. `forecasts` carries one of `predictedGrams` /
 * `predictedUnits` per row, so any forecast decides. A product that somehow has both is WEIGHT
 * (the worker's tie-break) and its unit rows are ignored everywhere -- never reclassified by row
 * order. A product with no forecast yet is read from what it actually sold. A product with no
 * signal (nothing predicted, nothing sold) is absent from the map. Keyed by product id.
 */
export function unitByProduct(rows: ForecastVsActualDto[]): Map<string, ForecastUnit> {
  const result = new Map<string, ForecastUnit>()
  for (const row of rows) {
    if (row.predictedGrams != null) result.set(row.productId, "WEIGHT")
    else if (row.predictedUnits != null && result.get(row.productId) !== "WEIGHT") {
      result.set(row.productId, "UNIT")
    }
  }
  for (const row of rows) {
    if (result.has(row.productId)) continue
    if (row.actualGrams > 0) result.set(row.productId, "WEIGHT")
    else if (row.actualUnits > 0) result.set(row.productId, "UNIT")
  }
  return result
}

/** The (predicted, actual) pair of a row in its product's unit; predicted is null with no forecast. */
export function valuesIn(row: ForecastVsActualDto, unit: ForecastUnit) {
  return unit === "WEIGHT"
    ? { predicted: row.predictedGrams, actual: row.actualGrams }
    : { predicted: row.predictedUnits, actual: row.actualUnits }
}

/**
 * Splits K8 rows into one group per unit so grams and units are never summed together: the
 * chart total only adds products of the same unit. A group with no forecast at all is omitted (the page's
 * cold-start state covers "nothing forecast").
 */
export function groupForecastByUnit(rows: ForecastVsActualDto[]): ForecastUnitGroup[] {
  const units = unitByProduct(rows)
  const groups: ForecastUnitGroup[] = []

  for (const unit of ["WEIGHT", "UNIT"] as const) {
    const byDate = new Map<string, ForecastPoint>()
    const models: Record<string, ModelKind[]> = {}
    const series = new Map<string, ForecastSeriesInfo>()
    const points: Record<string, ProductPoint[]> = {}
    let hasForecast = false

    for (const row of rows) {
      if (units.get(row.productId) !== unit) continue
      series.set(row.productId, { productId: row.productId, name: row.productName, productType: row.productType })
      const { predicted, actual } = valuesIn(row, unit)
      ;(points[row.productId] ??= []).push({ date: row.date, predicted, actual })
      const point = byDate.get(row.date) ?? { date: row.date, predicted: 0, actual: 0 }
      point.predicted += predicted ?? 0
      point.actual += actual
      byDate.set(row.date, point)

      if (predicted != null) {
        hasForecast = true
        const kinds = (models[row.productId] ??= [])
        const kind = modelKind(row.modelVersion)
        if (!kinds.includes(kind)) kinds.push(kind)
      }
    }

    if (!hasForecast) continue
    groups.push({
      unit,
      series: [...series.values()].sort((a, b) => a.name.localeCompare(b.name, "es-CL")),
      rows: [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date)),
      points,
      models,
    })
  }
  return groups
}

/** The filter value a Select can actually show: [selected] if it is still among [available],
 * otherwise [all]. A type or product that vanished (branch switched, data refetched) must read as
 * "all", never as a stale value the Select has no item for. */
export function resolveFilter(selected: string, available: readonly string[], all: string): string {
  return selected !== all && available.includes(selected) ? selected : all
}

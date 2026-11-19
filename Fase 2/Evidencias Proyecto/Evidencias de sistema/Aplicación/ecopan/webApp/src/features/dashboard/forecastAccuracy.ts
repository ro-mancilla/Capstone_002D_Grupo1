import type { ForecastVsActualDto } from "@/api/types"
import { unitByProduct, valuesIn, type ForecastUnit } from "./forecastSeries"

/** Window the accuracy figure is measured over ("Precisión últimas 4 semanas"). */
export const ACCURACY_WINDOW_DAYS = 28

/** Fewer scored days than this and the figure is noise, so it is hidden rather than shown. */
export const ACCURACY_MIN_DAYS = 7

export interface Accuracy {
  /** Σ|actual − predicted| / Σ actual, as a fraction (0.12 = 12 %). */
  wape: number
  /** Distinct past days that had both a forecast and were scored. */
  days: number
  /** Past days with sales but no forecast: not scored, disclosed so the figure isn't flattering. */
  unforecastedDays: number
}

export interface ForecastAccuracy {
  /** Per product id. */
  byProduct: Record<string, Accuracy | undefined>
  /** Pooled per unit: kg and units are never combined. */
  overall: Partial<Record<ForecastUnit, Accuracy>>
}

interface Tally {
  absError: number
  actual: number
  days: Set<string>
  unforecastedDays: Set<string>
}

const emptyTally = (): Tally => ({ absError: 0, actual: 0, days: new Set(), unforecastedDays: new Set() })

function tallyFor<K>(tallies: Map<K, Tally>, key: K): Tally {
  let tally = tallies.get(key)
  if (!tally) {
    tally = emptyTally()
    tallies.set(key, tally)
  }
  return tally
}

function toAccuracy(tally: Tally): Accuracy | undefined {
  if (tally.days.size < ACCURACY_MIN_DAYS || tally.actual <= 0) return undefined
  return { wape: tally.absError / tally.actual, days: tally.days.size, unforecastedDays: tally.unforecastedDays.size }
}

/**
 * WAPE of past forecasts against what was actually sold, derived from K8 rows (which already join
 * the forecast cache with actuals) so the page has one definition of "forecast vs. actual".
 *
 * A day is scored only if it is strictly before [today] (`yyyy-MM-dd`; today is still being
 * sold) and carries a forecast in its product's unit. Forecasts are not overwritten after their day
 * passes -- a refresh only upserts today..today+6 -- so each scored day holds the last forecast
 * made without that day's sales.
 */
export function computeAccuracy(rows: ForecastVsActualDto[], today: string): ForecastAccuracy {
  const units = unitByProduct(rows)
  const perProduct = new Map<string, Tally>()
  const perUnit = new Map<ForecastUnit, Tally>()

  for (const row of rows) {
    const unit = units.get(row.productId)
    if (!unit || row.date >= today) continue
    const { predicted, actual } = valuesIn(row, unit)
    const tallies = [tallyFor(perProduct, row.productId), tallyFor(perUnit, unit)]
    if (predicted == null) {
      if (actual > 0) for (const tally of tallies) tally.unforecastedDays.add(row.date)
      continue
    }

    for (const tally of tallies) {
      tally.absError += Math.abs(actual - predicted)
      tally.actual += actual
      tally.days.add(row.date)
    }
  }

  const result: ForecastAccuracy = { byProduct: {}, overall: {} }
  for (const [id, tally] of perProduct) result.byProduct[id] = toAccuracy(tally)
  for (const [unit, tally] of perUnit) {
    const accuracy = toAccuracy(tally)
    if (accuracy) result.overall[unit] = accuracy
  }
  return result
}

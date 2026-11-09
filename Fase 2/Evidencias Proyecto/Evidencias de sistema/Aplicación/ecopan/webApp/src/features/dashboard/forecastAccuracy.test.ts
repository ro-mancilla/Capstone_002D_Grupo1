import { describe, expect, it } from "vitest"
import type { ForecastVsActualDto } from "@/api/types"
import { ACCURACY_MIN_DAYS, computeAccuracy } from "./forecastAccuracy"

const day = (n: number) => `2026-01-${String(n).padStart(2, "0")}`
const grams = (n: number, productId: string, predicted: number, actual: number): ForecastVsActualDto => ({
  date: day(n),
  productId,
  productName: productId,
  productType: "Pan",
  predictedGrams: predicted,
  predictedUnits: null,
  actualGrams: actual,
  actualUnits: 0,
})
const units = (n: number, productId: string, predicted: number, actual: number): ForecastVsActualDto => ({
  date: day(n),
  productId,
  productName: productId,
  productType: "Pan",
  predictedGrams: null,
  predictedUnits: predicted,
  actualGrams: 0,
  actualUnits: actual,
})
const span = (build: (n: number) => ForecastVsActualDto) =>
  Array.from({ length: ACCURACY_MIN_DAYS }, (_, i) => build(i + 1))

describe("computeAccuracy", () => {
  it("is WAPE = sum|actual - predicted| / sum actual over past days", () => {
    // |100-90| = 10 on each day; sum actual = 100 per day -> 0.1
    const result = computeAccuracy(span((n) => grams(n, "PAN", 90, 100)), "2026-02-01")
    expect(result.byProduct.PAN?.wape).toBeCloseTo(0.1)
    expect(result.byProduct.PAN?.days).toBe(ACCURACY_MIN_DAYS)
    expect(result.overall.WEIGHT?.wape).toBeCloseTo(0.1)
  })

  it("ignores today and future days, and days without a forecast", () => {
    const rows = [
      ...span((n) => grams(n, "PAN", 100, 100)),
      grams(20, "PAN", 1, 100), // today
      grams(21, "PAN", 1, 100), // future
      { ...grams(15, "PAN", 0, 100), predictedGrams: null }, // actual only
    ]
    const result = computeAccuracy(rows, day(20))
    expect(result.byProduct.PAN).toMatchObject({ wape: 0, days: ACCURACY_MIN_DAYS })
  })

  it("hides a figure below the minimum number of days", () => {
    const result = computeAccuracy(span((n) => grams(n, "PAN", 90, 100)).slice(1), "2026-02-01")
    expect(result.byProduct.PAN).toBeUndefined()
    expect(result.overall.WEIGHT).toBeUndefined()
  })

  it("never combines kg and units: overall is per unit", () => {
    const rows = [...span((n) => grams(n, "PAN", 50, 100)), ...span((n) => units(n, "HALLULLA", 9, 10))]
    const result = computeAccuracy(rows, "2026-02-01")
    expect(result.overall.WEIGHT?.wape).toBeCloseTo(0.5)
    expect(result.overall.UNIT?.wape).toBeCloseTo(0.1)
  })

  it("pools products of one unit in the overall figure", () => {
    const rows = [...span((n) => grams(n, "PAN", 100, 100)), ...span((n) => grams(n, "TORTA", 0, 100))]
    const result = computeAccuracy(rows, "2026-02-01")
    expect(result.overall.WEIGHT?.wape).toBeCloseTo(0.5)
    expect(result.overall.WEIGHT?.days).toBe(ACCURACY_MIN_DAYS)
  })

  it("has no figure when nothing was sold", () => {
    const result = computeAccuracy(span((n) => grams(n, "PAN", 5, 0)), "2026-02-01")
    expect(result.byProduct.PAN).toBeUndefined()
  })

  it("counts past days with sales but no forecast instead of silently skipping them", () => {
    const rows = [
      ...span((n) => grams(n, "PAN", 100, 100)),
      { ...grams(10, "PAN", 0, 80), predictedGrams: null }, // sold, never forecast
      { ...grams(11, "PAN", 0, 0), predictedGrams: null }, // nothing sold, nothing forecast: not counted
      { ...grams(25, "PAN", 0, 80), predictedGrams: null }, // today: not counted
    ]
    const result = computeAccuracy(rows, day(25))
    expect(result.byProduct.PAN?.unforecastedDays).toBe(1)
    expect(result.overall.WEIGHT?.unforecastedDays).toBe(1)
    expect(result.byProduct.PAN?.wape).toBe(0)
  })

  it("scores a product with both gram and unit forecasts in grams only", () => {
    const rows = [
      ...span((n) => grams(n, "PAN", 90, 100)),
      ...span((n) => units(n + 10, "PAN", 1, 1000)),
    ]
    const result = computeAccuracy(rows, "2026-02-01")
    expect(result.byProduct.PAN?.days).toBe(ACCURACY_MIN_DAYS)
    expect(result.overall.UNIT).toBeUndefined()
  })
})

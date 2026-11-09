import { describe, expect, it } from "vitest"
import type { ForecastVsActualDto } from "@/api/types"
import { groupForecastByUnit, modelKind, resolveFilter } from "./forecastSeries"

const MARRAQUETA = "p-marraqueta"
const HALLULLA = "p-hallulla"
const TORTA = "p-torta"

const row = (over: Partial<ForecastVsActualDto>): ForecastVsActualDto => ({
  date: "2026-01-01",
  productId: MARRAQUETA,
  productName: "Marraqueta",
  productType: "Pan",
  predictedGrams: null,
  predictedUnits: null,
  actualGrams: 0,
  actualUnits: 0,
  ...over,
})

describe("groupForecastByUnit", () => {
  it("never adds grams and units: by-weight products and by-unit products land in separate groups", () => {
    const groups = groupForecastByUnit([
      row({ predictedGrams: 10_000, actualGrams: 9_000 }),
      row({ productId: TORTA, productName: "Torta", productType: "Pastelería", predictedGrams: 2_000, actualGrams: 1_000 }),
      row({ productId: HALLULLA, productName: "Hallulla", predictedUnits: 5, actualUnits: 4 }),
    ])

    const weight = groups.find((g) => g.unit === "WEIGHT")!
    const unit = groups.find((g) => g.unit === "UNIT")!
    expect(weight.rows).toEqual([{ date: "2026-01-01", predicted: 12_000, actual: 10_000 }])
    expect(unit.rows).toEqual([{ date: "2026-01-01", predicted: 5, actual: 4 }])
    expect(weight.series.map((s) => s.name)).toEqual(["Marraqueta", "Torta"])
    expect(unit.series.map((s) => s.name)).toEqual(["Hallulla"])
  })

  it("two products of one type are two series, even in one unit", () => {
    const [group] = groupForecastByUnit([
      row({ predictedGrams: 1_000, modelVersion: "baseline-swlw" }),
      row({ productId: TORTA, productName: "Amasado", predictedGrams: 500, modelVersion: "lgbm-x" }),
    ])
    // Sorted by name: Amasado before Marraqueta.
    expect(group.series.map((s) => s.productId)).toEqual([TORTA, MARRAQUETA])
    expect(group.models).toEqual({ [MARRAQUETA]: ["BASELINE"], [TORTA]: ["MODEL"] })
  })

  it("classifies a product by its actuals when the day has no forecast", () => {
    const groups = groupForecastByUnit([
      row({ date: "2026-01-01", productId: HALLULLA, actualUnits: 3 }),
      row({ date: "2026-01-02", productId: HALLULLA, predictedUnits: 6 }),
    ])
    expect(groups).toHaveLength(1)
    expect(groups[0].unit).toBe("UNIT")
    expect(groups[0].rows.map((r) => r.actual)).toEqual([3, 0])
  })

  it("omits a group that has no forecast at all", () => {
    expect(groupForecastByUnit([row({ productId: HALLULLA, actualUnits: 3 })])).toEqual([])
  })

  it("collects the estimator kinds per product, both when a series mixes versions", () => {
    const [group] = groupForecastByUnit([
      row({ date: "2026-01-01", predictedGrams: 1, modelVersion: "baseline-trailing-mean" }),
      row({ date: "2026-01-02", predictedGrams: 1, modelVersion: "lgbm-favorita" }),
      row({ date: "2026-01-03", productId: TORTA, productName: "Torta", predictedGrams: 1, modelVersion: "baseline-swlw" }),
      row({ date: "2026-01-04", productId: TORTA, productName: "Torta", actualGrams: 5 }),
    ])
    expect(group.models).toEqual({ [MARRAQUETA]: ["BASELINE", "MODEL"], [TORTA]: ["BASELINE"] })
  })
})

describe("groupForecastByUnit with missing or mixed data", () => {
  it("labels a forecast row without a modelVersion as UNKNOWN, never as nothing", () => {
    const [group] = groupForecastByUnit([row({ predictedGrams: 1 })])
    expect(group.models).toEqual({ [MARRAQUETA]: ["UNKNOWN"] })
  })

  it("keeps a product with both gram and unit forecasts in one unit (WEIGHT wins, as in the worker) and ignores its other-unit values", () => {
    const groups = groupForecastByUnit([
      row({ date: "2026-01-01", predictedGrams: 1_000, actualGrams: 900 }),
      row({ date: "2026-01-02", predictedUnits: 7, actualUnits: 6 }),
    ])
    expect(groups).toHaveLength(1)
    expect(groups[0].unit).toBe("WEIGHT")
    expect(groups[0].rows).toEqual([
      { date: "2026-01-01", predicted: 1_000, actual: 900 },
      { date: "2026-01-02", predicted: 0, actual: 0 },
    ])
  })
})

describe("modelKind", () => {
  it("maps known wire versions to a kind and everything else to UNKNOWN", () => {
    expect(modelKind("lgbm-favorita")).toBe("MODEL")
    expect(modelKind("baseline-default")).toBe("BASELINE")
    expect(modelKind("baseline-swlw")).toBe("BASELINE")
    expect(modelKind("something-else")).toBe("UNKNOWN")
    expect(modelKind(null)).toBe("UNKNOWN")
    expect(modelKind(undefined)).toBe("UNKNOWN")
  })
})

describe("resolveFilter", () => {
  it("keeps a selection that is still available and falls back to all otherwise", () => {
    expect(resolveFilter("Pastelería", ["Pan", "Pastelería"], "__all__")).toBe("Pastelería")
    expect(resolveFilter("Pastelería", ["Pan"], "__all__")).toBe("__all__")
    expect(resolveFilter("__all__", ["Pan"], "__all__")).toBe("__all__")
    expect(resolveFilter("Pan", [], "__all__")).toBe("__all__")
  })
})

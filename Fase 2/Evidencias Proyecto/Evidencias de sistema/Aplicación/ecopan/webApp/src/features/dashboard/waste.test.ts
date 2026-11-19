import { describe, expect, it } from "vitest"
import type { WasteEstimateDto } from "@/api/types"
import { totalWasteGrams, wasteByDay } from "./waste"

const row = (date: string, productType: string, estimatedWasteGrams: number | null): WasteEstimateDto => ({
  date,
  productType,
  producedGrams: 10_000,
  soldGrams: 4_000,
  soldUnits: 0,
  estimatedWasteGrams,
  estimated: true,
})

describe("wasteByDay", () => {
  it("sums product types within a day and sorts by date", () => {
    const days = wasteByDay([row("2026-03-11", "PAN", 1_000), row("2026-03-10", "PAN", 2_000), row("2026-03-10", "MASA", 500)])

    expect(days).toEqual([
      { date: "2026-03-10", wasteGrams: 2_500 },
      { date: "2026-03-11", wasteGrams: 1_000 },
    ])
  })

  it("leaves out unit-sold rows instead of counting them as zero waste", () => {
    expect(wasteByDay([row("2026-03-10", "EMPANADA", null)])).toEqual([])
  })
})

describe("totalWasteGrams", () => {
  it("adds every comparable row and ignores the rest", () => {
    expect(totalWasteGrams([row("2026-03-10", "PAN", 2_000), row("2026-03-10", "EMPANADA", null), row("2026-03-11", "PAN", 500)])).toBe(2_500)
  })
})

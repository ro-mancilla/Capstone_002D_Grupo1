import { describe, expect, it } from "vitest"
import type { ReconciliationRowDto } from "@/api/types"
import { formatLedgerFixed, formatShortDay, formatSignedKg, presetDays, reviewFlag } from "./reconciliation"

describe("ledger table formatting (design 3b)", () => {
  it("always shows three decimals, a real minus sign, and a bare 0 for nothing", () => {
    expect(formatLedgerFixed(200_000)).toBe("200,000")
    expect(formatLedgerFixed(-3_869)).toBe("−3,869")
    expect(formatLedgerFixed(181_548)).toBe("181,548")
    expect(formatLedgerFixed(1_181_548)).toBe("1.181,548")
    expect(formatLedgerFixed(0)).toBe("0")
  })

  it("trims and signs kilos for sentences", () => {
    expect(formatSignedKg(-12_500)).toBe("−12,5")
    expect(formatSignedKg(500)).toBe("+0,5")
  })

  it("formats a day as in the design, 'sep' not 'sept'", () => {
    expect(formatShortDay("2026-09-08")).toBe("8 sep 2026")
    expect(formatShortDay("2026-10-07")).toBe("7 oct 2026")
  })
})

describe("reviewFlag", () => {
  const base: ReconciliationRowDto = {
    ingredientId: "i",
    name: "x",
    openingGrams: 0,
    purchasedGrams: 200_000,
    decrementedGrams: -5_952,
    adjustedGrams: 0,
    closingGrams: 194_048,
    batchCount: 1,
  }

  it("flags a negative closing balance", () => {
    expect(reviewFlag({ ...base, closingGrams: -200 })).toEqual({ kind: "negativeClosing" })
  })

  it("flags an adjustment over 5 % of purchases, not one at or under it", () => {
    expect(reviewFlag({ ...base, adjustedGrams: -12_500 })).toEqual({ kind: "adjustment", grams: -12_500 })
    expect(reviewFlag({ ...base, adjustedGrams: -10_000 })).toBeNull()
  })

  it("lets a negative closing win over a large adjustment", () => {
    expect(reviewFlag({ ...base, closingGrams: -1, adjustedGrams: -50_000 })).toEqual({ kind: "negativeClosing" })
  })

  it("has no adjustment rule when nothing was purchased", () => {
    expect(reviewFlag({ ...base, purchasedGrams: 0, adjustedGrams: -500 })).toBeNull()
  })
})

describe("presetDays", () => {
  it("covers this month so far, the whole previous month, and the last 30 days", () => {
    expect(presetDays("thisMonth", "2026-10-07")).toEqual({ from: "2026-10-01", to: "2026-10-07" })
    expect(presetDays("lastMonth", "2026-10-07")).toEqual({ from: "2026-09-01", to: "2026-09-30" })
    expect(presetDays("lastMonth", "2026-01-15")).toEqual({ from: "2025-12-01", to: "2025-12-31" })
    expect(presetDays("last30", "2026-10-07")).toEqual({ from: "2026-09-08", to: "2026-10-07" })
  })
})

import { describe, expect, it } from "vitest"
import { endOfReportingDay, startOfReportingDay } from "@/lib/reportingDay"
import { resolvePreviousRange, resolveProductionRange } from "./productionFilters"

// 15:00 Santiago on 2026-01-10 (UTC-3).
const NOW = Date.parse("2026-01-10T18:00:00Z")

describe("resolveProductionRange", () => {
  it("'today' covers the whole reporting day, so a batch logged after the page opened is inside", () => {
    const { from, to } = resolveProductionRange("today", { fromDay: "2026-01-01", toDay: "2026-01-02" }, NOW)

    expect(from).toBe(startOfReportingDay("2026-01-10"))
    expect(to).toBe(endOfReportingDay("2026-01-10"))
    expect(to).toBeGreaterThan(NOW)
  })

  it("'7d' and '30d' end today and start 6 / 29 days earlier", () => {
    expect(resolveProductionRange("7d", { fromDay: "", toDay: "" }, NOW).from).toBe(startOfReportingDay("2026-01-04"))
    expect(resolveProductionRange("30d", { fromDay: "", toDay: "" }, NOW).from).toBe(startOfReportingDay("2025-12-12"))
  })

  it("a custom single day is exactly that reporting day", () => {
    const { from, to } = resolveProductionRange("custom", { fromDay: "2026-01-05", toDay: "2026-01-05" }, NOW)

    expect(from).toBe(startOfReportingDay("2026-01-05"))
    expect(to).toBe(endOfReportingDay("2026-01-05"))
    expect(to - from).toBe(24 * 3600 * 1000 - 1)
  })

  it("late evening Santiago is still today even though UTC has rolled over", () => {
    const lateEvening = Date.parse("2026-01-11T02:00:00Z") // 23:00 on the 10th in Santiago
    expect(resolveProductionRange("today", { fromDay: "", toDay: "" }, lateEvening).from).toBe(
      startOfReportingDay("2026-01-10")
    )
  })
})

describe("resolvePreviousRange", () => {
  it("is the same number of whole days right before the range", () => {
    const now = Date.parse("2026-10-07T15:00:00Z")
    const current = resolveProductionRange("7d", { fromDay: "", toDay: "" }, now)
    const previous = resolvePreviousRange("7d", { fromDay: "", toDay: "" }, now)
    expect(previous.to).toBe(current.from - 1)
    expect(previous.from).toBe(startOfReportingDay("2026-09-24"))
  })

  it("compares a custom range with the equally long range before it", () => {
    const previous = resolvePreviousRange("custom", { fromDay: "2026-01-05", toDay: "2026-01-05" }, 0)
    expect(previous).toEqual({ from: startOfReportingDay("2026-01-04"), to: endOfReportingDay("2026-01-04") })
  })
})

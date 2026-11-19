import { describe, expect, it } from "vitest"
import { forecastRanges } from "./forecastRanges"

const iso = (ms: number) => new Date(ms).toISOString()

describe("forecastRanges", () => {
  // 02:30Z on 2026-07-29 is still the 28th (22:30) in Santiago.
  const now = Date.parse("2026-07-29T02:30:00Z")

  it("aligns both windows to reporting-day boundaries", () => {
    const { overlay, accuracy } = forecastRanges(now)
    expect(iso(overlay.from)).toBe("2026-07-22T04:00:00.000Z") // 6 days before the 28th, 00:00 Santiago
    expect(iso(overlay.to)).toBe("2026-08-04T04:00:00.000Z") // 7 days after
    expect(iso(accuracy.from)).toBe("2026-06-30T04:00:00.000Z") // 28 days before the 28th
  })

  it("scores up to the end of yesterday, never today", () => {
    expect(iso(forecastRanges(now).accuracy.to)).toBe("2026-07-28T03:59:59.999Z")
  })

  it("is stable within a reporting day and moves at midnight", () => {
    const later = Date.parse("2026-07-29T03:59:00Z") // 23:59 Santiago, same day
    expect(forecastRanges(later)).toEqual(forecastRanges(now))
    const nextDay = Date.parse("2026-07-29T04:01:00Z")
    expect(forecastRanges(nextDay).accuracy.from).toBeGreaterThan(forecastRanges(now).accuracy.from)
  })

  it("counts calendar days, not 24h steps, across a DST change", () => {
    const afterSpringForward = Date.parse("2026-09-12T03:10:00Z") // 00:10 local, UTC-3
    expect(iso(forecastRanges(afterSpringForward).overlay.from)).toBe("2026-09-06T04:00:00.000Z")
  })
})

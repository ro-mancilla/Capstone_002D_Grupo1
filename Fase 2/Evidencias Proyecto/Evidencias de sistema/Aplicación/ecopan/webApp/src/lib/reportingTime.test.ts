import { describe, expect, it } from "vitest"
import { reportingDate, reportingDayStart } from "./reportingTime"

/** Fixed instants only: results must not depend on the machine's time zone. */
describe("reportingDate", () => {
  it("is the America/Santiago calendar day, not the UTC one", () => {
    // 02:30Z on the 29th is 22:30 on the 28th in Santiago (UTC-4 in winter).
    expect(reportingDate(Date.parse("2026-07-29T02:30:00Z"))).toBe("2026-07-28")
    expect(reportingDate(Date.parse("2026-07-29T04:00:00Z"))).toBe("2026-07-29")
  })

  it("follows daylight saving (UTC-3 in summer)", () => {
    expect(reportingDate(Date.parse("2026-01-15T02:59:00Z"))).toBe("2026-01-14")
    expect(reportingDate(Date.parse("2026-01-15T03:00:00Z"))).toBe("2026-01-15")
  })
})

describe("reportingDayStart", () => {
  it("is local midnight of the reporting day, as epoch millis", () => {
    expect(new Date(reportingDayStart(Date.parse("2026-07-29T02:30:00Z"))).toISOString()).toBe(
      "2026-07-28T04:00:00.000Z"
    )
    expect(new Date(reportingDayStart(Date.parse("2026-01-15T12:00:00Z"))).toISOString()).toBe(
      "2026-01-15T03:00:00.000Z"
    )
  })

  it("is idempotent", () => {
    const start = reportingDayStart(Date.parse("2026-07-29T15:00:00Z"))
    expect(reportingDayStart(start)).toBe(start)
  })

  it("shifts by calendar days across a DST change (Santiago springs forward 2026-09-06)", () => {
    // 00:10 on 2026-09-12 (UTC-3). Six 24h-steps back lands on 23:10 of Sep 5 -- the wrong day.
    const now = Date.parse("2026-09-12T03:10:00Z")
    expect(new Date(reportingDayStart(now, -6)).toISOString()).toBe("2026-09-06T04:00:00.000Z")
    expect(new Date(reportingDayStart(now, -7)).toISOString()).toBe("2026-09-05T04:00:00.000Z")
    expect(new Date(reportingDayStart(now, 7)).toISOString()).toBe("2026-09-19T03:00:00.000Z")
  })
})

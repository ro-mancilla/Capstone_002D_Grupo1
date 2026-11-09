import { describe, expect, it } from "vitest"
import { addDays, endOfReportingDay, reportingDate, startOfReportingDay } from "./reportingDay"

describe("reporting-timezone days (America/Santiago)", () => {
  it("names the day an instant falls on in Santiago, not in UTC", () => {
    // 02:00Z on the 11th is 23:00 on the 10th in January (UTC-3).
    expect(reportingDate(Date.parse("2026-01-11T02:00:00Z"))).toBe("2026-01-10")
    // 04:00Z on the 11th is already 00:00 on the 11th in July (UTC-4)... 03:59Z is still the 10th.
    expect(reportingDate(Date.parse("2026-07-11T03:59:00Z"))).toBe("2026-07-10")
    expect(reportingDate(Date.parse("2026-07-11T04:00:00Z"))).toBe("2026-07-11")
  })

  it("starts a day at Santiago midnight in summer and winter", () => {
    expect(new Date(startOfReportingDay("2026-01-10")).toISOString()).toBe("2026-01-10T03:00:00.000Z")
    expect(new Date(startOfReportingDay("2026-07-10")).toISOString()).toBe("2026-07-10T04:00:00.000Z")
  })

  it("ends a day one millisecond before the next one starts", () => {
    expect(endOfReportingDay("2026-01-10")).toBe(startOfReportingDay("2026-01-11") - 1)
  })

  it("does the day arithmetic on calendar dates", () => {
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28")
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01")
  })
})

import { describe, expect, it } from "vitest"
import { formatDayLabel, formatShortDayLabel } from "./format"
import { reportingDays } from "./reportingDay"

describe("reportingDays", () => {
  it("lists seven consecutive reporting-zone days ending today, oldest first", () => {
    // 2026-10-14 02:00 UTC is still the evening of the 13th in Santiago (UTC-3).
    const days = reportingDays(Date.parse("2026-10-14T02:00:00Z"), 7)
    expect(days).toHaveLength(7)
    expect(days[0]).toBe("2026-10-07")
    expect(days[6]).toBe("2026-10-13")
  })
})

describe("formatDayLabel", () => {
  it("renders a calendar day in es-CL, never as an ISO date", () => {
    expect(formatDayLabel("2026-10-08")).toBe("jue 8 oct")
    expect(formatShortDayLabel("2026-10-08")).toBe("8 oct")
  })
})

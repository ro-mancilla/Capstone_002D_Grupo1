import { describe, expect, it } from "vitest"
import { render } from "@testing-library/react"
import { ForecastBars } from "./ForecastBars"
import { axisLabel, chartDays, freshness } from "./forecastFormat"

describe("ForecastBars", () => {
  const today = "2026-10-07"
  const points = [
    { date: "2026-10-06", predicted: 10_000, actual: 9_000 },
    { date: "2026-10-07", predicted: 12_000, actual: 4_000 },
    { date: "2026-10-08", predicted: 22_700, actual: 0 },
  ]

  it("never draws a sold bar for a future day, even if the wire says 0", () => {
    const { container } = render(
      <ForecastBars name="Marraqueta" unit="WEIGHT" points={points} today={today} tomorrow="2026-10-08" />
    )
    // 2 past/today days carry a sold bar; tomorrow carries only its forecast.
    expect(container.querySelectorAll(".bg-chart-1")).toHaveLength(2)
    expect(container.querySelectorAll(".border-chart-2")).toHaveLength(3)
  })

  it("states tomorrow's forecast in the product's unit", () => {
    const { getByText } = render(
      <ForecastBars name="Marraqueta" unit="WEIGHT" points={points} today={today} tomorrow="2026-10-08" />
    )
    expect(getByText("22,7 kg")).toBeInTheDocument()
  })
})

describe("forecastFormat", () => {
  it("spans 6 days back to 7 ahead", () => {
    const days = chartDays("2026-10-07")
    expect(days).toHaveLength(14)
    expect(days[6]).toBe("2026-10-07")
    expect(days[13]).toBe("2026-10-14")
  })

  it("labels the axis with the weekday initial and the day", () => {
    expect(axisLabel("2026-10-05")).toEqual({ weekday: "L", day: 5 })
  })

  it("calls a forecast from before yesterday stale", () => {
    const now = Date.parse("2026-10-07T15:00:00Z")
    expect(freshness(Date.parse("2026-10-07T09:15:00Z"), now)).toMatchObject({ stale: false })
    expect(freshness(Date.parse("2026-10-05T04:05:00Z"), now)).toMatchObject({ stale: true })
  })
})

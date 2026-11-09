import { describe, expect, it } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import type { SalesByDayDto } from "@/api/types"
import { renderApp } from "@/test/router-utils"

/**
 * Plan 07 §10's mandated sales-KPI-rendering test surface (#2). Fixture buckets mirror
 * `AnalyticsServiceImpl.salesByDay`'s real shape: one weight-sold product type (PAN,
 * `weightGrams > 0`, `units === 0`) and one unit-sold type (EMPANADA, the reverse) -- see
 * `aggregate.test.ts` for the empty-day-gap and per-type-stacking-totals assertions, which are
 * exact-value checks better made against the pure aggregation functions than parsed out of
 * rendered SVG.
 */
const FIXTURE_ROWS: SalesByDayDto[] = [
  {
    date: "2026-01-01",
    branchId: FIXTURE.branch.id,
    productType: "PAN",
    salesCount: 3,
    weightGrams: 2500,
    units: 0,
    revenueMinor: 990_000,
  },
  {
    date: "2026-01-02",
    branchId: FIXTURE.branch.id,
    productType: "EMPANADA",
    salesCount: 10,
    weightGrams: 0,
    units: 40,
    revenueMinor: 40_000,
  },
]

async function loginAsGeneralManager() {
  const user = userEvent.setup()
  await screen.findByText(/iniciar sesión/i)
  await user.type(screen.getByLabelText(/panadería/i), "reference")
  await user.type(screen.getByLabelText(/usuario/i), "genmgr")
  await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
  await user.click(screen.getByRole("button", { name: /ingresar/i }))
  return user
}

describe("SalesPage (plan 07 §10 mandated test surface #2)", () => {
  it("formats CLP with no spurious decimals, stacks per-type totals, and labels kg vs. pieces correctly", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/sales-by-day`, () => HttpResponse.json(FIXTURE_ROWS))
    )

    renderApp("/analytics/sales")
    await loginAsGeneralManager()

    await screen.findByRole("heading", { name: "Ventas" })

    // CLP is zero-decimal -- 990000 minor units must render as "$990.000", never "$990.000,00".
    const revenueCell = await screen.findByText("$990.000")
    expect(revenueCell.textContent).not.toMatch(/,00/)

    // Weight-sold product type (PAN) is labeled in kg...
    expect(await screen.findByText("2,500 kg")).toBeInTheDocument()
    // ...unit-sold product type (EMPANADA) is labeled in pieces, not kg.
    expect(await screen.findByText("40 u.")).toBeInTheDocument()

    // Per-type totals table sums each row's own revenue -- both are present and distinct.
    expect(screen.getByText("$40.000")).toBeInTheDocument()
  })

  it("changing the date-range filter issues a request with a different query key/params", async () => {
    const requestedRanges: string[] = []
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/sales-by-day`, ({ request }) => {
        const url = new URL(request.url)
        requestedRanges.push(`${url.searchParams.get("from")}-${url.searchParams.get("to")}`)
        return HttpResponse.json(FIXTURE_ROWS)
      })
    )

    renderApp("/analytics/sales")
    const user = await loginAsGeneralManager()

    await screen.findByRole("heading", { name: "Ventas" })
    await waitFor(() => expect(requestedRanges.length).toBeGreaterThanOrEqual(1))
    const initialRangeCount = requestedRanges.length

    // Default preset is "7 días" -- switching to "Hoy" changes `from` (today's start vs. 7 days
    // back), which changes the query key and must trigger a brand-new network request.
    await user.click(screen.getByRole("button", { name: "Hoy" }))

    await waitFor(() => expect(requestedRanges.length).toBeGreaterThan(initialRangeCount))
    const [firstRange, ...rest] = requestedRanges
    expect(rest.some((range) => range !== firstRange)).toBe(true)
  })
})

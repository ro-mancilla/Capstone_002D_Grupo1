import { describe, expect, it } from "vitest"
import { fireEvent, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import type { LoginRequest, LoginResponse, ProductionSummaryDto, TenantProfileResponse } from "@/api/types"
import { renderApp, renderRouterAt } from "@/test/router-utils"
import { managerSession } from "@/test/fixtures"
import { endOfReportingDay, reportingDate, startOfReportingDay } from "@/lib/reportingDay"

async function loginAsGeneralManager() {
  const user = userEvent.setup()
  await screen.findByText(/iniciar sesión/i)
  await user.type(screen.getByLabelText(/panadería/i), "reference")
  await user.type(screen.getByLabelText(/usuario/i), "genmgr")
  await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
  await user.click(screen.getByRole("button", { name: /ingresar/i }))
  return user
}

/** Serves `rows` and records every request's query string. */
function serveProduction(rows: ProductionSummaryDto[]) {
  const requests: URLSearchParams[] = []
  server.use(
    mswHttp.get(`${API_BASE_URL}/analytics/production-summary`, ({ request }) => {
      requests.push(new URL(request.url).searchParams)
      return HttpResponse.json(rows)
    })
  )
  return requests
}

/** The request for the chosen range: each page load also asks for the range before it. */
function currentRequest(requests: URLSearchParams[]) {
  const isPrevious = (r: URLSearchParams) =>
    requests.some((other) => Number(other.get("from")) === Number(r.get("to")) + 1)
  return requests.filter((r) => !isPrevious(r)).at(-1)!
}

/** The tenant has these branches (the fixture only has one). */
function withBranches(branches: { id: string; name: string; active: boolean }[]) {
  const profile: TenantProfileResponse = {
    id: FIXTURE.tenant.id,
    name: FIXTURE.tenant.name,
    slug: FIXTURE.tenant.slug,
    currencyCode: FIXTURE.tenant.currencyCode,
    enabledModules: [...FIXTURE.tenant.enabledModules],
    branches,
  }
  server.use(
    mswHttp.post<never, LoginRequest>(`${API_BASE_URL}/auth/login`, () => {
      const response: LoginResponse = {
        accessToken: "access-token-1",
        refreshToken: "refresh-token-1",
        user: FIXTURE.genMgr,
        tenant: {
          id: profile.id,
          name: profile.name,
          currencyCode: profile.currencyCode,
          enabledModules: profile.enabledModules,
        },
        branches,
      }
      return HttpResponse.json(response)
    }),
    mswHttp.get(`${API_BASE_URL}/tenant`, () => HttpResponse.json(profile))
  )
}

function row(overrides: Partial<ProductionSummaryDto>): ProductionSummaryDto {
  return {
    date: "2026-01-10",
    branchId: FIXTURE.branch.id,
    productId: "p-marraqueta",
    productName: "Marraqueta",
    productType: "Pan",
    batchCount: 1,
    mixWeightGrams: 1000,
    ...overrides,
  }
}

describe("ProductionPage (HU-ANA-0003)", () => {
  it("shows dough kilos and batch count per product and branch, labelled as masa", async () => {
    serveProduction([
      row({ date: "2026-01-10", batchCount: 2, mixWeightGrams: 18_000 }),
      row({ date: "2026-01-11", batchCount: 1, mixWeightGrams: 7_000 }),
      row({ productId: "p-croissant", productName: "Croissant", productType: "Pastelería", mixWeightGrams: 3_000 }),
    ])

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    const table = await screen.findByRole("table")
    const marraqueta = within(table).getByRole("row", { name: /Marraqueta/ })
    // 18 kg + 7 kg across both days, 3 batches, grouped under the fixture branch.
    expect(within(table).getByRole("row", { name: "Main" })).toBeInTheDocument()
    expect(within(marraqueta).getByText("25,000 kg")).toBeInTheDocument()
    expect(within(marraqueta).getByText("3")).toBeInTheDocument()
    expect(within(table).getByRole("columnheader", { name: "Kg de masa" })).toBeInTheDocument()
    expect(screen.getByText(/no de pan terminado/i)).toBeInTheDocument()
  })

  it("keeps two products with the same name apart", async () => {
    serveProduction([
      row({ productId: "p1", productName: "Pan amasado", mixWeightGrams: 4_000 }),
      row({ productId: "p2", productName: "Pan amasado", mixWeightGrams: 6_000 }),
    ])

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    const table = await screen.findByRole("table")
    expect(within(table).getAllByRole("row", { name: /Pan amasado/ })).toHaveLength(2)
    expect(within(table).getByText("4,000 kg")).toBeInTheDocument()
    expect(within(table).getByText("6,000 kg")).toBeInTheDocument()
  })

  it("labels a branch the session does not know neutrally, never by its id or as an error", async () => {
    serveProduction([row({ branchId: "00000000-0000-0000-0000-00000000dead" })])

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    expect(await screen.findByText("Otra sucursal")).toBeInTheDocument()
    expect(screen.queryByText(/dead/)).not.toBeInTheDocument()
    expect(screen.queryByText(/desconocida/i)).not.toBeInTheDocument()
  })

  it("labels a product with no name neutrally, never by its id", async () => {
    serveProduction([row({ productId: "00000000-0000-0000-0000-0000000000ab", productName: "  " })])

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    const table = await screen.findByRole("table")
    expect(within(table).getByText("Producto sin nombre")).toBeInTheDocument()
    expect(screen.queryByText(/0000000000ab/)).not.toBeInTheDocument()
  })

  it("shows the empty state when there are no batches", async () => {
    serveProduction([])

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    expect(await screen.findByText(/no hay datos para el período/i)).toBeInTheDocument()
    expect(screen.queryByRole("table")).not.toBeInTheDocument()
  })

  it("shows an error, not the empty state, when the request fails", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/production-summary`, () =>
        HttpResponse.json({ code: "BOOM" }, { status: 500 })
      )
    )

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    // The KPI queries retry once, so the error surfaces after the retry delay.
    expect(await screen.findByText(/no se pudieron cargar los datos/i, undefined, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByText(/no hay datos para el período/i)).not.toBeInTheDocument()
  })

  it("asks for the whole of today, so a batch logged after the page opened is inside the range", async () => {
    const requests = serveProduction([row({})])

    renderApp("/analytics/production")
    await loginAsGeneralManager()
    await screen.findByRole("table")

    const to = Number(currentRequest(requests).get("to"))
    expect(to).toBe(endOfReportingDay(reportingDate(Date.now())))
    expect(to).toBeGreaterThan(Date.now())
  })

  it("queries exactly one reporting day when the custom range is a single day", async () => {
    const requests = serveProduction([row({})])

    renderApp("/analytics/production")
    const user = await loginAsGeneralManager()
    await screen.findByRole("table")

    await user.click(screen.getByRole("button", { name: "Personalizado" }))
    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2026-01-05" } })
    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2026-01-05" } })

    await waitFor(() => {
      const last = currentRequest(requests)
      expect(Number(last.get("from"))).toBe(startOfReportingDay("2026-01-05"))
      expect(Number(last.get("to"))).toBe(endOfReportingDay("2026-01-05"))
    })
  })

  it("filters by the branch picked in the branch selector", async () => {
    const north = { id: "11111111-1111-4111-8111-111111111111", name: "Norte", active: true }
    const south = { id: "22222222-2222-4222-8222-222222222222", name: "Sur", active: true }
    withBranches([north, south])
    const requests = serveProduction([row({ branchId: north.id })])

    renderApp("/analytics/production")
    const user = await loginAsGeneralManager()
    await screen.findByRole("table")
    expect(currentRequest(requests).get("branchId")).toBeNull()

    await user.click(screen.getByRole("combobox", { name: "Sucursal" }))
    await user.click(await screen.findByRole("option", { name: "Sur" }))

    await waitFor(() => expect(currentRequest(requests).get("branchId")).toBe(south.id))
  })

  const north = { id: "11111111-1111-4111-8111-111111111111", name: "Norte", active: true }
  const south = { id: "22222222-2222-4222-8222-222222222222", name: "Sur", active: true }

  it("does not mention a striped second branch when only one branch is stacked", async () => {
    withBranches([north, south])
    serveProduction([row({ branchId: north.id })])

    renderApp("/analytics/production")
    await loginAsGeneralManager()
    await screen.findByRole("table")
    expect(screen.queryByText(/patrón rayado/i)).not.toBeInTheDocument()
  })

  it("mentions the striped second branch when two branches are stacked", async () => {
    withBranches([north, south])
    serveProduction([row({ branchId: north.id }), row({ branchId: south.id })])

    renderApp("/analytics/production")
    await loginAsGeneralManager()
    expect(await screen.findByText(/patrón rayado/i)).toBeInTheDocument()
  })

  it("folds branches past the fifth into one Otros series", async () => {
    const branches = Array.from({ length: 7 }, (_, i) => ({
      id: `00000000-0000-4000-8000-00000000000${i}`,
      name: `Sucursal ${i + 1}`,
      active: true,
    }))
    withBranches(branches)
    serveProduction(branches.map((branch, i) => row({ branchId: branch.id, mixWeightGrams: (i + 1) * 1000 })))

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    // The table still lists every branch (heading + product row each); the chart legend has five
    // branches plus one Otros.
    const table = await screen.findByRole("table")
    expect(within(table).getAllByRole("row")).toHaveLength(1 + 7 * 2)
    expect(await screen.findByText("Otros")).toBeInTheDocument()
  })
})

describe("ProductionPage comparison and bars (design 3a)", () => {
  /** Serves `current` for the chosen range and `previous` for the range right before it. */
  function serveCurrentAndPrevious(current: ProductionSummaryDto[], previous: ProductionSummaryDto[]) {
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/production-summary`, ({ request }) => {
        const to = Number(new URL(request.url).searchParams.get("to"))
        return HttpResponse.json(to >= startOfReportingDay(reportingDate(Date.now())) ? current : previous)
      })
    )
  }

  it("compares the totals with the same number of days before, in the cards", async () => {
    serveCurrentAndPrevious(
      [row({ mixWeightGrams: 60_000, batchCount: 4 })],
      [row({ date: "2026-01-01", mixWeightGrams: 50_000, batchCount: 3 })]
    )

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    expect(await screen.findByText("+10,000 kg de masa")).toBeInTheDocument()
    expect(screen.getByText(/frente a los 7 días anteriores \(50,000 kg\)/)).toBeInTheDocument()
    expect(screen.getByText("+1 lote")).toBeInTheDocument()
    expect(screen.getByText(/frente a los 7 días anteriores \(3\)/)).toBeInTheDocument()
  })

  it("omits the comparison when the previous period has no batches", async () => {
    serveCurrentAndPrevious([row({ mixWeightGrams: 60_000, batchCount: 4 })], [])

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    await screen.findByRole("table")
    expect(screen.queryByText(/frente a los/)).not.toBeInTheDocument()
    expect(screen.queryByTestId("previous-mark")).not.toBeInTheDocument()
  })

  it("prints each branch's kilos inside its bar segment and a tick at the previous total", async () => {
    const north = { id: "11111111-1111-4111-8111-111111111111", name: "Norte", active: true }
    const south = { id: "22222222-2222-4222-8222-222222222222", name: "Sur", active: true }
    withBranches([north, south])
    serveCurrentAndPrevious(
      [
        row({ branchId: north.id, mixWeightGrams: 81_000, batchCount: 6 }),
        row({ branchId: south.id, mixWeightGrams: 54_000, batchCount: 4 }),
      ],
      [row({ date: "2026-01-01", branchId: north.id, mixWeightGrams: 120_000 })]
    )

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    const segment = await screen.findByRole("img", { name: "Marraqueta · Norte · 81,000 kg de masa · 6 lotes" })
    expect(segment).toHaveTextContent("81,000 kg")
    expect(screen.getByRole("img", { name: "Marraqueta · Sur · 54,000 kg de masa · 4 lotes" })).toHaveTextContent("54,000 kg")
    expect(screen.getByText("135,000 kg de masa")).toBeInTheDocument()
    expect(screen.getByTestId("previous-mark")).toBeInTheDocument()
    expect(screen.getByText("7 días anteriores")).toBeInTheDocument()
  })

  it("hides the branch legend when there is a single branch", async () => {
    serveCurrentAndPrevious([row({ mixWeightGrams: 9_000 })], [])

    renderApp("/analytics/production")
    await loginAsGeneralManager()

    await screen.findByRole("table")
    // Only the table's group heading names the branch: no legend, and no previous-period key.
    expect(screen.getAllByText("Main")).toHaveLength(1)
    expect(screen.queryByText("7 días anteriores")).not.toBeInTheDocument()
  })

  it("opens on Hoy for the branch and highlights the product when it comes from a batch notice", async () => {
    const requests = serveProduction([row({ productId: "p-marraqueta" })])

    const { router } = renderApp("/analytics/production")
    await loginAsGeneralManager()
    await screen.findByRole("table")
    // Leave and come back, as when following the notice from another page.
    await router.navigate({ to: "/analytics" })
    await router.navigate({ to: "/analytics/production", search: { today: true, highlight: "p-marraqueta" } })

    const table = await screen.findByRole("table")
    const marraqueta = within(table).getByRole("row", { name: /Marraqueta/ })
    expect(marraqueta).toHaveClass("bg-peach-soft")
    const today = startOfReportingDay(reportingDate(Date.now()))
    expect(Number(currentRequest(requests).get("from"))).toBe(today)
  })
})

describe("analytics/production route guard", () => {
  it("sends a session without ANALYTICS_DASHBOARD somewhere it can open, and stays there", async () => {
    const session = managerSession({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] })
    const { router } = renderRouterAt("/analytics/production", session)

    await waitFor(() => expect(router.state.location.pathname).not.toBe("/analytics/production"))
    await waitFor(() => expect(router.state.status).toBe("idle"))
    expect(router.state.location.pathname.startsWith("/analytics")).toBe(false)
  })

  it("sends a session without VIEW_DASHBOARD away, and ends on a terminal page", async () => {
    const session = managerSession({ role: "BAKER" })
    const { router } = renderRouterAt("/analytics/production", session)

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"))
    await waitFor(() => expect(router.state.status).toBe("idle"))
    expect(router.state.location.pathname).toBe("/login")
  })
})

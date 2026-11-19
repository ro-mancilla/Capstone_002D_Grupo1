import { describe, expect, it } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import type { ForecastDto, LoginRequest, LoginResponse, Role, TenantProfileResponse } from "@/api/types"
import { renderApp } from "@/test/router-utils"

/** Forecasts #46: the *Actualizar pronóstico* button (F2, `MODULES_TOGGLE` -- GM/ADMIN only) and
 * the "Actualizado" freshness line on the Pronóstico page. */

const BRANCH_MANAGER = {
  id: "b1b1b1b1-0000-4000-8000-000000000001",
  displayName: "Beto Branch Manager",
  role: "BRANCH_MANAGER" as const,
  branchId: FIXTURE.branch.id,
}

function loginAs(user: { id: string; displayName: string; role: Role; branchId: string | null }) {
  const modules = [...FIXTURE.tenant.enabledModules, "DEMAND_FORECASTING"] as const
  const tenantProfile: TenantProfileResponse = {
    id: FIXTURE.tenant.id,
    name: FIXTURE.tenant.name,
    slug: FIXTURE.tenant.slug,
    currencyCode: FIXTURE.tenant.currencyCode,
    enabledModules: [...modules],
    branches: [FIXTURE.branch],
  }
  server.use(
    mswHttp.post<never, LoginRequest>(`${API_BASE_URL}/auth/login`, () => {
      const response: LoginResponse = {
        accessToken: "access-token-1",
        refreshToken: "refresh-token-1",
        user,
        tenant: {
          id: tenantProfile.id,
          name: tenantProfile.name,
          currencyCode: tenantProfile.currencyCode,
          enabledModules: tenantProfile.enabledModules,
        },
        branches: [FIXTURE.branch],
      }
      return HttpResponse.json(response)
    }),
    mswHttp.get(`${API_BASE_URL}/tenant`, () => HttpResponse.json(tenantProfile)),
    mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, () => HttpResponse.json([]))
  )
}

async function login() {
  const user = userEvent.setup()
  await screen.findByText(/iniciar sesión/i)
  await user.type(screen.getByLabelText(/panadería/i), "reference")
  await user.type(screen.getByLabelText(/usuario/i), "someone")
  await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
  await user.click(screen.getByRole("button", { name: /ingresar/i }))
  return user
}

const GENERATED_AT = Date.UTC(2026, 6, 20, 4, 5)
const forecastRow = (generatedAt: number): ForecastDto => ({
  id: "f1",
  branchId: FIXTURE.branch.id,
  productType: "PAN",
  forecastDate: "2026-07-21",
  predictedGrams: 10_000,
  predictedUnits: null,
  modelVersion: "lgbm",
  generatedAt,
})

describe("ForecastRefresh", () => {
  it("is hidden for a branch manager, who cannot call F2", async () => {
    loginAs(BRANCH_MANAGER)
    server.use(mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json([forecastRow(GENERATED_AT)])))

    renderApp("/analytics/forecast")
    await login()

    expect(await screen.findByText(/^actualizado /i)).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /(actualizar|generar) pronóstico/i })).not.toBeInTheDocument()
  })

  it("posts F2 for the selected branch when a general manager clicks it", async () => {
    loginAs(FIXTURE.genMgr)
    let body: unknown = null
    server.use(
      mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json([forecastRow(GENERATED_AT)])),
      mswHttp.post(`${API_BASE_URL}/forecasts/refresh`, async ({ request }) => {
        body = await request.json()
        return new HttpResponse(null, { status: 202 })
      })
    )

    renderApp("/analytics/forecast")
    const user = await login()
    await user.click(await screen.findByRole("button", { name: /(actualizar|generar) pronóstico/i }))

    await waitFor(() => expect(body).toEqual({ branchId: FIXTURE.branch.id }))
    expect(await screen.findByText(/pronóstico en actualización/i)).toBeInTheDocument()
  })

  it("shows when the forecast was last generated", async () => {
    loginAs(FIXTURE.genMgr)
    server.use(mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json([forecastRow(GENERATED_AT)])))

    renderApp("/analytics/forecast")
    await login()

    const label = await screen.findByText(/^actualizado /i)
    expect(label.textContent).toMatch(/^Actualizado (hoy|ayer|\d{1,2} \p{L}+), \d{2}:\d{2}$/u)
  })

  it("reads freshness with a single-day window (from == to), distinguishable from range readers", async () => {
    loginAs(FIXTURE.genMgr)
    const seen: URL[] = []
    server.use(
      mswHttp.get(`${API_BASE_URL}/forecasts`, ({ request }) => {
        seen.push(new URL(request.url))
        return HttpResponse.json([forecastRow(GENERATED_AT)])
      })
    )

    renderApp("/analytics/forecast")
    await login()
    await screen.findByText(/^actualizado /i)

    expect(seen.length).toBeGreaterThan(0)
    for (const url of seen) {
      expect(url.searchParams.get("from")).toBe(url.searchParams.get("to"))
    }
  })
})

import { afterEach, describe, expect, it, vi } from "vitest"
import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import type {
  ForecastDto,
  ForecastVsActualDto,
  LoginRequest,
  LoginResponse,
  TenantProfileResponse,
} from "@/api/types"
import { renderApp } from "@/test/router-utils"
import { reportingDate } from "@/lib/reportingTime"

/** `DEMAND_FORECASTING` isn't in the shared `FIXTURE.tenant` (most tests don't need it) -- these
 * tests override both the login response and the `GET /tenant` refetch so the module is already
 * enabled the moment `dashboard/forecast`'s route guard runs, right after login. `GET /forecasts`
 * (tomorrow's suggested production) defaults to no rows. */
function withDemandForecasting() {
  const tenantProfile: TenantProfileResponse = {
    id: FIXTURE.tenant.id,
    name: FIXTURE.tenant.name,
    slug: FIXTURE.tenant.slug,
    currencyCode: FIXTURE.tenant.currencyCode,
    enabledModules: [...FIXTURE.tenant.enabledModules, "DEMAND_FORECASTING"],
    branches: [FIXTURE.branch],
  }
  server.use(
    mswHttp.post<never, LoginRequest>(`${API_BASE_URL}/auth/login`, () => {
      const response: LoginResponse = {
        accessToken: "access-token-1",
        refreshToken: "refresh-token-1",
        user: FIXTURE.genMgr,
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
    mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json([]))
  )
}

async function loginAsGeneralManager() {
  const user = userEvent.setup()
  await screen.findByText(/iniciar sesión/i)
  await user.type(screen.getByLabelText(/panadería/i), "reference")
  await user.type(screen.getByLabelText(/usuario/i), "genmgr")
  await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
  await user.click(screen.getByRole("button", { name: /ingresar/i }))
  return user
}

const MARRAQUETA = { productId: "p-marraqueta", productName: "Marraqueta", productType: "Pan" }
const HALLULLA = { productId: "p-hallulla", productName: "Hallulla", productType: "Pan" }
const TORTA = { productId: "p-torta", productName: "Torta", productType: "Pastelería" }

/** Plan 07 §11 item 9's AC, re-keyed by #51: the forecast page renders a 7-day per-product
 * forecast overlay from stubbed `/analytics/forecast-vs-actual` data, and shows the designed
 * cold-start state (not an error) when the backend has no forecast rows yet. */
describe("ForecastPage", () => {
  afterEach(() => vi.useRealTimers())

  /** `yyyy-MM-dd` for `daysAgo` days before today, in the reporting zone as the page computes it. */
  const dayString = (daysAgo: number) => reportingDate(Date.now() - daysAgo * 86_400_000)

  const kgRow = (
    product: typeof MARRAQUETA,
    over: Partial<ForecastVsActualDto> = {}
  ): ForecastVsActualDto => ({
    date: dayString(0),
    ...product,
    predictedGrams: 10_000,
    predictedUnits: null,
    actualGrams: 0,
    actualUnits: 0,
    modelVersion: "lgbm-favorita",
    ...over,
  })

  const pastDays = (product: typeof MARRAQUETA, predicted: number, actual: number): ForecastVsActualDto[] =>
    Array.from({ length: 7 }, (_, i) =>
      kgRow(product, { date: dayString(i + 1), predictedGrams: predicted, actualGrams: actual })
    )

  const serveKpi = (rows: ForecastVsActualDto[]) =>
    server.use(mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, () => HttpResponse.json(rows)))

  it("shows the cold-start 'collecting history' state when there is no forecast data yet", async () => {
    withDemandForecasting()
    serveKpi([])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    expect(await screen.findByText("Recopilando historial…")).toBeInTheDocument()
  })

  it("renders a forecast-vs-actual overlay chart when the backend has forecast rows", async () => {
    withDemandForecasting()
    serveKpi([
      kgRow(MARRAQUETA, { date: "2026-01-01", predictedGrams: 10_000, actualGrams: 9_000 }),
      kgRow(MARRAQUETA, { date: "2026-01-02", predictedGrams: 11_000 }),
    ])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    expect(await screen.findByText("Pronóstico vs. real (kg)")).toBeInTheDocument()
    expect(screen.queryByText("Recopilando historial…")).not.toBeInTheDocument()
  })

  it("keeps kg and units apart: the kg chart excludes unit-sold products and units get their own chart", async () => {
    withDemandForecasting()
    serveKpi([
      kgRow(MARRAQUETA),
      kgRow(HALLULLA, {
        predictedGrams: null,
        predictedUnits: 5,
        modelVersion: "baseline-trailing-mean",
      }),
    ])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    const kg = await screen.findByTestId("forecast-section-WEIGHT")
    const units = await screen.findByTestId("forecast-section-UNIT")
    expect(within(kg).getByText("Pronóstico vs. real (kg)")).toBeInTheDocument()
    expect(within(units).getByText("Pronóstico vs. real (unidades)")).toBeInTheDocument()
    expect(within(kg).queryByText(/Hallulla/)).not.toBeInTheDocument()
    expect(within(units).queryByText(/Marraqueta/)).not.toBeInTheDocument()
  })

  it("never lists Almacén: it is not forecast", async () => {
    withDemandForecasting()
    serveKpi([
      kgRow(MARRAQUETA),
      kgRow(
        { productId: "p-cola", productName: "Cola 500", productType: "Almacén" },
        { predictedGrams: null, predictedUnits: 5, modelVersion: "baseline-trailing-mean" }
      ),
    ])

    renderApp("/analytics/forecast")
    const user = await loginAsGeneralManager()

    await screen.findByTestId("forecast-section-WEIGHT")
    expect(screen.queryByTestId("forecast-section-UNIT")).not.toBeInTheDocument()
    expect(screen.queryByText(/Cola 500/)).not.toBeInTheDocument()
    await user.click(screen.getByRole("combobox", { name: "Tipo de producto" }))
    expect(screen.queryByRole("option", { name: "Almacén" })).not.toBeInTheDocument()
  })

  it("labels each product's series with the estimator that produced it", async () => {
    withDemandForecasting()
    serveKpi([
      kgRow(MARRAQUETA, { modelVersion: "lgbm-favorita" }),
      kgRow(TORTA, { predictedGrams: 2_000, modelVersion: "baseline-trailing-mean" }),
    ])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    // Different estimators: no group claim, each chart says its own.
    expect(await screen.findByText("Estimador: varía por producto")).toBeInTheDocument()
    expect(within(screen.getByTestId("forecast-bars-Marraqueta")).getByText("Modelo (LightGBM)")).toBeInTheDocument()
    expect(within(screen.getByTestId("forecast-bars-Torta")).getByText("Línea base")).toBeInTheDocument()
  })

  it("shows last-4-weeks WAPE with the days used, per unit", async () => {
    withDemandForecasting()
    // 7 past days, predicted 90 vs actual 100 kg-grams -> WAPE 10 %.
    serveKpi(pastDays(MARRAQUETA, 90_000, 100_000))

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    const block = await screen.findByTestId("accuracy-block")
    expect(within(block).getAllByText(/^10\s?%$/).length).toBeGreaterThan(0)
    expect(within(block).getByText(/^7 de 28 días con pronóstico y ventas/)).toBeInTheDocument()
    // "WAPE" lives only in the explanation behind the toggle.
    expect(within(block).queryByText(/WAPE/)).not.toBeInTheDocument()
  })

  it("hides the accuracy figure below the minimum number of scored days", async () => {
    withDemandForecasting()
    serveKpi([kgRow(MARRAQUETA, { date: dayString(1), predictedGrams: 90_000, actualGrams: 100_000 })])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    expect(await screen.findByText(/Aún no hay suficientes días con pronóstico y ventas \(mínimo 7\)/)).toBeInTheDocument()
    expect(screen.queryByTestId("accuracy-block")?.textContent).not.toMatch(/\d\s?%/)
  })

  it("discloses past days with sales but no forecast", async () => {
    withDemandForecasting()
    serveKpi([
      ...pastDays(MARRAQUETA, 90_000, 100_000),
      kgRow(MARRAQUETA, { date: dayString(9), predictedGrams: null, actualGrams: 50_000, modelVersion: null }),
    ])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    expect(await screen.findByText(/1 día con ventas sin pronóstico, no incluidos en la cifra/)).toBeInTheDocument()
  })

  it("never renders an empty or misleading estimator label for a missing or unrecognised version", async () => {
    withDemandForecasting()
    serveKpi([
      kgRow(MARRAQUETA, { modelVersion: null }),
      kgRow(TORTA, { predictedGrams: 2_000, modelVersion: "mystery-9" }),
    ])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    expect(await screen.findByText("Estimador: Estimador desconocido en todos")).toBeInTheDocument()
    expect(screen.queryByText(/Línea base/)).not.toBeInTheDocument()
  })

  it("requests reporting-day-aligned windows: 28 whole days of history for accuracy, 7 back + 7 ahead for the overlay", async () => {
    withDemandForecasting()
    // 02:30Z is still the 28th in Santiago -- a browser-local or UTC "today" would be the 29th.
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.setSystemTime(Date.parse("2026-07-29T02:30:00Z"))
    const requests: { from: string; to: string; productType: string | null }[] = []
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, ({ request }) => {
        const params = new URL(request.url).searchParams
        requests.push({
          from: new Date(Number(params.get("from"))).toISOString(),
          to: new Date(Number(params.get("to"))).toISOString(),
          productType: params.get("productType"),
        })
        return HttpResponse.json(pastDays(MARRAQUETA, 90_000, 100_000))
      })
    )

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()
    await screen.findByTestId("accuracy-block")

    expect(requests).toContainEqual({ from: "2026-07-22T04:00:00.000Z", to: "2026-08-04T04:00:00.000Z", productType: null })
    expect(requests).toContainEqual({ from: "2026-06-30T04:00:00.000Z", to: "2026-07-28T03:59:59.999Z", productType: null })
  })

  it("scores accuracy for the selected product only and keeps every product in the dropdown", async () => {
    withDemandForecasting()
    const rows = [...pastDays(MARRAQUETA, 90_000, 100_000), ...pastDays(TORTA, 50_000, 100_000)]
    const seen: (string | null)[] = []
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, ({ request }) => {
        seen.push(new URL(request.url).searchParams.get("productType"))
        return HttpResponse.json(rows)
      })
    )

    renderApp("/analytics/forecast")
    const user = await loginAsGeneralManager()

    const block = await screen.findByTestId("accuracy-block")
    expect(await within(block).findByText(/^30\s?%$/)).toBeInTheDocument()
    // Per-product rows, largest deviation first.
    const productRows = within(block).getAllByRole("listitem")
    expect(productRows[0]).toHaveTextContent(/Torta.*50\s?%/)
    expect(productRows[1]).toHaveTextContent(/Marraqueta.*10\s?%/)

    await user.click(screen.getByRole("combobox", { name: "Producto" }))
    await user.click(await screen.findByRole("option", { name: "Marraqueta" }))

    const filtered = await screen.findByTestId("accuracy-block")
    await waitFor(() => expect(within(filtered).getByText(/^10\s?%$/)).toBeInTheDocument())
    expect(within(filtered).queryByText(/50\s?%/)).not.toBeInTheDocument()
    expect(within(filtered).queryByRole("listitem")).not.toBeInTheDocument()

    // The filter is applied client-side: nothing is requested per type, and the other product is
    // still selectable.
    expect(seen.every((productType) => productType === null)).toBe(true)
    await user.click(screen.getByRole("combobox", { name: "Producto" }))
    expect(await screen.findByRole("option", { name: "Torta" })).toBeInTheDocument()
  })

  it("filters by type: the product list narrows to that type's products", async () => {
    withDemandForecasting()
    serveKpi([
      ...pastDays(MARRAQUETA, 90_000, 100_000),
      ...pastDays(HALLULLA, 90_000, 100_000),
      ...pastDays(TORTA, 50_000, 100_000),
    ])

    renderApp("/analytics/forecast")
    const user = await loginAsGeneralManager()
    await screen.findByTestId("accuracy-block")

    await user.click(screen.getByRole("combobox", { name: "Tipo de producto" }))
    await user.click(await screen.findByRole("option", { name: "Pastelería" }))

    await waitFor(() => expect(within(screen.getByTestId("accuracy-block")).getByText(/^50\s?%$/)).toBeInTheDocument())
    await user.click(screen.getByRole("combobox", { name: "Producto" }))
    expect(await screen.findByRole("option", { name: "Torta" })).toBeInTheDocument()
    expect(screen.queryByRole("option", { name: "Marraqueta" })).not.toBeInTheDocument()
  })

  it("says the accuracy could not be computed when its request fails, instead of 'not enough days'", async () => {
    withDemandForecasting()
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, ({ request }) => {
        const from = Number(new URL(request.url).searchParams.get("from"))
        // The accuracy window starts ~28 days back; the overlay ~6.
        return from < Date.now() - 20 * 86_400_000
          ? HttpResponse.json({ code: "BOOM" }, { status: 500 })
          : HttpResponse.json(pastDays(MARRAQUETA, 90_000, 100_000))
      })
    )

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    expect(await screen.findByText("No se pudo calcular la precisión.", {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByText(/Aún no hay suficientes días/)).not.toBeInTheDocument()
  })

  it("shows a load error with retry -- not the cold-start state -- when the forecast request fails", async () => {
    withDemandForecasting()
    let fail = true
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, () =>
        fail ? HttpResponse.json({ code: "BOOM" }, { status: 500 }) : HttpResponse.json(pastDays("PAN", 90_000, 100_000))
      )
    )

    renderApp("/analytics/forecast")
    const user = await loginAsGeneralManager()

    expect(await screen.findByText("No se pudo cargar el pronóstico.", {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByText("Recopilando historial…")).not.toBeInTheDocument()

    fail = false
    await user.click(screen.getByRole("button", { name: "Reintentar" }))
    expect(await screen.findByText("Pronóstico vs. real (kg)")).toBeInTheDocument()
  })

  it("says so when the selected type has sales but no forecast, instead of rendering blank", async () => {
    withDemandForecasting()
    const rows: ForecastVsActualDto[] = [
      ...pastDays(MARRAQUETA, 90_000, 100_000),
      kgRow(TORTA, { date: dayString(1), predictedGrams: null, actualGrams: 0, actualUnits: 4, modelVersion: null }),
    ]
    server.use(mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, () => HttpResponse.json(rows)))

    renderApp("/analytics/forecast")
    const user = await loginAsGeneralManager()
    await screen.findByText("Pronóstico vs. real (kg)")

    await user.click(screen.getByRole("combobox", { name: "Tipo de producto" }))
    await user.click(await screen.findByRole("option", { name: "Pastelería" }))

    expect(await screen.findByText("No hay pronóstico para la selección actual.")).toBeInTheDocument()
  })
})

describe("Producción sugerida para mañana", () => {
  afterEach(() => vi.useRealTimers())

  const GENERATED_AT = Date.parse("2026-07-29T12:00:00Z")

  const f1 = (product: typeof MARRAQUETA, over: Partial<ForecastDto>): ForecastDto => ({
    id: `f-${product.productId}`,
    branchId: FIXTURE.branch.id,
    ...product,
    forecastDate: "2026-07-30",
    predictedGrams: null,
    predictedUnits: null,
    modelVersion: "baseline-trailing-mean",
    generatedAt: GENERATED_AT,
    ...over,
  })

  const serve = (forecasts: ForecastDto[]) => {
    withDemandForecasting()
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, () => HttpResponse.json([])),
      mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json(forecasts))
    )
  }

  it("lists tomorrow's quantity per product with its unit; baseline rows never say Modelo", async () => {
    serve([
      f1(MARRAQUETA, { predictedGrams: 12_500 }),
      f1(HALLULLA, { predictedUnits: 40 }),
    ])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    const table = await screen.findByTestId("suggested-production")
    const kg = within(await within(table).findByTestId("suggested-WEIGHT"))
    expect(kg.getByText("Marraqueta")).toBeInTheDocument()
    expect(kg.getByText("12,5 kg")).toBeInTheDocument()
    expect(within(table).getByTestId("suggested-UNIT")).toHaveTextContent(/Hallulla.*40 u\./)
    // One estimator for every row: said once in the header, never as "Modelo".
    expect(within(table).getByText("Estimador: Línea base en todos")).toBeInTheDocument()
    expect(within(table).queryByText(/Modelo/)).not.toBeInTheDocument()
  })

  it("labels a trained-model row Modelo", async () => {
    serve([f1(MARRAQUETA, { predictedGrams: 12_500, modelVersion: "lgbm-favorita" })])

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    const table = await screen.findByTestId("suggested-production")
    expect(await within(table).findByText("Estimador: Modelo (LightGBM) en todos")).toBeInTheDocument()
  })

  it("asks for exactly tomorrow in the reporting zone", async () => {
    // 02:30Z on the 29th is still the evening of the 28th in Santiago, so tomorrow is the 29th.
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.setSystemTime(Date.parse("2026-07-29T02:30:00Z"))
    const calls: { keys: string; from: string; to: string }[] = []
    serve([])
    server.use(
      mswHttp.get(`${API_BASE_URL}/forecasts`, ({ request }) => {
        const params = new URL(request.url).searchParams
        // Record every /forecasts call; other components on the page (e.g. a freshness check
        // for today) may add calls for other days or shapes, which this test must not mind.
        calls.push({
          keys: [...params.keys()].sort().join(","),
          from: reportingDate(Number(params.get("from"))),
          to: reportingDate(Number(params.get("to"))),
        })
        return HttpResponse.json([])
      })
    )

    renderApp("/analytics/forecast")
    await loginAsGeneralManager()

    expect(await screen.findByText("Aún no hay pronóstico para mañana.")).toBeInTheDocument()
    // Tomorrow's call exists, with exactly the expected shape: that one day, no other filter.
    await vi.waitFor(() =>
      expect(calls).toContainEqual({ keys: "branchId,from,to", from: "2026-07-29", to: "2026-07-29" })
    )
    // And nothing asks for a day after tomorrow.
    expect(calls.every((call) => call.to <= "2026-07-29")).toBe(true)
  })

  it("follows the product filter", async () => {
    serve([f1(MARRAQUETA, { predictedGrams: 12_500 }), f1(TORTA, { predictedUnits: 3 })])
    server.use(
      mswHttp.get(`${API_BASE_URL}/analytics/forecast-vs-actual`, () =>
        HttpResponse.json([
          {
            date: reportingDate(Date.now()),
            ...MARRAQUETA,
            predictedGrams: 1,
            predictedUnits: null,
            actualGrams: 0,
            actualUnits: 0,
            modelVersion: "baseline-swlw",
          },
          {
            date: reportingDate(Date.now()),
            ...TORTA,
            predictedGrams: null,
            predictedUnits: 3,
            actualGrams: 0,
            actualUnits: 0,
            modelVersion: "baseline-swlw",
          },
        ] satisfies ForecastVsActualDto[])
      )
    )

    renderApp("/analytics/forecast")
    const user = await loginAsGeneralManager()
    const table = await screen.findByTestId("suggested-production")
    await within(table).findByText("Torta")

    await user.click(screen.getByRole("combobox", { name: "Producto" }))
    await user.click(await screen.findByRole("option", { name: "Torta" }))

    expect(within(table).queryByText("Marraqueta")).not.toBeInTheDocument()
    expect(within(table).getByText("Torta")).toBeInTheDocument()
  })
})

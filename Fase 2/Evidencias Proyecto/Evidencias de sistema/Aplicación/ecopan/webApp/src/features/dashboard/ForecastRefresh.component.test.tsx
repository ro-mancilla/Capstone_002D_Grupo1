import { beforeEach, describe, expect, it, vi } from "vitest"
import { act, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { toast } from "sonner"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import { storeSession } from "@/api/auth"
import { FORECASTS_KEY_PREFIX, forecastVsActualQuery } from "@/api/queries/forecast"
import type { ForecastDto, Session } from "@/api/types"
import { AuthProvider } from "@/features/auth/AuthContext"
import { t } from "@/lib/i18n"
import { ForecastRefresh } from "./ForecastRefresh"

vi.mock("sonner", async (importOriginal) => {
  const original = await importOriginal<typeof import("sonner")>()
  return { ...original, toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }) }
})

/** Component-level: polling, give-up, branch switch, failure and pending states, with the poll
 * and give-up timers shortened so nothing waits in real time. */

const strings = t.dashboard.forecast
const BRANCH_A = "aaaaaaaa-0000-4000-8000-00000000000a"
const BRANCH_B = "bbbbbbbb-0000-4000-8000-00000000000b"

const session: Session = {
  userId: FIXTURE.genMgr.id,
  tenantId: FIXTURE.tenant.id,
  tenantSlug: FIXTURE.tenant.slug,
  branchId: null,
  role: "GENERAL_MANAGER",
  displayName: FIXTURE.genMgr.displayName,
  tenantName: FIXTURE.tenant.name,
  currencyCode: "CLP",
  enabledModules: ["DEMAND_FORECASTING"],
  branches: [],
}

const row = (branchId: string, generatedAt: number): ForecastDto => ({
  id: `f-${branchId}`,
  branchId,
  productType: "PAN",
  forecastDate: "2026-07-21",
  predictedGrams: 1,
  predictedUnits: null,
  modelVersion: "lgbm",
  generatedAt,
})

function setup(branchId: string, opts: { pollMs?: number; giveUpMs?: number } = {}) {
  storeSession(session)
  const queryClient = new QueryClient()
  const ui = (id: string) => (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ForecastRefresh key={id} branchId={id} pollMs={opts.pollMs ?? 20} giveUpMs={opts.giveUpMs ?? 5_000} />
      </AuthProvider>
    </QueryClientProvider>
  )
  const utils = render(ui(branchId))
  return { queryClient, switchTo: (id: string) => utils.rerender(ui(id)) }
}

const button = () => screen.findByRole("button", { name: /^(Actualizar|Generar) pronóstico$/ })

beforeEach(() => {
  vi.mocked(toast.error).mockClear()
  vi.mocked(toast.success).mockClear()
})

describe("ForecastRefresh (component)", () => {
  it("polls after the request, clears the wait when a newer generatedAt lands, and invalidates K8", async () => {
    let generatedAt = 1_000
    server.use(
      mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json([row(BRANCH_A, generatedAt)])),
      mswHttp.post(`${API_BASE_URL}/forecasts/refresh`, () => new HttpResponse(null, { status: 202 }))
    )
    const { queryClient } = setup(BRANCH_A)
    const k8Key = forecastVsActualQuery({ branchId: BRANCH_A, from: 1, to: 2 }).queryKey
    queryClient.setQueryData(k8Key, [])
    const otherF1Key = [...FORECASTS_KEY_PREFIX, "suggested-production", BRANCH_A] as const
    queryClient.setQueryData(otherF1Key, [])

    const user = userEvent.setup()
    await user.click(await button())
    expect(await screen.findByText(strings.refreshing)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: /^(Actualizar|Generar) pronóstico$/ })).toBeDisabled()

    generatedAt = 2_000 // the worker finished
    await waitFor(() => expect(screen.queryByText(strings.refreshing)).not.toBeInTheDocument())
    await waitFor(() => expect(queryClient.getQueryState(k8Key)?.isInvalidated).toBe(true))
    await waitFor(() => expect(queryClient.getQueryState(otherF1Key)?.isInvalidated).toBe(true))
    expect(screen.getByRole("button", { name: /^(Actualizar|Generar) pronóstico$/ })).toBeEnabled()
  })

  it("gives up after the timeout with a message instead of spinning forever", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json([row(BRANCH_A, 1_000)])),
      mswHttp.post(`${API_BASE_URL}/forecasts/refresh`, () => new HttpResponse(null, { status: 202 }))
    )
    setup(BRANCH_A, { giveUpMs: 150 })

    await userEvent.setup().click(await button())

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(strings.refreshTimedOut))
    expect(screen.queryByText(strings.refreshing)).not.toBeInTheDocument()
    expect(screen.getByRole("button", { name: /^(Actualizar|Generar) pronóstico$/ })).toBeEnabled()
  })

  it("discards the wait and the last-seen timestamp when the branch changes", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}/forecasts`, ({ request }) => {
        const branch = new URL(request.url).searchParams.get("branchId")!
        return HttpResponse.json([row(branch, branch === BRANCH_A ? 9_000_000_000_000 : 1_000)])
      }),
      mswHttp.post(`${API_BASE_URL}/forecasts/refresh`, () => new HttpResponse(null, { status: 202 }))
    )
    const { switchTo, queryClient } = setup(BRANCH_A)
    const k8Key = forecastVsActualQuery({ branchId: BRANCH_B, from: 1, to: 2 }).queryKey
    queryClient.setQueryData(k8Key, [])

    await userEvent.setup().click(await button())
    expect(await screen.findByText(strings.refreshing)).toBeInTheDocument()

    await act(async () => switchTo(BRANCH_B))

    await waitFor(() => expect(screen.queryByText(strings.refreshing)).not.toBeInTheDocument())
    // Branch B's lower generatedAt must not be read as "newer than A's" nor as a change.
    expect(queryClient.getQueryState(k8Key)?.isInvalidated).toBe(false)
    expect(screen.getByRole("button", { name: /^(Actualizar|Generar) pronóstico$/ })).toBeEnabled()
  })

  it("toasts refreshFailed and stays idle when F2 errors", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json([])),
      mswHttp.post(`${API_BASE_URL}/forecasts/refresh`, () => HttpResponse.json({ code: "X" }, { status: 500 }))
    )
    setup(BRANCH_A)

    await userEvent.setup().click(await button())

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(strings.refreshFailed))
    expect(screen.queryByText(strings.refreshing)).not.toBeInTheDocument()
    expect(screen.getByRole("button", { name: /^(Actualizar|Generar) pronóstico$/ })).toBeEnabled()
  })

  it("disables the button while the request is in flight", async () => {
    let release!: () => void
    const gate = new Promise<void>((resolve) => (release = resolve))
    server.use(
      mswHttp.get(`${API_BASE_URL}/forecasts`, () => HttpResponse.json([])),
      mswHttp.post(`${API_BASE_URL}/forecasts/refresh`, async () => {
        await gate
        return new HttpResponse(null, { status: 202 })
      })
    )
    setup(BRANCH_A)

    await userEvent.setup().click(await button())
    await waitFor(() => expect(screen.getByRole("button", { name: /^(Actualizar|Generar) pronóstico$/ })).toBeDisabled())
    release()
    expect(await screen.findByText(strings.refreshing)).toBeInTheDocument()
  })
})

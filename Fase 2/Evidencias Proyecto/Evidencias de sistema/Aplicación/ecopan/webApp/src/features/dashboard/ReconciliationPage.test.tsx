import { describe, expect, it, vi } from "vitest"
import { fireEvent, screen, waitFor, within } from "@testing-library/react"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import type { ReconciliationRowDto } from "@/api/types"
import { managerSession } from "@/test/fixtures"
import { renderRouterAt } from "@/test/router-utils"

const ROWS: ReconciliationRowDto[] = [
  {
    ingredientId: "i-1",
    name: "Harina",
    openingGrams: 9_000,
    purchasedGrams: 4_300,
    decrementedGrams: -2_000,
    adjustedGrams: -200,
    closingGrams: 11_100,
    batchCount: 2,
  },
  {
    ingredientId: "i-2",
    name: "Sal",
    openingGrams: 500,
    purchasedGrams: 0,
    decrementedGrams: -120,
    adjustedGrams: 0,
    closingGrams: 380,
    batchCount: 1,
  },
]

const auth = vi.hoisted(() => ({ session: null as unknown }))

// The page reads the session from `useAuth`; the router (real route tree, so the route guard runs)
// gets the same session.
vi.mock("@/features/auth/AuthContext", () => ({
  useAuth: () => ({
    session: auth.session,
    login: vi.fn(),
    logout: vi.fn(),
    clearSession: vi.fn(),
    syncFromTenantProfile: vi.fn(),
  }),
}))

function serve(handler: Parameters<typeof mswHttp.get>[1]) {
  server.use(mswHttp.get(`${API_BASE_URL}/inventory/reconciliation`, handler))
}

function renderPage() {
  const session = managerSession()
  auth.session = session
  return renderRouterAt("/analytics/reconciliation", session)
}

/** HU-ANA-0006: per ingredient, opening + purchases + decrements + adjustments = closing, with a
 * totals row and a CSV export. */
describe("ReconciliationPage", () => {
  it("renders the ledger per ingredient, to the gram, with a totals row", async () => {
    let requested: URL | undefined
    serve(({ request }) => {
      requested = new URL(request.url)
      return HttpResponse.json({ batchCount: 2, rows: ROWS })
    })

    renderPage()

    // The first render pulls in the whole lazy route tree; give it more than the 1 s default.
    await screen.findByRole("heading", { name: "Inventario reconciliado" }, { timeout: 5000 })
    const flourRow = await screen.findByRole("row", { name: /Harina/ })
    // Three fixed decimals and a real minus sign, so the commas line up.
    expect(within(flourRow).getByText("9,000")).toBeInTheDocument()
    expect(within(flourRow).getByText("4,300")).toBeInTheDocument()
    expect(within(flourRow).getByText("\u22122,000")).toBeInTheDocument()
    expect(within(flourRow).getByText("\u22120,200")).toBeInTheDocument()
    expect(within(flourRow).getByText("11,100")).toBeInTheDocument()

    const totalRow = screen.getByRole("row", { name: /^Total/ })
    expect(within(totalRow).getByText("11,480")).toBeInTheDocument()
    // Flour used 2 batches and salt 1, but both batches decremented both: the period has 2 batches,
    // not the 3 the per-ingredient counts add up to.
    expect(within(totalRow).getAllByRole("cell").at(-1)).toHaveTextContent(/^2$/)

    expect(requested?.searchParams.get("branchId")).toBe(FIXTURE.branch.id)
    expect(requested?.searchParams.get("from")).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(requested?.searchParams.get("to")).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(screen.getByRole("button", { name: "Descargar CSV" })).toBeEnabled()
  })

  it("labels an ingredient with no name instead of showing its id", async () => {
    const orphanId = "6f1c0c0e-0000-4000-8000-00000000abcd"
    serve(() => HttpResponse.json({ batchCount: 2, rows: [{ ...ROWS[0], ingredientId: orphanId, name: "" }] }))

    renderPage()

    expect(await screen.findByText("Insumo sin nombre", {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.queryByText(orphanId)).not.toBeInTheDocument()
    expect(document.body.textContent).not.toContain(orphanId)
  })

  it("shows an empty state when the ledger has no movements", async () => {
    serve(() => HttpResponse.json({ batchCount: 0, rows: [] }))

    renderPage()

    expect(
      await screen.findByText("No hay movimientos de insumos hasta la fecha final de este rango.")
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Descargar CSV" })).toBeDisabled()
  })

  it("shows a failure with a retry, not the empty state, when the read fails", async () => {
    serve(() => HttpResponse.json({ code: "BOOM" }, { status: 500 }))

    renderPage()

    expect(await screen.findByRole("button", { name: "Reintentar" }, { timeout: 4000 })).toBeInTheDocument()
    expect(
      screen.queryByText("No hay movimientos de insumos hasta la fecha final de este rango.")
    ).not.toBeInTheDocument()
  })

  it("tells a cleared date input apart from an inverted range, and does not query for either", async () => {
    let calls = 0
    serve(() => {
      calls += 1
      return HttpResponse.json({ batchCount: 2, rows: ROWS })
    })

    renderPage()
    await screen.findByRole("row", { name: /Harina/ })
    const callsAfterLoad = calls

    fireEvent.click(screen.getByRole("button", { name: "Fechas" }))
    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "" } })
    expect(await screen.findByText("Indica la fecha inicial y la final para ver el informe.")).toBeInTheDocument()
    expect(screen.queryByText("La fecha inicial no puede ser posterior a la final.")).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2999-01-01" } })
    expect(await screen.findByText("La fecha inicial no puede ser posterior a la final.")).toBeInTheDocument()
    expect(screen.queryByText("Indica la fecha inicial y la final para ver el informe.")).not.toBeInTheDocument()

    await waitFor(() => expect(screen.queryByRole("row", { name: /Harina/ })).not.toBeInTheDocument())
    expect(calls).toBe(callsAfterLoad)
  })

  it("refuses a range longer than a year before asking the server", async () => {
    serve(() => HttpResponse.json({ batchCount: 2, rows: ROWS }))

    renderPage()
    await screen.findByRole("row", { name: /Harina/ })

    fireEvent.click(screen.getByRole("button", { name: "Fechas" }))
    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2000-01-01" } })
    expect(await screen.findByText("El rango no puede superar los 366 días.")).toBeInTheDocument()
  })

  it("marks rows that need a look, with the reason, and leaves the others alone", async () => {
    serve(() =>
      HttpResponse.json({
        batchCount: 2,
        rows: [
          { ...ROWS[0], purchasedGrams: 200_000, adjustedGrams: -12_500, closingGrams: 181_548 },
          { ...ROWS[1], name: "Levadura", closingGrams: -200 },
          { ...ROWS[1], ingredientId: "i-3", name: "Agua" },
        ],
      })
    )

    renderPage()

    const flour = await screen.findByRole("row", { name: /Harina/, }, { timeout: 5000 })
    expect(within(flour).getByText("Revisar · Ajuste de \u221212,5 kg (merma)")).toBeInTheDocument()
    const yeast = screen.getByRole("row", { name: /Levadura/ })
    expect(within(yeast).getByText("Revisar · Cierre negativo")).toBeInTheDocument()
    expect(within(screen.getByRole("row", { name: /Agua/ })).queryByText(/Revisar/)).not.toBeInTheDocument()
  })

  it("shows a bare 0 for empty columns", async () => {
    serve(() => HttpResponse.json({ batchCount: 1, rows: [ROWS[1]] }))

    renderPage()

    const salt = await screen.findByRole("row", { name: /Sal/ }, { timeout: 5000 })
    expect(within(salt).getAllByText("0").length).toBeGreaterThanOrEqual(2)
  })

  it("warns that the period can still change only when the range does not end today", async () => {
    serve(() => HttpResponse.json({ batchCount: 2, rows: ROWS }))

    renderPage()
    await screen.findByRole("row", { name: /Harina/ }, { timeout: 5000 })
    expect(screen.queryByText(/Este período puede cambiar/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "Mes anterior" }))
    expect(await screen.findByText(/Este período puede cambiar si se sincroniza/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "Este mes" }))
    await waitFor(() => expect(screen.queryByText(/Este período puede cambiar/)).not.toBeInTheDocument())
  })

  it("asks for the quick range picked and says what it covers", async () => {
    const requests: URL[] = []
    serve(({ request }) => {
      requests.push(new URL(request.url))
      return HttpResponse.json({ batchCount: 2, rows: ROWS })
    })

    renderPage()
    await screen.findByRole("row", { name: /Harina/ }, { timeout: 5000 })
    fireEvent.click(screen.getByRole("button", { name: "Mes anterior" }))

    await waitFor(() => expect(requests.at(-1)!.searchParams.get("from")).toMatch(/-01$/))
    expect(screen.getByText(/ al /)).toBeInTheDocument()
    expect(screen.getByText("El archivo viene en gramos exactos, para Excel.")).toBeInTheDocument()
  })
})

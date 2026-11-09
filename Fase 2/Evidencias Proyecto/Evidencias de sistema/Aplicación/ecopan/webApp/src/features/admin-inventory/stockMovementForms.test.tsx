import { describe, expect, it } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type { CreateStockMovementRequest, IngredientDto } from "@/api/types"
import { PurchaseEntryForm } from "./PurchaseEntryForm"
import { AdjustmentForm } from "./AdjustmentForm"

const BRANCH_ID = "d7026124-76e6-4907-aaee-d497a08f908e"
const FLOUR: IngredientDto = {
  id: "f1e6de63-667a-4b5d-93a7-1ab1e3533ec1",
  name: "Harina",
  lowStockThresholdGrams: 5000,
  active: true,
}

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient()
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

/** Plan 07 §10's mandated test surface 2: kg entry -> grams wire conversion for stock
 * movements. Both purchase entry and manual signed adjustment append the same
 * `CreateStockMovementRequest` shape (I5); the wire payload must carry an integer gram value,
 * never the raw kg string/decimal. */
describe("Stock movement forms (plan 07 §10 mandated surface 2)", () => {
  it("purchase entry converts a kg amount to an integer deltaGrams on the wire", async () => {
    let capturedBody: CreateStockMovementRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/movements`, async ({ request }) => {
        capturedBody = (await request.json()) as CreateStockMovementRequest
        return HttpResponse.json({ serverSeq: 1 }, { status: 201 })
      })
    )

    renderWithClient(<PurchaseEntryForm branchId={BRANCH_ID} ingredients={[FLOUR]} />)
    const user = userEvent.setup()

    await user.click(screen.getByLabelText("Insumo"))
    await user.click(await screen.findByRole("option", { name: "Harina" }))
    await user.type(screen.getByLabelText("Cantidad (kg)"), "2.5")
    await user.type(screen.getByLabelText("Nota"), "Factura #1")
    await user.click(screen.getByRole("button", { name: "Registrar ingreso" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody!.deltaGrams).toBe(2500)
    expect(Number.isInteger(capturedBody!.deltaGrams)).toBe(true)
    expect(capturedBody!.kind).toBe("PURCHASE")
    expect(capturedBody!.branchId).toBe(BRANCH_ID)
    expect(capturedBody!.ingredientId).toBe(FLOUR.id)
    expect(typeof capturedBody!.occurredAt).toBe("number")
  })

  it("manual adjustment carries a negative kg amount through as a negative integer deltaGrams", async () => {
    let capturedBody: CreateStockMovementRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/movements`, async ({ request }) => {
        capturedBody = (await request.json()) as CreateStockMovementRequest
        return HttpResponse.json({ serverSeq: 2 }, { status: 201 })
      })
    )

    renderWithClient(<AdjustmentForm branchId={BRANCH_ID} ingredients={[FLOUR]} />)
    const user = userEvent.setup()

    await user.click(screen.getByLabelText("Insumo"))
    await user.click(await screen.findByRole("option", { name: "Harina" }))
    // `userEvent.type`'s char-by-char typing hits a jsdom quirk on `<input type="number">`
    // where an intermediate "-" resets the value to empty; `fireEvent.change` sets the whole
    // string in one native change event instead, matching how a real browser's number input
    // reports "-3.2" once the user finishes typing it.
    fireEvent.change(screen.getByLabelText("Diferencia (kg, puede ser negativa)"), {
      target: { value: "-3.2" },
    })
    await user.type(screen.getByLabelText("Motivo"), "Merma por conteo físico")
    await user.click(screen.getByRole("button", { name: "Registrar ajuste" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody!.deltaGrams).toBe(-3200)
    expect(Number.isInteger(capturedBody!.deltaGrams)).toBe(true)
    expect(capturedBody!.kind).toBe("MANUAL_ADJUSTMENT")
    expect(capturedBody!.note).toBe("Merma por conteo físico")
  })

  it("rejects a zero-kg adjustment client-side (never appends a no-op movement)", async () => {
    let requestCount = 0
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/movements`, () => {
        requestCount += 1
        return HttpResponse.json({ serverSeq: 3 }, { status: 201 })
      })
    )

    renderWithClient(<AdjustmentForm branchId={BRANCH_ID} ingredients={[FLOUR]} />)
    const user = userEvent.setup()

    await user.click(screen.getByLabelText("Insumo"))
    await user.click(await screen.findByRole("option", { name: "Harina" }))
    await user.type(screen.getByLabelText("Diferencia (kg, puede ser negativa)"), "0")
    await user.type(screen.getByLabelText("Motivo"), "sin cambio")
    await user.click(screen.getByRole("button", { name: "Registrar ajuste" }))

    await screen.findByText("La diferencia no puede ser cero.")
    expect(requestCount).toBe(0)
  })
})

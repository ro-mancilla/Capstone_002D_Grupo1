import { describe, expect, it } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type { CreateProductStockMovementRequest } from "@/api/types"
import { ProductAdjustmentForm, ProductPurchaseForm } from "./ProductStockForms"

const BRANCH_ID = "d7026124-76e6-4907-aaee-d497a08f908e"
const COLA_ID = "3c1c7f0e-5a54-4d0a-9d2a-6a4d6f1b2c3d"

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient()
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

/** Almacén product stock forms: the ingredient forms' contract, in whole units, for the product they sit in. */
describe("Product stock forms", () => {
  it("purchase entry posts a positive integer deltaUnits with kind PURCHASE", async () => {
    let capturedBody: CreateProductStockMovementRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/product-movements`, async ({ request }) => {
        capturedBody = (await request.json()) as CreateProductStockMovementRequest
        return HttpResponse.json({ ...capturedBody, note: null, createdBy: null }, { status: 201 })
      })
    )

    renderWithClient(<ProductPurchaseForm branchId={BRANCH_ID} productId={COLA_ID} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText("Cantidad (unidades)"), "24")
    await user.type(screen.getByLabelText("Nota"), "Factura #7")
    await user.click(screen.getByRole("button", { name: "Registrar ingreso" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody!.deltaUnits).toBe(24)
    expect(capturedBody!.kind).toBe("PURCHASE")
    expect(capturedBody!.branchId).toBe(BRANCH_ID)
    expect(capturedBody!.productId).toBe(COLA_ID)
    expect(capturedBody!.note).toBe("Factura #7")
  })

  it("manual adjustment carries a negative unit count through as-is", async () => {
    let capturedBody: CreateProductStockMovementRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/product-movements`, async ({ request }) => {
        capturedBody = (await request.json()) as CreateProductStockMovementRequest
        return HttpResponse.json({ ...capturedBody, createdBy: null }, { status: 201 })
      })
    )

    renderWithClient(<ProductAdjustmentForm branchId={BRANCH_ID} productId={COLA_ID} />)
    const user = userEvent.setup()

    // Same jsdom number-input quirk as the ingredient adjustment test: set "-3" in one change.
    fireEvent.change(screen.getByLabelText("Diferencia (unidades, puede ser negativa)"), {
      target: { value: "-3" },
    })
    await user.type(screen.getByLabelText("Motivo"), "Unidades vencidas")
    await user.click(screen.getByRole("button", { name: "Registrar ajuste" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody!.deltaUnits).toBe(-3)
    expect(capturedBody!.kind).toBe("MANUAL_ADJUSTMENT")
    expect(capturedBody!.note).toBe("Unidades vencidas")
  })

  it("rejects a fractional unit count client-side -- bottles are whole", async () => {
    let requestCount = 0
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/product-movements`, () => {
        requestCount += 1
        return HttpResponse.json({}, { status: 201 })
      })
    )

    renderWithClient(<ProductPurchaseForm branchId={BRANCH_ID} productId={COLA_ID} />)
    const user = userEvent.setup()

    fireEvent.change(screen.getByLabelText("Cantidad (unidades)"), { target: { value: "2.5" } })
    await user.click(screen.getByRole("button", { name: "Registrar ingreso" }))

    await screen.findByText("La cantidad debe ser un número entero mayor a 0.")
    expect(requestCount).toBe(0)
  })
})

import { describe, expect, it } from "vitest"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type { IngredientDto, ProductDto, PutFormulaRequest } from "@/api/types"
import { Button } from "@/components/ui/button"
import { FormulaEditorDialog } from "./FormulaEditorDialog"

/**
 * Plan 07 §10's mandated test surface 1 (formula editor half), restated for the kg-per-kg-of-flour
 * entry that replaced percentages: kg/kg<->bp round-trips, flour locked at 10000 bp, the live
 * preview driven by a flour weight and derived with the exact `06-baker-mobile.md` §3 algorithm,
 * and `Σ previewGrams === totalDough`.
 */

const PRODUCT: ProductDto = {
  id: "p1",
  name: "Marraqueta",
  productType: "PAN",
  soldBy: "WEIGHT",
  pricePerKgMinor: 2500,
  priceMinor: null,
  barcode: null,
  imageUrl: null,
  active: true,
  serverSeq: 1,
}

const FLOUR: IngredientDto = { id: "flour", name: "Harina", lowStockThresholdGrams: null, active: true }
const WATER: IngredientDto = { id: "water", name: "Agua", lowStockThresholdGrams: null, active: true }
const SALT: IngredientDto = { id: "salt", name: "Sal", lowStockThresholdGrams: null, active: true }
const YEAST: IngredientDto = { id: "yeast", name: "Levadura", lowStockThresholdGrams: null, active: true }

function renderDialog(ingredients: IngredientDto[]) {
  const queryClient = new QueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <FormulaEditorDialog
        product={PRODUCT}
        ingredients={ingredients}
        trigger={<Button>Crear fórmula</Button>}
      />
    </QueryClientProvider>
  )
}

describe("FormulaEditorDialog (plan 07 §10 mandated surface 1)", () => {
  it("locks the flour row at 1 kg and converts entered kg-per-kg-of-flour to exact basis points on submit", async () => {
    let capturedBody: PutFormulaRequest | null = null
    server.use(
      mswHttp.put(`${API_BASE_URL}/catalog/products/${PRODUCT.id}/formula`, async ({ request }) => {
        capturedBody = (await request.json()) as PutFormulaRequest
        return HttpResponse.json({ id: "f1", productId: PRODUCT.id, lines: capturedBody.lines })
      })
    )

    renderDialog([FLOUR, WATER, SALT, YEAST])
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Crear fórmula" }))
    await screen.findByRole("heading", { name: /Marraqueta/ })

    // Flour row is present, not an editable quantity input.
    expect(within(screen.getByRole("table")).getByText("Harina")).toBeInTheDocument()
    expect(screen.getByText("Harina (base: 1 kg)")).toBeInTheDocument()
    expect(screen.queryByLabelText(/Kg por kilo de harina.*Harina/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/%/)).not.toBeInTheDocument()

    // Add water at 0.65 kg per kg of flour -- must land as exactly 6500 bp.
    await user.click(screen.getByRole("combobox", { name: /Agregar insumo/i }))
    await user.click(await screen.findByRole("option", { name: "Agua" }))
    await user.type(screen.getByLabelText(/Kg por kilo de harina.*Agua/i), "0.65")

    await user.click(screen.getByRole("button", { name: "Guardar" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    const flourLine = capturedBody!.lines.find((l) => l.ingredientId === "flour")
    const waterLine = capturedBody!.lines.find((l) => l.ingredientId === "water")
    expect(flourLine?.basisPoints).toBe(10000)
    expect(waterLine?.basisPoints).toBe(6500)
    expect(Number.isInteger(waterLine!.basisPoints)).toBe(true)
  })

  it("round-trips a 0.0025 kg/kg edge case (2.5 g per kg of flour) to exactly 25 bp", async () => {
    let capturedBody: PutFormulaRequest | null = null
    server.use(
      mswHttp.put(`${API_BASE_URL}/catalog/products/${PRODUCT.id}/formula`, async ({ request }) => {
        capturedBody = (await request.json()) as PutFormulaRequest
        return HttpResponse.json({ id: "f1", productId: PRODUCT.id, lines: capturedBody.lines })
      })
    )

    renderDialog([FLOUR, YEAST])
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Crear fórmula" }))
    await screen.findByRole("heading", { name: /Marraqueta/ })

    await user.click(screen.getByRole("combobox", { name: /Agregar insumo/i }))
    await user.click(await screen.findByRole("option", { name: "Levadura" }))
    await user.type(screen.getByLabelText(/Kg por kilo de harina.*Levadura/i), "0.0025")

    await user.click(screen.getByRole("button", { name: "Guardar" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    const yeastLine = capturedBody!.lines.find((l) => l.ingredientId === "yeast")
    expect(yeastLine?.basisPoints).toBe(25)
  })

  it("live preview scales every ingredient from the entered flour weight and sums to the total dough", async () => {
    renderDialog([FLOUR, WATER, SALT, YEAST])
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Crear fórmula" }))
    await screen.findByRole("heading", { name: /Marraqueta/ })

    await user.click(screen.getByRole("combobox", { name: /Agregar insumo/i }))
    await user.click(await screen.findByRole("option", { name: "Agua" }))
    await user.type(screen.getByLabelText(/Kg por kilo de harina.*Agua/i), "0.65")

    await user.click(screen.getByRole("combobox", { name: /Agregar insumo/i }))
    await user.click(await screen.findByRole("option", { name: "Sal" }))
    await user.type(screen.getByLabelText(/Kg por kilo de harina.*Sal/i), "0.02")

    await user.click(screen.getByRole("combobox", { name: /Agregar insumo/i }))
    await user.click(await screen.findByRole("option", { name: "Levadura" }))
    await user.type(screen.getByLabelText(/Kg por kilo de harina.*Levadura/i), "0.01")

    // Preview is driven by flour, defaulting to 10 kg: the same 06 §3 formula (10000 / 6500 /
    // 200 / 100 bp) now reads as plain multiples of the flour instead of shares of a mix.
    const flourInput = screen.getByLabelText(/Harina a usar/i)
    expect(flourInput).toHaveValue(10)

    const previewList = await screen.findByText("Harina", { selector: "span" })
    const container = previewList.closest("ul")!

    await waitFor(() => {
      expect(within(container).getByText("10,000 kg")).toBeInTheDocument()
    })
    expect(within(container).getByText("6,500 kg")).toBeInTheDocument() // water 6500g
    expect(within(container).getByText("0,200 kg")).toBeInTheDocument() // salt 200g
    expect(within(container).getByText("0,100 kg")).toBeInTheDocument() // yeast 100g

    // Total dough row === Σ lines === 10 kg x 16800 / 10000.
    expect(within(container).getByText("Masa total")).toBeInTheDocument()
    expect(within(container).getByText("16,800 kg")).toBeInTheDocument()

    // The worked example in reverse: 5.952 kg of flour is the flour share of its 10 kg dough, so
    // the preview lands back on the example's grams (water 3869, salt 119, yeast 60 -> 59 after
    // rounding the 9999 g total) via the same largest-remainder split the ledger uses.
    // jsdom drops a number input's value mid-way through typing "5.952" -- set it in one change.
    fireEvent.change(flourInput, { target: { value: "5.952" } })
    await waitFor(() => {
      expect(within(container).getByText("5,952 kg")).toBeInTheDocument()
    })
    expect(within(container).getByText("3,869 kg")).toBeInTheDocument()
    expect(within(container).getByText("0,119 kg")).toBeInTheDocument()
    expect(within(container).getByText("0,059 kg")).toBeInTheDocument()
  })
})

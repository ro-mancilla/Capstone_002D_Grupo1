import { describe, expect, it } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type {
  ConfirmImageRequest,
  CreateProductRequest,
  ImageUploadGrantRequest,
  ProductDto,
  UpdateProductRequest,
} from "@/api/types"
import { Button } from "@/components/ui/button"
import { ProductFormDialog } from "./ProductFormDialog"
import { t } from "@/lib/i18n"

/** Plan 07 §10's mandated test surface 1 (product form half): `soldBy`'s select swaps which
 * minor-unit field is populated, and decimal entry converts to an *integer* minor-units value on
 * the wire -- CLP is zero-decimal, so a plain "3500" entry must become `pricePerKgMinor: 3500`,
 * never `350000` (a stray *100) or a float. */
function renderDialog() {
  const queryClient = new QueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <ProductFormDialog currencyCode="CLP" trigger={<Button>Nuevo producto</Button>} />
    </QueryClientProvider>
  )
}

/** Opens the Tipo select and chooses one of the three fixed types. */
async function pickType(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(screen.getByLabelText("Tipo"))
  await user.click(await screen.findByRole("option", { name }))
}

describe("ProductFormDialog (plan 07 §10 mandated surface 1)", () => {
  it("submits pricePerKgMinor as an integer when soldBy=WEIGHT (CLP, zero-decimal)", async () => {
    let capturedBody: CreateProductRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        capturedBody = (await request.json()) as CreateProductRequest
        return HttpResponse.json({ ...capturedBody, active: true, serverSeq: 1 }, { status: 201 })
      })
    )

    renderDialog()
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })

    await user.type(screen.getByLabelText("Nombre"), "Marraqueta")
    await pickType(user, "Pan")
    // soldBy defaults to WEIGHT -- the price field should already read "Precio por kg".
    expect(screen.getByLabelText("Precio por kg")).toBeInTheDocument()
    await user.type(screen.getByLabelText("Precio por kg"), "3500")

    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody!.pricePerKgMinor).toBe(3500)
    expect(Number.isInteger(capturedBody!.pricePerKgMinor)).toBe(true)
    expect(capturedBody!.priceMinor).toBeNull()
    expect(capturedBody!.soldBy).toBe("WEIGHT")
  })

  it("swaps to priceMinor when soldBy is switched to UNIT, and never sends both fields", async () => {
    let capturedBody: CreateProductRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        capturedBody = (await request.json()) as CreateProductRequest
        return HttpResponse.json({ ...capturedBody, active: true, serverSeq: 1 }, { status: 201 })
      })
    )

    renderDialog()
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })

    await user.type(screen.getByLabelText("Nombre"), "Torta de chocolate")
    await pickType(user, "Pastelería")

    // Switch soldBy WEIGHT -> UNIT: the price field's label swaps, and now expects a unit price.
    await user.click(screen.getByLabelText("Se vende por"))
    await user.click(await screen.findByRole("option", { name: "Unidad" }))

    expect(screen.getByLabelText("Precio unitario")).toBeInTheDocument()
    await user.type(screen.getByLabelText("Precio unitario"), "1200")

    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody!.soldBy).toBe("UNIT")
    expect(capturedBody!.priceMinor).toBe(1200)
    expect(Number.isInteger(capturedBody!.priceMinor)).toBe(true)
    expect(capturedBody!.pricePerKgMinor).toBeNull()
  })

  it("uploads a chosen photo straight to storage after the product is created, then confirms the key", async () => {
    const productId = "7f4a2b1c-9d3e-4f5a-8b6c-1d2e3f4a5b6c"
    const grantedKey = `tenant/${productId}/photo.png`
    const uploadUrl = "http://localhost:10000/devstoreaccount1/product-images/" + grantedKey + "?sig=test"
    let createBody: CreateProductRequest | null = null
    let grantBody: ImageUploadGrantRequest | null = null
    let storagePut: { contentType: string | null; blobType: string | null; body: string } | null = null
    let confirmBody: ConfirmImageRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        createBody = (await request.json()) as CreateProductRequest
        return HttpResponse.json(
          { ...createBody, id: productId, imageUrl: null, active: true, serverSeq: 1 },
          { status: 201 }
        )
      }),
      mswHttp.post(`${API_BASE_URL}/catalog/products/${productId}/image/upload-url`, async ({ request }) => {
        grantBody = (await request.json()) as ImageUploadGrantRequest
        return HttpResponse.json({
          key: grantedKey,
          uploadUrl,
          expiresAt: Date.now() + 600_000,
          contentType: grantBody.contentType,
        })
      }),
      mswHttp.put(uploadUrl, async ({ request }) => {
        storagePut = {
          contentType: request.headers.get("content-type"),
          blobType: request.headers.get("x-ms-blob-type"),
          body: await request.text(),
        }
        return new HttpResponse(null, { status: 201 })
      }),
      mswHttp.put(`${API_BASE_URL}/catalog/products/${productId}/image`, async ({ request }) => {
        confirmBody = (await request.json()) as ConfirmImageRequest
        return HttpResponse.json({
          ...createBody,
          id: productId,
          imageUrl: uploadUrl.split("?")[0],
          active: true,
          serverSeq: 2,
        })
      })
    )

    renderDialog()
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    await user.type(screen.getByLabelText("Nombre"), "Berlín")
    await pickType(user, "Pastelería")
    await user.type(screen.getByLabelText("Precio por kg"), "900")

    // Text content stands in for image bytes: jsdom re-encodes a File body on its way into
    // fetch, so a byte-count assertion would test jsdom, not the upload.
    const photo = new File(["fake-png-bytes"], "berlin.png", { type: "image/png" })
    await user.upload(screen.getByLabelText("Foto del producto"), photo)
    await screen.findByText("Se subirá al guardar: berlin.png")

    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    await waitFor(() => expect(confirmBody).not.toBeNull())
    // The API never received the bytes: it granted, storage got the PUT, then it got the key.
    expect(createBody).not.toHaveProperty("imageUrl")
    expect(grantBody!.contentType).toBe("image/png")
    expect(storagePut!.blobType).toBe("BlockBlob")
    expect(storagePut!.contentType).toBe("image/png")
    expect(storagePut!.body).toBe("fake-png-bytes")
    expect(confirmBody!.key).toBe(grantedKey)
  })

  it("says why when the backend refuses the stored photo (#138), not a vague upload error", async () => {
    const productId = "7f4a2b1c-9d3e-4f5a-8b6c-1d2e3f4a5b6d"
    const uploadUrl = "http://localhost:10000/devstoreaccount1/product-images/k?sig=test"
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        const body = (await request.json()) as CreateProductRequest
        return HttpResponse.json({ ...body, id: productId, imageUrl: null, active: true, serverSeq: 1 }, { status: 201 })
      }),
      mswHttp.post(`${API_BASE_URL}/catalog/products/${productId}/image/upload-url`, () =>
        HttpResponse.json({ key: "k", uploadUrl, expiresAt: Date.now() + 600_000, contentType: "image/png" })
      ),
      mswHttp.put(uploadUrl, () => new HttpResponse(null, { status: 201 })),
      mswHttp.put(`${API_BASE_URL}/catalog/products/${productId}/image`, () =>
        HttpResponse.json({ code: "IMAGE_NOT_AN_IMAGE" }, { status: 422 })
      )
    )
    renderDialog()
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    await user.type(screen.getByLabelText("Nombre"), "Berlín")
    await pickType(user, "Pastelería")
    await user.type(screen.getByLabelText("Precio por kg"), "900")
    await user.upload(screen.getByLabelText("Foto del producto"), new File(["texto"], "falso.png", { type: "image/png" }))
    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    await screen.findByText("Ese archivo no es una foto JPEG, PNG o WebP.")
  })

  it("refuses a non-image file client-side without touching the API", async () => {
    renderDialog()
    // `applyAccept: false`: the input's `accept` would otherwise filter the file out before the
    // handler ever saw it, which is the browser's job -- this test is about the handler's own check.
    const user = userEvent.setup({ applyAccept: false })
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })

    const notAnImage = new File(["hello"], "notes.txt", { type: "text/plain" })
    await user.upload(screen.getByLabelText("Foto del producto"), notAnImage)
    await screen.findByText("La foto debe ser JPG, PNG o WebP.")
  })
})

describe("ProductFormDialog error surface (issue #3)", () => {
  it("shows a Spanish sentence when the save is refused, not the backend's code", async () => {
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, () =>
        HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 })
      )
    )

    renderDialog()
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    await user.type(screen.getByLabelText("Nombre"), "Marraqueta")
    await pickType(user, "Pan")
    await user.type(screen.getByLabelText("Precio por kg"), "3500")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    expect(await screen.findByText(t.errors.codes.FORBIDDEN)).toBeInTheDocument()
    expect(screen.queryByText(/FORBIDDEN/)).not.toBeInTheDocument()
  })
})

describe("ProductFormDialog Tipo decides baked vs Almacén", () => {
  it("has one Tipo field, no Origen, and sends no origin", async () => {
    let body: CreateProductRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        body = (await request.json()) as CreateProductRequest
        return HttpResponse.json({ ...body, active: true, serverSeq: 1 }, { status: 201 })
      })
    )
    renderDialog()
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    expect(screen.queryByLabelText("Origen")).not.toBeInTheDocument()

    await user.type(screen.getByLabelText("Nombre"), "Marraqueta")
    await pickType(user, "Pan")
    await user.type(screen.getByLabelText("Precio por kg"), "3500")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body).not.toHaveProperty("origin")
    expect(body!.productType).toBe("Pan")
  })

  it("forces sold-by unit and hides the weight option once Almacén is chosen", async () => {
    let body: CreateProductRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        body = (await request.json()) as CreateProductRequest
        return HttpResponse.json({ ...body, active: true, serverSeq: 1 }, { status: 201 })
      })
    )
    renderDialog()
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })

    // Starts on the default WEIGHT; picking Almacén must flip it, not leave an invalid pair.
    expect(screen.getByLabelText("Precio por kg")).toBeInTheDocument()
    await pickType(user, "Almacén")

    expect(screen.getByLabelText("Se vende por")).toHaveTextContent("Unidad")
    expect(screen.getByLabelText("Precio unitario")).toBeInTheDocument()
    await user.click(screen.getByLabelText("Se vende por"))
    expect(await screen.findByRole("option", { name: "Unidad" })).toBeInTheDocument()
    expect(screen.queryByRole("option", { name: "Peso" })).not.toBeInTheDocument()
    await user.keyboard("{Escape}")

    await user.type(screen.getByLabelText("Nombre"), "Bebida cola")
    await user.type(screen.getByLabelText("Precio unitario"), "1500")
    await user.type(screen.getByLabelText("Stock inicial (unidades)"), "24")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body!.productType).toBe("Almacén")
    expect(body).not.toHaveProperty("origin")
    expect(body!.soldBy).toBe("UNIT")
    expect(body!.priceMinor).toBe(1500)
    expect(body!.pricePerKgMinor).toBeNull()
    expect(body!.openingStock).toBe(24)
  })

  it("offers an Almacén product only the Almacén Tipo when editing and sends no origin", async () => {
    const cola: ProductDto = {
      id: "11111111-1111-4111-8111-111111111111",
      name: "Bebida cola",
      productType: "Almacén",
      origin: "ALMACEN",
      soldBy: "UNIT",
      pricePerKgMinor: null,
      priceMinor: 1500,
      barcode: null,
      imageUrl: null,
      active: true,
      serverSeq: 3,
    }
    let body: UpdateProductRequest | null = null
    server.use(
      mswHttp.put(`${API_BASE_URL}/catalog/products/${cola.id}`, async ({ request }) => {
        body = (await request.json()) as UpdateProductRequest
        return HttpResponse.json({ ...cola, ...body })
      })
    )
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ProductFormDialog currencyCode="CLP" product={cola} trigger={<Button>Editar</Button>} />
      </QueryClientProvider>
    )
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Editar" }))
    await screen.findByRole("heading", { name: "Editar producto" })

    // The API refuses to cross Almacén <-> Pan / Pastelería, so the select only offers Almacén.
    expect(screen.queryByLabelText("Origen")).not.toBeInTheDocument()
    expect(screen.getByLabelText("Tipo")).toHaveTextContent("Almacén")
    await user.click(screen.getByLabelText("Tipo"))
    expect(await screen.findAllByRole("option")).toHaveLength(1)
    await user.keyboard("{Escape}")
    expect(screen.getByLabelText("Se vende por")).toHaveTextContent("Unidad")
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body).not.toHaveProperty("origin")
    expect(body!.soldBy).toBe("UNIT")
  })

  it("shows a Spanish sentence when the API rejects an Almacén product by weight", async () => {
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, () =>
        HttpResponse.json({ code: "ALMACEN_REQUIRES_UNIT" }, { status: 400 })
      )
    )
    renderDialog()
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    await user.type(screen.getByLabelText("Nombre"), "X")
    await pickType(user, "Pan")
    await user.type(screen.getByLabelText("Precio por kg"), "10")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))
    expect(await screen.findByText(t.errors.codes.ALMACEN_REQUIRES_UNIT)).toBeInTheDocument()
  })
})

describe("ProductFormDialog Almacén stock (ticket #24)", () => {
  function renderAlmacenDialog() {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ProductFormDialog
          currencyCode="CLP"
          branchId="b1b1b1b1-0000-4000-8000-000000000001"
          branchName="Casa Matriz"
          trigger={<Button>Nuevo producto</Button>}
        />
      </QueryClientProvider>
    )
  }

  async function openAlmacenForm(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    await pickType(user, "Almacén")
    await user.type(screen.getByLabelText("Nombre"), "Bebida cola")
    await user.type(screen.getByLabelText("Precio unitario"), "1500")
  }

  it("asks for the opening count only for Almacén, naming the branch it counts", async () => {
    renderAlmacenDialog()
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    expect(screen.queryByLabelText("Stock inicial (unidades)")).not.toBeInTheDocument()
    expect(screen.queryByLabelText("Nivel de reorden (unidades)")).not.toBeInTheDocument()

    await pickType(user, "Almacén")
    expect(screen.getByLabelText("Stock inicial (unidades)")).toBeInTheDocument()
    expect(screen.getByLabelText("Nivel de reorden (unidades)")).toBeInTheDocument()
    expect(screen.getByText(/Casa Matriz/)).toBeInTheDocument()
  })

  it("refuses an Almacén product without an opening count and sends nothing", async () => {
    let posted = 0
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, () => {
        posted += 1
        return HttpResponse.json({}, { status: 201 })
      })
    )
    renderAlmacenDialog()
    const user = userEvent.setup()
    await openAlmacenForm(user)
    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    expect(await screen.findByText("Ingresa el stock inicial (puede ser 0).")).toBeInTheDocument()
    expect(posted).toBe(0)
  })

  it("accepts an opening count of zero and sends it with the branch", async () => {
    let body: CreateProductRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        body = (await request.json()) as CreateProductRequest
        return HttpResponse.json({ ...body, active: true, serverSeq: 1 }, { status: 201 })
      })
    )
    renderAlmacenDialog()
    const user = userEvent.setup()
    await openAlmacenForm(user)
    await user.type(screen.getByLabelText("Stock inicial (unidades)"), "0")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body!.openingStock).toBe(0)
    expect(body!.openingBranchId).toBe("b1b1b1b1-0000-4000-8000-000000000001")
  })

  it("rejects a fractional or negative opening count client-side", async () => {
    renderAlmacenDialog()
    const user = userEvent.setup()
    await openAlmacenForm(user)
    await user.type(screen.getByLabelText("Stock inicial (unidades)"), "-2")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))
    expect(await screen.findByText("El stock inicial debe ser un número entero, 0 o mayor.")).toBeInTheDocument()
  })

  it("saves the reorder level on the new product after it is created", async () => {
    const created = "9f9f9f9f-0000-4000-8000-000000000009"
    let threshold: unknown = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        const sent = (await request.json()) as CreateProductRequest
        return HttpResponse.json({ ...sent, id: created, active: true, serverSeq: 1 }, { status: 201 })
      }),
      mswHttp.put(`${API_BASE_URL}/inventory/product-stock/${created}/threshold`, async ({ request }) => {
        threshold = await request.json()
        return HttpResponse.json({ productId: created, lowStockThresholdUnits: 6 })
      })
    )
    renderAlmacenDialog()
    const user = userEvent.setup()
    await openAlmacenForm(user)
    await user.type(screen.getByLabelText("Stock inicial (unidades)"), "12")
    await user.type(screen.getByLabelText("Nivel de reorden (unidades)"), "6")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))

    await waitFor(() => expect(threshold).toEqual({ thresholdUnits: 6 }))
  })

  it("editing an Almacén product shows its reorder level, no opening count, and sends no opening stock", async () => {
    const cola: ProductDto = {
      id: "11111111-1111-4111-8111-111111111111",
      name: "Bebida cola",
      productType: "Almacén",
      origin: "ALMACEN",
      soldBy: "UNIT",
      pricePerKgMinor: null,
      priceMinor: 1500,
      barcode: null,
      imageUrl: null,
      active: true,
      serverSeq: 3,
    }
    let body: Record<string, unknown> | null = null
    let thresholdPuts = 0
    server.use(
      mswHttp.put(`${API_BASE_URL}/catalog/products/${cola.id}`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...cola, ...body })
      }),
      mswHttp.put(`${API_BASE_URL}/inventory/product-stock/${cola.id}/threshold`, () => {
        thresholdPuts += 1
        return HttpResponse.json({})
      })
    )
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ProductFormDialog currencyCode="CLP" product={cola} reorderLevel={10} trigger={<Button>Editar</Button>} />
      </QueryClientProvider>
    )
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Editar" }))
    await screen.findByRole("heading", { name: "Editar producto" })

    expect(screen.queryByLabelText("Stock inicial (unidades)")).not.toBeInTheDocument()
    expect(screen.getByLabelText("Nivel de reorden (unidades)")).toHaveValue("10")
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body).not.toHaveProperty("openingStock")
    expect(thresholdPuts, "an unchanged reorder level is not re-sent").toBe(0)
  })
})

describe("ProductFormDialog product type picker (ticket #27)", () => {
  it("offers only the managed list, shows the chosen type's label, and sends its name", async () => {
    let body: CreateProductRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, async ({ request }) => {
        body = (await request.json()) as CreateProductRequest
        return HttpResponse.json({ ...body, active: true, serverSeq: 1 }, { status: 201 })
      })
    )
    renderDialog()
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })

    await user.click(screen.getByLabelText("Tipo"))
    expect(await screen.findAllByRole("option")).toHaveLength(3)
    await user.click(screen.getByRole("option", { name: "Pastelería" }))
    expect(screen.getByLabelText("Tipo")).toHaveTextContent("Pastelería")

    await user.type(screen.getByLabelText("Nombre"), "Berlín")
    await user.type(screen.getByLabelText("Precio por kg"), "900")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))
    await waitFor(() => expect(body).not.toBeNull())
    expect(body!.productType).toBe("Pastelería")
  })

  it("will not submit without a type and says so in Spanish", async () => {
    renderDialog()
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    await user.type(screen.getByLabelText("Nombre"), "X")
    await user.type(screen.getByLabelText("Precio por kg"), "10")
    await user.click(screen.getByRole("button", { name: "Crear producto" }))
    expect(await screen.findByText(t.admin.products.form.validation.productTypeRequired)).toBeInTheDocument()
  })

  it("has no create-new option", async () => {
    renderDialog()
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Nuevo producto" }))
    await screen.findByRole("heading", { name: "Nuevo producto" })
    expect(screen.queryByRole("button", { name: "Crear un tipo nuevo" })).not.toBeInTheDocument()
  })

  it("editing a baked product offers Pan and Pastelería but not Almacén", async () => {
    const pan: ProductDto = {
      id: "22222222-2222-4222-8222-222222222222", name: "Marraqueta", productType: "Pan",
      origin: "PANADERIA", soldBy: "UNIT", pricePerKgMinor: null, priceMinor: 500,
      barcode: null, imageUrl: null, active: true, serverSeq: 1,
    }
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ProductFormDialog currencyCode="CLP" product={pan} trigger={<Button>Editar</Button>} />
      </QueryClientProvider>
    )
    const user = userEvent.setup()
    await user.click(screen.getByRole("button", { name: "Editar" }))
    await screen.findByRole("heading", { name: "Editar producto" })
    await user.click(screen.getByLabelText("Tipo"))
    expect((await screen.findAllByRole("option")).map((o) => o.textContent)).toEqual(["Pan", "Pastelería"])
  })
})

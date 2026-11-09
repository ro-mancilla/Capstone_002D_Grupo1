import { z } from "zod"
import { decimalToMinor, minorToDecimal } from "@/lib/format"
import { t } from "@/lib/i18n"
import { isAlmacenType } from "@/lib/productTypes"
import type { CreateProductRequest, ProductDto, UpdateProductRequest } from "@/api/types"

/**
 * Product create/edit form (plan 07 §5 row 1): a single `price` field whose *meaning* (per-kg
 * vs. per-unit) swaps with `soldBy` -- so exactly one of `pricePerKgMinor`/`priceMinor` is ever
 * built (the "validate exactly one of the two is set" requirement is satisfied structurally by
 * `toCreateProductRequest`/`toUpdateProductRequest` never populating both, not by a zod
 * cross-field refinement over two separate inputs).
 */
export const productFormSchema = z.object({
  name: z.string().min(1, t.admin.products.form.validation.nameRequired),
  productType: z.string().min(1, t.admin.products.form.validation.productTypeRequired),
  soldBy: z.enum(["WEIGHT", "UNIT"]),
  price: z.coerce
    .number({ error: t.admin.products.form.validation.pricePositive })
    .positive(t.admin.products.form.validation.pricePositive),
  barcode: z.string().optional(),
  // Almacén only. Kept as text so "empty" stays distinguishable from 0: the opening count is
  // required for Almacén (the dialog checks that), the reorder level is optional.
  openingStock: z.string().optional(),
  reorderLevel: z.string().optional(),
  // The photo is not a form field: it is uploaded to storage separately, after the product row
  // exists (`ProductFormDialog`), so the request bodies below never carry an image URL.
})
  .superRefine((values, context) => {
    if (!isAlmacenType(values.productType)) return
    const whole = (raw: string | undefined) => raw === undefined || raw.trim() === "" || isWholeUnits(raw)
    if (!whole(values.openingStock)) {
      context.addIssue({ code: "custom", path: ["openingStock"], message: t.admin.products.form.validation.openingStockInteger })
    }
    if (!whole(values.reorderLevel)) {
      context.addIssue({ code: "custom", path: ["reorderLevel"], message: t.admin.products.form.validation.reorderLevelInteger })
    }
  })

function isWholeUnits(raw: string): boolean {
  const value = Number(raw)
  return Number.isInteger(value) && value >= 0
}

export type ProductFormValues = z.infer<typeof productFormSchema>

export const emptyProductFormValues: ProductFormValues = {
  name: "",
  productType: "",
  soldBy: "WEIGHT",
  price: 0,
  barcode: "",
  openingStock: "",
  reorderLevel: "",
}

/** `ProductDto` -> form values, for populating the edit dialog. `price` reads from whichever
 * minor-unit field matches the product's current `soldBy` (display/entry is always decimal, per
 * plan 07 §5). */
export function productToFormValues(
  product: ProductDto,
  currencyCode: string,
  reorderLevel?: number | null
): ProductFormValues {
  const minor = product.soldBy === "WEIGHT" ? product.pricePerKgMinor : product.priceMinor
  return {
    name: product.name,
    productType: product.productType,
    soldBy: product.soldBy,
    price: minor != null ? minorToDecimal(minor, currencyCode) : 0,
    barcode: product.barcode ?? "",
    openingStock: "",
    reorderLevel: reorderLevel == null ? "" : String(reorderLevel),
  }
}

/** Form values -> `CreateProductRequest`. Mints the client-side id (products are created with a
 * client-chosen UUID, per `CreateProductRequest.id` / `CatalogRouter.kt`'s POST). Exactly one of
 * `pricePerKgMinor`/`priceMinor` is set, matching `soldBy`. */
export function toCreateProductRequest(
  values: ProductFormValues,
  currencyCode: string,
  openingBranchId?: string
): CreateProductRequest {
  const priceMinor = decimalToMinor(values.price, currencyCode)
  return {
    id: crypto.randomUUID(),
    name: values.name,
    productType: values.productType,
    soldBy: values.soldBy,
    pricePerKgMinor: values.soldBy === "WEIGHT" ? priceMinor : null,
    priceMinor: values.soldBy === "UNIT" ? priceMinor : null,
    barcode: values.barcode || null,
    // Only an Almacén product has stock to open; for Pan and Pastelería the API ignores these.
    ...(isAlmacenType(values.productType)
      ? { openingStock: Number(values.openingStock), openingBranchId: openingBranchId ?? null }
      : {}),
  }
}

/** The reorder level typed on the form: `null` clears it. Only meaningful for Almacén. */
export function reorderLevelOf(values: ProductFormValues): number | null {
  const raw = values.reorderLevel?.trim() ?? ""
  return raw === "" ? null : Number(raw)
}

/** An edit never crosses Almacén <-> Pan / Pastelería (the API rejects it), so the picker only offers the same side. */
export function toUpdateProductRequest(
  values: ProductFormValues,
  currencyCode: string
): UpdateProductRequest {
  const priceMinor = decimalToMinor(values.price, currencyCode)
  return {
    name: values.name,
    productType: values.productType,
    soldBy: values.soldBy,
    pricePerKgMinor: values.soldBy === "WEIGHT" ? priceMinor : null,
    priceMinor: values.soldBy === "UNIT" ? priceMinor : null,
    barcode: values.barcode || null,
  }
}

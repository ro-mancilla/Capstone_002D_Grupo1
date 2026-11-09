import { z } from "zod"
import { t } from "@/lib/i18n"
import type { CreateProductStockMovementRequest } from "@/api/types"

/**
 * Product-stock forms for an Almacén product, opened from the product itself: purchase entry
 * (+units) and manual signed adjustment (±units + reason) build the same
 * `CreateProductStockMovementRequest` -- an append-only `POST /inventory/product-movements`. The
 * product is fixed by the panel the form sits in, so it is an argument here, not a field. Whole
 * units only: a bottle is either on the shelf or it is not, so the schema refuses decimals rather
 * than rounding them.
 */

const strings = t.admin.products.stock

export const productPurchaseFormSchema = z.object({
  quantityUnits: z.coerce
    .number({ error: strings.purchase.validation.quantityPositive })
    .int(strings.purchase.validation.quantityPositive)
    .positive(strings.purchase.validation.quantityPositive),
  note: z.string().optional(),
})

export type ProductPurchaseFormValues = z.infer<typeof productPurchaseFormSchema>

export const emptyProductPurchaseFormValues: ProductPurchaseFormValues = {
  quantityUnits: 0,
  note: "",
}

export function toProductPurchaseRequest(
  values: ProductPurchaseFormValues,
  branchId: string,
  productId: string
): CreateProductStockMovementRequest {
  return {
    id: crypto.randomUUID(),
    branchId,
    productId,
    deltaUnits: values.quantityUnits,
    kind: "PURCHASE",
    note: values.note || null,
    occurredAt: Date.now(),
  }
}

export const productAdjustmentFormSchema = z.object({
  quantityUnits: z.coerce
    .number({ error: strings.adjustment.validation.quantityNonZero })
    .int(strings.adjustment.validation.quantityNonZero)
    .refine((value) => value !== 0, strings.adjustment.validation.quantityNonZero),
  reason: z.string().min(1, strings.adjustment.validation.reasonRequired),
})

export type ProductAdjustmentFormValues = z.infer<typeof productAdjustmentFormSchema>

export const emptyProductAdjustmentFormValues: ProductAdjustmentFormValues = {
  quantityUnits: 0,
  reason: "",
}

/** The sign travels straight through: a -3 entry is a -3 unit movement. */
export function toProductAdjustmentRequest(
  values: ProductAdjustmentFormValues,
  branchId: string,
  productId: string
): CreateProductStockMovementRequest {
  return {
    id: crypto.randomUUID(),
    branchId,
    productId,
    deltaUnits: values.quantityUnits,
    kind: "MANUAL_ADJUSTMENT",
    note: values.reason,
    occurredAt: Date.now(),
  }
}

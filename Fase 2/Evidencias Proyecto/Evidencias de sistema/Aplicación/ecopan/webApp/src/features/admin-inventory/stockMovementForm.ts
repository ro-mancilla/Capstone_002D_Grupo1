import { z } from "zod"
import { kgToGrams } from "@/lib/format"
import { t } from "@/lib/i18n"
import type { CreateStockMovementRequest } from "@/api/types"

/**
 * Stock movement forms (plan 07 §5 row 4): purchase entry (+kg) and manual signed adjustment
 * (±kg + reason) both build the same `CreateStockMovementRequest` -- an append-only `POST
 * /inventory/movements` (I5). Neither ever edits a stored quantity; every submit mints a new
 * movement id and lets the backend accumulate it into the ledger (01 §6.1 / plan 07 §5's
 * cut-line note).
 */

export const purchaseFormSchema = z.object({
  ingredientId: z.string().min(1, t.admin.stockEntry.purchase.validation.ingredientRequired),
  quantityKg: z.coerce
    .number({ error: t.admin.stockEntry.purchase.validation.quantityPositive })
    .positive(t.admin.stockEntry.purchase.validation.quantityPositive),
  note: z.string().optional(),
})

export type PurchaseFormValues = z.infer<typeof purchaseFormSchema>

export const emptyPurchaseFormValues: PurchaseFormValues = {
  ingredientId: "",
  quantityKg: 0,
  note: "",
}

/** Purchase entry -> `+grams` movement. `quantityKg` is always entered positive; the sign is
 * implicit in the movement kind (a purchase is always an increase). */
export function toPurchaseMovementRequest(
  values: PurchaseFormValues,
  branchId: string
): CreateStockMovementRequest {
  return {
    id: crypto.randomUUID(),
    branchId,
    ingredientId: values.ingredientId,
    deltaGrams: kgToGrams(values.quantityKg),
    kind: "PURCHASE",
    note: values.note || null,
    occurredAt: Date.now(),
  }
}

export const adjustmentFormSchema = z.object({
  ingredientId: z.string().min(1, t.admin.stockEntry.adjustment.validation.ingredientRequired),
  quantityKg: z.coerce
    .number({ error: t.admin.stockEntry.adjustment.validation.quantityNonZero })
    .refine((value) => value !== 0, t.admin.stockEntry.adjustment.validation.quantityNonZero),
  reason: z.string().min(1, t.admin.stockEntry.adjustment.validation.reasonRequired),
})

export type AdjustmentFormValues = z.infer<typeof adjustmentFormSchema>

export const emptyAdjustmentFormValues: AdjustmentFormValues = {
  ingredientId: "",
  quantityKg: 0,
  reason: "",
}

/** Manual signed adjustment -- the cut-line fallback (plan 07 §5 / 00 §cut-lines): works whether
 * or not formula auto-decrement is running, because it is *never* derived from a formula -- it
 * is always a directly-entered signed `±kg` amount, appended the same as a purchase. `quantityKg`
 * carries its sign straight through `kgToGrams` into `deltaGrams` (never coerced to a positive
 * magnitude + separate direction flag), so a -2.5 kg entry becomes a -2500 g movement. */
export function toAdjustmentMovementRequest(
  values: AdjustmentFormValues,
  branchId: string
): CreateStockMovementRequest {
  return {
    id: crypto.randomUUID(),
    branchId,
    ingredientId: values.ingredientId,
    deltaGrams: kgToGrams(values.quantityKg),
    kind: "MANUAL_ADJUSTMENT",
    note: values.reason,
    occurredAt: Date.now(),
  }
}

import { z } from "zod"
import { gramsToKg, kgToGrams } from "@/lib/format"
import { t } from "@/lib/i18n"
import type { CreateIngredientRequest, IngredientDto, UpdateIngredientRequest } from "@/api/types"

/** Ingredient create/edit form (plan 07 §5 row 4): low-stock threshold entered/displayed in kg,
 * stored in grams (wire-integer), via `lib/format.ts`'s shared kg<->grams conversion. */
export const ingredientFormSchema = z.object({
  name: z.string().min(1, t.admin.ingredients.form.validation.nameRequired),
  lowStockThresholdKg: z.coerce.number().nonnegative().optional(),
  active: z.boolean(),
})

export type IngredientFormValues = z.infer<typeof ingredientFormSchema>

export const emptyIngredientFormValues: IngredientFormValues = {
  name: "",
  lowStockThresholdKg: undefined,
  active: true,
}

export function ingredientToFormValues(ingredient: IngredientDto): IngredientFormValues {
  return {
    name: ingredient.name,
    lowStockThresholdKg:
      ingredient.lowStockThresholdGrams != null
        ? gramsToKg(ingredient.lowStockThresholdGrams)
        : undefined,
    active: ingredient.active,
  }
}

export function toCreateIngredientRequest(values: IngredientFormValues): CreateIngredientRequest {
  return {
    id: crypto.randomUUID(),
    name: values.name,
    lowStockThresholdGrams:
      values.lowStockThresholdKg != null ? kgToGrams(values.lowStockThresholdKg) : null,
  }
}

export function toUpdateIngredientRequest(values: IngredientFormValues): UpdateIngredientRequest {
  return {
    name: values.name,
    lowStockThresholdGrams:
      values.lowStockThresholdKg != null ? kgToGrams(values.lowStockThresholdKg) : null,
    active: values.active,
  }
}

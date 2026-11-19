import { z } from "zod"
import { bpToKgPerKgFlour, kgPerKgFlourToBp } from "@/lib/format"
import { t } from "@/lib/i18n"
import type { FormulaDto, FormulaLineDto, IngredientDto, PutFormulaRequest } from "@/api/types"

/** Flour is always 10000 bp = 1 kg per kg of flour, the anchor every other line is relative to
 * (01 §5 / 06 §3).
 * Not a flag on `IngredientDto` -- the backend has no "isFlour" concept, only a convention that
 * one line's `basisPoints === FLOUR_BASIS_POINTS`. `resolveFlourIngredientId` below is this
 * client's single place that decides which ingredient plays that role. */
export const FLOUR_BASIS_POINTS = 10000

/**
 * Picks the flour ingredient for a product's formula editor: (1) an existing formula's line
 * whose `basisPoints === 10000` wins (it's already established); otherwise (2) an ingredient
 * whose name looks like flour ("harina"/"flour", case-insensitive, the seeded convention);
 * otherwise (3) the first available ingredient, so a brand-new formula always has *a* pinned
 * 100% row per plan 07 §5 ("always present"), even before the tenant has renamed anything.
 * Returns `null` only when there are no ingredients at all to pin.
 */
export function resolveFlourIngredientId(
  formula: FormulaDto | undefined,
  ingredients: readonly IngredientDto[]
): string | null {
  const existingFlourLine = formula?.lines.find((line) => line.basisPoints === FLOUR_BASIS_POINTS)
  if (existingFlourLine) return existingFlourLine.ingredientId

  const byName = ingredients.find((ingredient) => /harina|flour/i.test(ingredient.name))
  if (byName) return byName.id

  return ingredients[0]?.id ?? null
}

export const formulaLineFormSchema = z.object({
  ingredientId: z.string().min(1),
  /** Kilos of this ingredient per kilo of flour (0.65 = 650 g per kg). Wire form is basis
   * points; the conversion lives in `lib/format.ts` (`kgPerKgFlourToBp`). */
  kgPerKgFlour: z.coerce
    .number({ error: t.admin.formulas.dialog.validation.quantityRequired })
    .positive(t.admin.formulas.dialog.validation.quantityRequired),
})

export const formulaFormSchema = z
  .object({
    lines: z.array(formulaLineFormSchema),
  })
  .check((ctx) => {
    const ids = ctx.value.lines.map((line) => line.ingredientId)
    if (new Set(ids).size !== ids.length) {
      ctx.issues.push({
        code: "custom",
        message: t.admin.formulas.dialog.validation.duplicateIngredient,
        input: ctx.value.lines,
        path: ["lines"],
      })
    }
  })

export type FormulaFormValues = z.infer<typeof formulaFormSchema>

export const emptyFormulaFormValues: FormulaFormValues = { lines: [] }

/** `FormulaDto` -> form values, for populating the edit dialog. The flour line is excluded here
 * -- it's rendered as its own fixed, non-editable row (`FormulaEditorDialog`), never part of the
 * editable `lines` array. */
export function formulaToFormValues(
  formula: FormulaDto | undefined,
  flourIngredientId: string | null
): FormulaFormValues {
  const lines = (formula?.lines ?? []).filter((line) => line.ingredientId !== flourIngredientId)
  return {
    lines: lines.map((line) => ({
      ingredientId: line.ingredientId,
      kgPerKgFlour: bpToKgPerKgFlour(line.basisPoints),
    })),
  }
}

/** Form values -> C6's replace-all request. Always prepends the pinned flour line at exactly
 * `FLOUR_BASIS_POINTS` -- the one invariant the UI enforces that the backend itself doesn't
 * (`CatalogServiceImpl.putFormula` only validates `basisPoints > 0`, per-line, no flour rule). */
export function toPutFormulaRequest(
  existingFormulaId: string | undefined,
  flourIngredientId: string,
  values: FormulaFormValues
): PutFormulaRequest {
  const lines: FormulaLineDto[] = [
    { ingredientId: flourIngredientId, basisPoints: FLOUR_BASIS_POINTS },
    ...values.lines.map((line) => ({
      ingredientId: line.ingredientId,
      basisPoints: kgPerKgFlourToBp(line.kgPerKgFlour),
    })),
  ]
  return { id: existingFormulaId, lines }
}

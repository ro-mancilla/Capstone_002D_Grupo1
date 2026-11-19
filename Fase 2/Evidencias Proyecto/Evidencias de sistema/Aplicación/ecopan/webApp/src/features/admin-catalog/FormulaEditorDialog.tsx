import { useEffect, useMemo, useState } from "react"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useFieldArray, useForm } from "react-hook-form"
import { XIcon } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { usePutFormula } from "@/api/queries/formulas"
import { isModuleDisabledError, errorMessage } from "@/lib/apiErrors"
import { ModuleDisabledState } from "@/components/shared/ModuleDisabledState"
import { deriveFormula } from "@/lib/formulaMath"
import { formatGramsAsKg, kgPerKgFlourToBp, kgToGrams } from "@/lib/format"
import type { FormulaDto, IngredientDto, ProductDto } from "@/api/types"
import { t } from "@/lib/i18n"
import {
  FLOUR_BASIS_POINTS,
  formulaFormSchema,
  formulaToFormValues,
  resolveFlourIngredientId,
  toPutFormulaRequest,
  type FormulaFormValues,
} from "./formulaForm"

interface FormulaEditorDialogProps {
  product: ProductDto
  formula?: FormulaDto
  ingredients: IngredientDto[]
  trigger: React.ReactNode
}

const DEFAULT_PREVIEW_FLOUR_KG = 10

/** Create/edit dialog for a Panadería product's formula, opened from its row on `admin/products` (plan 07 §5 row 2, mandated test surface 1 --
 * plan 07 §10). Flour is a fixed, non-editable "1 kg" row (`resolveFlourIngredientId`); every
 * other line is entered as **kilos per kilo of flour** (0.65 kg of water per kg of flour), backed
 * by a `useFieldArray` and converted to/from basis points via `lib/format.ts`'s
 * `kgPerKgFlourToBp`/`bpToKgPerKgFlour`. Percentages were dropped (team decision, 2026-09): the
 * bakery weighs everything in kilos, so a second unit only made the preview harder to read.
 *
 * The preview asks "how much flour will you use?" and lists every ingredient in kg plus the
 * total dough that makes -- but it still runs the *exact* ledger algorithm (`lib/formulaMath.ts`)
 * on that total dough weight, so what it shows is what the baker client would decrement for a
 * batch of that size. */
export function FormulaEditorDialog({
  product,
  formula,
  ingredients,
  trigger,
}: FormulaEditorDialogProps) {
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [previewFlourKg, setPreviewFlourKg] = useState(String(DEFAULT_PREVIEW_FLOUR_KG))
  const putFormula = usePutFormula()
  const strings = t.admin.formulas.dialog

  const flourIngredientId = useMemo(
    () => resolveFlourIngredientId(formula, ingredients),
    [formula, ingredients]
  )
  const ingredientsById = useMemo(
    () => new Map(ingredients.map((ingredient) => [ingredient.id, ingredient])),
    [ingredients]
  )
  const flourIngredient = flourIngredientId ? ingredientsById.get(flourIngredientId) : undefined

  const form = useForm({
    resolver: zodResolver(formulaFormSchema),
    defaultValues: formulaToFormValues(formula, flourIngredientId),
  })
  const { fields, append, remove } = useFieldArray({ control: form.control, name: "lines" })
  const watchedLines = form.watch("lines")

  useEffect(() => {
    if (!open) return
    form.reset(formulaToFormValues(formula, flourIngredientId))
    setFormError(null)
    setPreviewFlourKg(String(DEFAULT_PREVIEW_FLOUR_KG))
  }, [open, formula, flourIngredientId, form])

  const usedIngredientIds = new Set([
    flourIngredientId,
    ...watchedLines.map((line) => line.ingredientId),
  ])
  const availableIngredients = ingredients.filter(
    (ingredient) => !usedIngredientIds.has(ingredient.id)
  )
  const availableIngredientItems = availableIngredients.map((ingredient) => ({
    value: ingredient.id,
    label: ingredient.name,
  }))

  const previewLines = flourIngredientId
    ? [
        { ingredientId: flourIngredientId, basisPoints: FLOUR_BASIS_POINTS },
        ...watchedLines
          .filter((line) => line.ingredientId && Number(line.kgPerKgFlour) > 0)
          .map((line) => ({
            ingredientId: line.ingredientId,
            basisPoints: kgPerKgFlourToBp(Number(line.kgPerKgFlour)),
          })),
      ]
    : []
  // The ledger algorithm splits a *total dough* weight; the baker thinks in flour. Total dough
  // for F grams of flour is F x totalBp / 10000, rounded to the gram, and the derivation then
  // hands flour back within a gram of what was typed.
  const previewFlourGrams = kgToGrams(Number(previewFlourKg))
  const previewTotalBp = previewLines.reduce((sum, line) => sum + line.basisPoints, 0)
  const previewMixGrams = Math.round((previewFlourGrams * previewTotalBp) / FLOUR_BASIS_POINTS)
  const preview =
    flourIngredientId && Number.isFinite(previewMixGrams) && previewMixGrams > 0
      ? deriveFormula(previewMixGrams, previewLines)
      : null

  async function onSubmit(values: FormulaFormValues) {
    if (!flourIngredientId) return
    setFormError(null)
    try {
      await putFormula.mutateAsync({
        productId: product.id,
        body: toPutFormulaRequest(formula?.id, flourIngredientId, values),
      })
      setOpen(false)
    } catch (error) {
      if (isModuleDisabledError(error)) {
        setFormError(t.errors.moduleDisabled)
        return
      }
      setFormError(errorMessage(error, strings.genericError))
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger as React.ReactElement} />
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>
            {strings.title} -- {product.name}
          </DialogTitle>
        </DialogHeader>

        {isModuleDisabledError(putFormula.error) ? (
          <ModuleDisabledState />
        ) : (
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <FieldGroup>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{strings.ingredientColumn}</TableHead>
                    <TableHead>{strings.quantityColumn}</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow>
                    <TableCell className="font-medium">
                      {flourIngredient?.name ?? "—"}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {strings.flourRowLabel}
                    </TableCell>
                    <TableCell />
                  </TableRow>
                  {fields.map((field, index) => (
                    <TableRow key={field.id}>
                      <TableCell>
                        {ingredientsById.get(watchedLines[index]?.ingredientId)?.name ?? "—"}
                      </TableCell>
                      <TableCell>
                        <Controller
                          name={`lines.${index}.kgPerKgFlour`}
                          control={form.control}
                          render={({ field: quantityField, fieldState }) => (
                            <Field data-invalid={fieldState.invalid} className="gap-1">
                              <Input
                                {...quantityField}
                                value={quantityField.value as number | string}
                                id={`formula-line-kg-${index}`}
                                aria-label={`${strings.quantityColumn} -- ${ingredientsById.get(watchedLines[index]?.ingredientId)?.name ?? ""}`}
                                type="number"
                                step="0.0001"
                                min="0"
                                aria-invalid={fieldState.invalid}
                              />
                              <FieldError errors={[fieldState.error]} />
                            </Field>
                          )}
                        />
                      </TableCell>
                      <TableCell>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label={strings.removeIngredient}
                          onClick={() => remove(index)}
                        >
                          <XIcon className="size-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {availableIngredients.length > 0 ? (
                <Field>
                  <ChoiceSelect
                    options={availableIngredientItems}
                    value=""
                    onValueChange={(ingredientId) => {
                      if (ingredientId) append({ ingredientId, kgPerKgFlour: 0 })
                    }}
                    className="w-full"
                    aria-label={strings.addIngredient}
                    placeholder={strings.ingredientPickerPlaceholder}
                  />
                </Field>
              ) : (
                fields.length === 0 && (
                  <p className="text-muted-foreground text-sm">
                    {strings.noIngredientsAvailable}
                  </p>
                )
              )}
              {form.formState.errors.lines?.message && (
                <p role="alert" className="text-destructive text-sm">
                  {form.formState.errors.lines.message}
                </p>
              )}

              <div className="rounded-lg border p-3">
                <h3 className="mb-2 text-sm font-medium">{strings.preview.title}</h3>
                <Field orientation="horizontal" className="mb-3 max-w-56">
                  <FieldLabel htmlFor="formula-preview-flour-kg">
                    {strings.preview.flourWeightLabel}
                  </FieldLabel>
                  <Input
                    id="formula-preview-flour-kg"
                    type="number"
                    step="0.1"
                    min="0"
                    value={previewFlourKg}
                    onChange={(event) => setPreviewFlourKg(event.target.value)}
                  />
                </Field>
                {preview ? (
                  <ul className="text-sm">
                    {preview.map((result) => (
                      <li key={result.ingredientId} className="flex justify-between gap-4">
                        <span>{ingredientsById.get(result.ingredientId)?.name ?? "—"}</span>
                        <span>{formatGramsAsKg(result.grams)}</span>
                      </li>
                    ))}
                    <li className="flex justify-between gap-4 border-t pt-1 font-medium">
                      <span>{strings.preview.total}</span>
                      <span>
                        {formatGramsAsKg(preview.reduce((sum, r) => sum + r.grams, 0))}
                      </span>
                    </li>
                  </ul>
                ) : (
                  <p className="text-muted-foreground text-sm">{strings.preview.invalid}</p>
                )}
              </div>

              {formError && (
                <p role="alert" className="text-destructive text-sm">
                  {formError}
                </p>
              )}
              <Button type="submit" className="w-full" disabled={putFormula.isPending}>
                {t.common.save}
              </Button>
            </FieldGroup>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

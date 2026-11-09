import { useState } from "react"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useAppendStockMovement } from "@/api/queries/inventory"
import { errorMessage } from "@/lib/apiErrors"
import type { IngredientDto } from "@/api/types"
import { t } from "@/lib/i18n"
import {
  adjustmentFormSchema,
  emptyAdjustmentFormValues,
  toAdjustmentMovementRequest,
  type AdjustmentFormValues,
} from "./stockMovementForm"

interface AdjustmentFormProps {
  branchId: string
  ingredients: IngredientDto[]
  /** When set, the form is for that one ingredient: no picker, the id rides along. */
  fixedIngredientId?: string
}

/**
 * Manual signed stock adjustment (plan 07 §5 row 4) -- the cut-line fallback: a `±kg` amount +
 * reason, always appended as a new `MANUAL_ADJUSTMENT` movement, never an edit to a stored
 * quantity. This form has no dependency on formula auto-decrement being enabled; it works
 * identically whether or not `INVENTORY_FORMULAS`-derived movements exist in the ledger (the
 * module gate on this whole page/route only reflects that the backend's `/inventory` router is
 * uniformly gated by that module -- see `stockMovementForm.ts`'s doc comment).
 */
export function AdjustmentForm({ branchId, ingredients, fixedIngredientId }: AdjustmentFormProps) {
  const ingredientItems = ingredients.map((ingredient) => ({
    value: ingredient.id,
    label: ingredient.name,
  }))

  const strings = t.admin.stockEntry.adjustment
  const [formError, setFormError] = useState<string | null>(null)
  const appendMovement = useAppendStockMovement()

  const initialValues = { ...emptyAdjustmentFormValues, ingredientId: fixedIngredientId ?? "" }
  const form = useForm({
    resolver: zodResolver(adjustmentFormSchema),
    defaultValues: initialValues,
  })

  async function onSubmit(values: AdjustmentFormValues) {
    setFormError(null)
    try {
      await appendMovement.mutateAsync(toAdjustmentMovementRequest(values, branchId))
      toast.success(strings.success)
      form.reset(initialValues)
    } catch (error) {
      setFormError(errorMessage(error, strings.genericError))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{strings.title}</CardTitle>
        <CardDescription>{strings.description}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            {fixedIngredientId === undefined && (
              <Controller
                name="ingredientId"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="adjustment-ingredient">{strings.ingredientLabel}</FieldLabel>
                    <ChoiceSelect
                      options={ingredientItems}
                      value={field.value}
                      onValueChange={field.onChange}
                      id="adjustment-ingredient"
                      className="w-full"
                    />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
            )}
            <Controller
              name="quantityKg"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="adjustment-quantity">{strings.quantityLabel}</FieldLabel>
                  <Input
                    {...field}
                    value={field.value as number | string}
                    id="adjustment-quantity"
                    type="number"
                    step="0.001"
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="reason"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="adjustment-reason">{strings.reasonLabel}</FieldLabel>
                  <Input
                    {...field}
                    id="adjustment-reason"
                    placeholder={strings.reasonPlaceholder}
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            {formError && (
              <p role="alert" className="text-destructive text-sm">
                {formError}
              </p>
            )}
            <Button type="submit" variant="secondary" disabled={appendMovement.isPending}>
              {strings.submit}
            </Button>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}

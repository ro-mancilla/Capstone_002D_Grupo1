import { useState } from "react"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useAppendStockMovement } from "@/api/queries/inventory"
import { errorMessage } from "@/lib/apiErrors"
import type { IngredientDto } from "@/api/types"
import { t } from "@/lib/i18n"
import {
  emptyPurchaseFormValues,
  purchaseFormSchema,
  toPurchaseMovementRequest,
  type PurchaseFormValues,
} from "./stockMovementForm"

interface PurchaseEntryFormProps {
  branchId: string
  ingredients: IngredientDto[]
  /** When set, the form is for that one ingredient: no picker, the id rides along. */
  fixedIngredientId?: string
}

/** Purchase entry (plan 07 §5 row 4): append a `+grams` movement, entered in kg. */
export function PurchaseEntryForm({ branchId, ingredients, fixedIngredientId }: PurchaseEntryFormProps) {
  const ingredientItems = ingredients.map((ingredient) => ({
    value: ingredient.id,
    label: ingredient.name,
  }))

  const strings = t.admin.stockEntry.purchase
  const [formError, setFormError] = useState<string | null>(null)
  const appendMovement = useAppendStockMovement()

  const initialValues = { ...emptyPurchaseFormValues, ingredientId: fixedIngredientId ?? "" }
  const form = useForm({
    resolver: zodResolver(purchaseFormSchema),
    defaultValues: initialValues,
  })

  async function onSubmit(values: PurchaseFormValues) {
    setFormError(null)
    try {
      await appendMovement.mutateAsync(toPurchaseMovementRequest(values, branchId))
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
                    <FieldLabel htmlFor="purchase-ingredient">{strings.ingredientLabel}</FieldLabel>
                    <ChoiceSelect
                      options={ingredientItems}
                      value={field.value}
                      onValueChange={field.onChange}
                      id="purchase-ingredient"
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
                  <FieldLabel htmlFor="purchase-quantity">{strings.quantityLabel}</FieldLabel>
                  <Input
                    {...field}
                    value={field.value as number | string}
                    id="purchase-quantity"
                    type="number"
                    step="0.001"
                    min="0"
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="note"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor="purchase-note">{strings.noteLabel}</FieldLabel>
                  <Input {...field} id="purchase-note" placeholder={strings.notePlaceholder} />
                </Field>
              )}
            />
            {formError && (
              <p role="alert" className="text-destructive text-sm">
                {formError}
              </p>
            )}
            <Button type="submit" disabled={appendMovement.isPending}>
              {strings.submit}
            </Button>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}

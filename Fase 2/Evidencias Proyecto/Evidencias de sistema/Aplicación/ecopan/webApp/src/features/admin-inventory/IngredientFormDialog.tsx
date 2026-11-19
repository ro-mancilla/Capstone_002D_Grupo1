import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
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
import { Switch } from "@/components/ui/switch"
import { useCreateIngredient, useUpdateIngredient } from "@/api/queries/inventory"
import { errorMessage } from "@/lib/apiErrors"
import type { IngredientDto } from "@/api/types"
import { t } from "@/lib/i18n"
import {
  emptyIngredientFormValues,
  ingredientFormSchema,
  ingredientToFormValues,
  toCreateIngredientRequest,
  toUpdateIngredientRequest,
  type IngredientFormValues,
} from "./ingredientForm"

interface IngredientFormDialogProps {
  ingredient?: IngredientDto
  trigger: React.ReactNode
}

/** Create/edit dialog for `admin/ingredients` (plan 07 §5 row 4). */
export function IngredientFormDialog({ ingredient, trigger }: IngredientFormDialogProps) {
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const createIngredient = useCreateIngredient()
  const updateIngredient = useUpdateIngredient()
  const isEdit = ingredient !== undefined
  const strings = t.admin.ingredients.form

  const form = useForm({
    resolver: zodResolver(ingredientFormSchema),
    defaultValues: ingredient ? ingredientToFormValues(ingredient) : emptyIngredientFormValues,
  })

  useEffect(() => {
    if (!open) return
    form.reset(ingredient ? ingredientToFormValues(ingredient) : emptyIngredientFormValues)
    setFormError(null)
  }, [open, ingredient, form])

  async function onSubmit(values: IngredientFormValues) {
    setFormError(null)
    try {
      if (isEdit) {
        await updateIngredient.mutateAsync({ id: ingredient.id, body: toUpdateIngredientRequest(values) })
      } else {
        await createIngredient.mutateAsync(toCreateIngredientRequest(values))
      }
      setOpen(false)
    } catch (error) {
      setFormError(errorMessage(error, strings.genericError))
    }
  }

  const isSubmitting = createIngredient.isPending || updateIngredient.isPending

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger as React.ReactElement} />
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? t.admin.ingredients.editIngredient : t.admin.ingredients.newIngredient}
          </DialogTitle>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="name"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="ingredient-name">{strings.nameLabel}</FieldLabel>
                  <Input
                    {...field}
                    id="ingredient-name"
                    placeholder={strings.namePlaceholder}
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="lowStockThresholdKg"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="ingredient-low-stock">
                    {strings.lowStockThresholdLabel}
                  </FieldLabel>
                  <Input
                    {...field}
                    value={(field.value as number | string | undefined) ?? ""}
                    id="ingredient-low-stock"
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder={strings.lowStockThresholdPlaceholder}
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            {isEdit && (
              <Controller
                name="active"
                control={form.control}
                render={({ field }) => (
                  <Field orientation="horizontal">
                    <FieldLabel htmlFor="ingredient-active">{strings.activeLabel}</FieldLabel>
                    <Switch
                      id="ingredient-active"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </Field>
                )}
              />
            )}
            {formError && (
              <p role="alert" className="text-destructive text-sm">
                {formError}
              </p>
            )}
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isEdit ? strings.submitEdit : strings.submitCreate}
            </Button>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}

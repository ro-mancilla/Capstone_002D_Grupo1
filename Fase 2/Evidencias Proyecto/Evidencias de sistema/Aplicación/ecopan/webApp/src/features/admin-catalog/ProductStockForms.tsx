import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useAppendProductStockMovement } from "@/api/queries/inventory"
import { errorMessage } from "@/lib/apiErrors"
import { t } from "@/lib/i18n"
import {
  emptyProductAdjustmentFormValues,
  emptyProductPurchaseFormValues,
  productAdjustmentFormSchema,
  productPurchaseFormSchema,
  toProductAdjustmentRequest,
  toProductPurchaseRequest,
  type ProductAdjustmentFormValues,
  type ProductPurchaseFormValues,
} from "./productStockForm"

interface ProductStockFormProps {
  branchId: string
  /** The Almacén product the panel is open on; the forms never choose one. */
  productId: string
}

/** Purchase entry for an Almacén product: append a `+units` movement. */
export function ProductPurchaseForm({ branchId, productId }: ProductStockFormProps) {
  const strings = t.admin.products.stock.purchase
  const [formError, setFormError] = useState<string | null>(null)
  const appendMovement = useAppendProductStockMovement()

  const form = useForm({
    resolver: zodResolver(productPurchaseFormSchema),
    defaultValues: emptyProductPurchaseFormValues,
  })

  async function onSubmit(values: ProductPurchaseFormValues) {
    setFormError(null)
    try {
      await appendMovement.mutateAsync(toProductPurchaseRequest(values, branchId, productId))
      toast.success(strings.success)
      form.reset(emptyProductPurchaseFormValues)
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
        {/* `noValidate`: a whole-unit input has step 1, and the browser would otherwise block a
            "2.5" with its own untranslated tooltip; zod's Spanish message is the one to show. */}
        <form noValidate onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="quantityUnits"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="product-purchase-quantity">{strings.quantityLabel}</FieldLabel>
                  <Input
                    {...field}
                    value={field.value as number | string}
                    id="product-purchase-quantity"
                    type="number"
                    step="1"
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
                  <FieldLabel htmlFor="product-purchase-note">{strings.noteLabel}</FieldLabel>
                  <Input {...field} id="product-purchase-note" placeholder={strings.notePlaceholder} />
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

/** Manual signed unit adjustment for an Almacén product -- always a new movement, never an edit. */
export function ProductAdjustmentForm({ branchId, productId }: ProductStockFormProps) {
  const strings = t.admin.products.stock.adjustment
  const [formError, setFormError] = useState<string | null>(null)
  const appendMovement = useAppendProductStockMovement()

  const form = useForm({
    resolver: zodResolver(productAdjustmentFormSchema),
    defaultValues: emptyProductAdjustmentFormValues,
  })

  async function onSubmit(values: ProductAdjustmentFormValues) {
    setFormError(null)
    try {
      await appendMovement.mutateAsync(toProductAdjustmentRequest(values, branchId, productId))
      toast.success(strings.success)
      form.reset(emptyProductAdjustmentFormValues)
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
        {/* `noValidate`: a whole-unit input has step 1, and the browser would otherwise block a
            "2.5" with its own untranslated tooltip; zod's Spanish message is the one to show. */}
        <form noValidate onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="quantityUnits"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="product-adjustment-quantity">{strings.quantityLabel}</FieldLabel>
                  <Input
                    {...field}
                    value={field.value as number | string}
                    id="product-adjustment-quantity"
                    type="number"
                    step="1"
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
                  <FieldLabel htmlFor="product-adjustment-reason">{strings.reasonLabel}</FieldLabel>
                  <Input
                    {...field}
                    id="product-adjustment-reason"
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

import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { FieldError, FieldLabel } from "@/components/ui/field"
import { productTypeOptions } from "@/lib/productTypes"
import { t } from "@/lib/i18n"

interface ProductTypePickerProps {
  value: string
  onChange: (type: string) => void
  invalid?: boolean
  error?: { message?: string }
  /** The stored Tipo when editing: limits the choices to the same side (Almacén or baked). */
  editing?: string
}

/**
 * The product's one classification: a plain select over the fixed list (Pan, Pastelería,
 * Almacén). No create-new -- the list is code-owned. `ChoiceSelect` derives Base UI's required
 * `items` map from the options so the trigger shows the label.
 */
export function ProductTypePicker({ value, onChange, invalid, error, editing }: ProductTypePickerProps) {
  const strings = t.admin.products.form
  return (
    <>
      <FieldLabel htmlFor="product-type">{strings.productTypeLabel}</FieldLabel>
      <ChoiceSelect
        options={productTypeOptions(editing)}
        value={value}
        onValueChange={onChange}
        placeholder={strings.productTypePlaceholder}
        id="product-type"
        className="w-full"
        aria-invalid={invalid}
      />
      <FieldError errors={[error]} />
    </>
  )
}

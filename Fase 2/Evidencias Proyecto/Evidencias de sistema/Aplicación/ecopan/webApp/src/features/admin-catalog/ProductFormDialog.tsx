import { useEffect, useRef, useState } from "react"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
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
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { toast } from "sonner"
import { ProductTypePicker } from "./ProductTypePicker"
import { useSetProductLowStockThreshold } from "@/api/queries/inventory"
import {
  useCreateProduct,
  useDeleteProductImage,
  useUpdateProduct,
  useUploadProductImage,
} from "@/api/queries/catalog"
import { ApiError } from "@/api/http"
import { errorMessage } from "@/lib/apiErrors"
import type { ProductDto } from "@/api/types"
import { t } from "@/lib/i18n"
import { isAlmacenType } from "@/lib/productTypes"
import {
  emptyProductFormValues,
  productFormSchema,
  productToFormValues,
  reorderLevelOf,
  toCreateProductRequest,
  toUpdateProductRequest,
  type ProductFormValues,
} from "./productForm"

interface ProductFormDialogProps {
  currencyCode: string
  /** Present -> edit dialog seeded from this product; absent -> create dialog. */
  product?: ProductDto
  /** The branch whose shelf an Almacén product's opening count describes (create only). */
  branchId?: string
  branchName?: string
  /** Almacén edit: the product's current reorder level, so the field starts on it. */
  reorderLevel?: number | null
  trigger: React.ReactNode
}

/** Create/edit dialog for `admin/products` (plan 07 §5 row 1). `soldBy`'s select swaps which
 * price field is shown -- both bind to the same `price` form field, only the label/meaning
 * changes, so `toCreateProductRequest`/`toUpdateProductRequest` never populate both minor-unit
 * fields at once. */
const SOLD_BY_ITEMS = [
  { value: "WEIGHT", label: t.admin.products.soldByWeight },
  { value: "UNIT", label: t.admin.products.soldByUnit },
]

/** Almacén goods are bought finished and counted in units: weight is not an option for them. */
const UNIT_ONLY_ITEMS = SOLD_BY_ITEMS.filter((item) => item.value === "UNIT")

const PHOTO_TYPES = new Set(["image/jpeg", "image/png", "image/webp"])
const PHOTO_MAX_BYTES = 5 * 1024 * 1024

export function ProductFormDialog({
  currencyCode,
  product,
  branchId,
  branchName,
  reorderLevel,
  trigger,
}: ProductFormDialogProps) {
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  // The photo is not part of the form values: it is uploaded to storage on its own, after the
  // product row exists (a new product has no id until the API returns one). `pendingFile` is the
  // file chosen and not yet uploaded; `removePhoto` marks the current photo for deletion.
  const [pendingFile, setPendingFile] = useState<File | null>(null)
  const [pendingPreview, setPendingPreview] = useState<string | null>(null)
  const [removePhoto, setRemovePhoto] = useState(false)
  const [photoError, setPhotoError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const createProduct = useCreateProduct()
  const updateProduct = useUpdateProduct()
  const uploadImage = useUploadProductImage()
  const deleteImage = useDeleteProductImage()
  const setReorderLevel = useSetProductLowStockThreshold()
  const isEdit = product !== undefined
  const strings = t.admin.products.form
  const photoStrings = strings.photo

  const form = useForm({
    resolver: zodResolver(productFormSchema),
    defaultValues: product ? productToFormValues(product, currencyCode, reorderLevel) : emptyProductFormValues,
  })

  useEffect(() => {
    if (!open) return
    form.reset(product ? productToFormValues(product, currencyCode, reorderLevel) : emptyProductFormValues)
    setFormError(null)
    setPendingFile(null)
    setRemovePhoto(false)
    setPhotoError(null)
  }, [open, product, currencyCode, reorderLevel, form])

  // Object URL for the preview of a not-yet-uploaded file; revoked when it changes or unmounts.
  useEffect(() => {
    if (!pendingFile) {
      setPendingPreview(null)
      return
    }
    const url = URL.createObjectURL(pendingFile)
    setPendingPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [pendingFile])

  const soldBy = form.watch("soldBy")
  const productType = form.watch("productType")
  const isAlmacen = isAlmacenType(productType)

  function onPhotoChosen(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null
    event.target.value = ""
    if (!file) return
    if (!PHOTO_TYPES.has(file.type)) {
      setPhotoError(photoStrings.invalidType)
      return
    }
    if (file.size > PHOTO_MAX_BYTES) {
      setPhotoError(photoStrings.tooLarge)
      return
    }
    setPhotoError(null)
    setPendingFile(file)
    setRemovePhoto(false)
  }

  async function onSubmit(values: ProductFormValues) {
    setFormError(null)
    // An Almacén product is tracked from creation, so the count is not optional (0 is a count).
    if (!isEdit && isAlmacenType(values.productType) && (values.openingStock ?? "").trim() === "") {
      form.setError("openingStock", { message: strings.validation.openingStockRequired })
      return
    }
    let saved: ProductDto
    try {
      saved = isEdit
        ? await updateProduct.mutateAsync({
            id: product.id,
            body: toUpdateProductRequest(values, currencyCode),
          })
        : await createProduct.mutateAsync(toCreateProductRequest(values, currencyCode, branchId))
    } catch (error) {
      setFormError(errorMessage(error, strings.genericError))
      return
    }

    // The reorder level is its own endpoint (it is stock, not catalog). Same posture as the photo:
    // the product row exists, so a failure here is reported without pretending nothing was saved.
    if (isAlmacenType(values.productType)) {
      const desired = reorderLevelOf(values)
      if (desired !== (isEdit ? (reorderLevel ?? null) : null)) {
        try {
          await setReorderLevel.mutateAsync({ productId: saved.id, thresholdUnits: desired })
        } catch {
          toast.error(strings.reorderLevelError)
          setOpen(false)
          return
        }
      }
    }

    // The row is saved; the photo is a second, separable step. If it fails the dialog stays
    // open on the photo error so nothing looks lost -- the product exists, only its picture is
    // missing, and "Editar" gets back here.
    try {
      if (pendingFile) {
        await uploadImage.mutateAsync({ id: saved.id, file: pendingFile })
      } else if (removePhoto && saved.imageUrl) {
        await deleteImage.mutateAsync(saved.id)
      }
    } catch (error) {
      // Deliberately not `errorMessage`: this branch is the photo upload failing, not the
      // product save, and both sentences name the photo. The shared STORAGE_UNAVAILABLE copy
      // would be a step vaguer here.
      // The backend inspects what landed in storage (#138): a file that is too big or not an image
      // says so, instead of the vague "could not upload".
      const refusal =
        error instanceof ApiError && (error.code === "IMAGE_TOO_LARGE" || error.code === "IMAGE_NOT_AN_IMAGE")
      setPhotoError(
        refusal
          ? errorMessage(error)
          : error instanceof ApiError && error.status === 503
            ? photoStrings.storageUnavailable
            : photoStrings.uploadError
      )
      return
    }
    setOpen(false)
  }

  const isSubmitting =
    createProduct.isPending ||
    updateProduct.isPending ||
    uploadImage.isPending ||
    deleteImage.isPending ||
    setReorderLevel.isPending
  const currentPhotoUrl = !removePhoto && !pendingFile ? (product?.imageUrl ?? null) : null
  const shownPhotoUrl = pendingPreview ?? currentPhotoUrl

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger as React.ReactElement} />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? t.admin.products.editProduct : t.admin.products.newProduct}
          </DialogTitle>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="name"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="product-name">{strings.nameLabel}</FieldLabel>
                  <Input
                    {...field}
                    id="product-name"
                    placeholder={strings.namePlaceholder}
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="productType"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <ProductTypePicker
                    value={field.value}
                    onChange={(type) => {
                      field.onChange(type)
                      // The API rejects Almacén by weight, so make the pair unreachable here.
                      if (isAlmacenType(type)) form.setValue("soldBy", "UNIT")
                    }}
                    invalid={fieldState.invalid}
                    error={fieldState.error}
                    editing={product?.productType}
                  />
                </Field>
              )}
            />
            <Controller
              name="soldBy"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor="product-sold-by">{strings.soldByLabel}</FieldLabel>
                  <ChoiceSelect
                    options={isAlmacen ? UNIT_ONLY_ITEMS : SOLD_BY_ITEMS}
                    value={field.value}
                    onValueChange={field.onChange}
                    id="product-sold-by"
                    className="w-full"
                  />
                </Field>
              )}
            />
            <Controller
              name="price"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="product-price">
                    {soldBy === "WEIGHT" ? strings.pricePerKgLabel : strings.priceLabel}
                  </FieldLabel>
                  <Input
                    {...field}
                    value={field.value as number | string}
                    id="product-price"
                    type="number"
                    step="0.01"
                    min="0"
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            {isAlmacen && !isEdit && (
              <Controller
                name="openingStock"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="product-opening-stock">{strings.openingStockLabel}</FieldLabel>
                    <Input
                      {...field}
                      value={field.value ?? ""}
                      id="product-opening-stock"
                      inputMode="numeric"
                      aria-invalid={fieldState.invalid}
                    />
                    <FieldDescription>
                      {branchName ? `${strings.branchLabel}: ${branchName}. ` : ""}
                      {strings.openingStockHelp}
                    </FieldDescription>
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
            )}
            {isAlmacen && (
              <Controller
                name="reorderLevel"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="product-reorder-level">{strings.reorderLevelLabel}</FieldLabel>
                    <Input
                      {...field}
                      value={field.value ?? ""}
                      id="product-reorder-level"
                      inputMode="numeric"
                      aria-invalid={fieldState.invalid}
                    />
                    <FieldDescription>{strings.reorderLevelHelp}</FieldDescription>
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
            )}
            <Controller
              name="barcode"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor="product-barcode">{strings.barcodeLabel}</FieldLabel>
                  <Input
                    {...field}
                    id="product-barcode"
                    placeholder={strings.barcodePlaceholder}
                  />
                </Field>
              )}
            />
            <Field>
              <FieldLabel htmlFor="product-photo">{photoStrings.label}</FieldLabel>
              <div className="flex items-center gap-3">
                {shownPhotoUrl ? (
                  <img
                    src={shownPhotoUrl}
                    alt=""
                    className="bg-muted size-16 shrink-0 rounded-md object-cover"
                  />
                ) : (
                  <span
                    aria-hidden
                    className="bg-muted text-muted-foreground flex size-16 shrink-0 items-center justify-center rounded-md text-xs"
                  >
                    —
                  </span>
                )}
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    {shownPhotoUrl ? photoStrings.replace : photoStrings.choose}
                  </Button>
                  {shownPhotoUrl && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        setPendingFile(null)
                        setRemovePhoto(true)
                        setPhotoError(null)
                      }}
                    >
                      {photoStrings.remove}
                    </Button>
                  )}
                </div>
                {/* Native input kept for the file dialog and for assistive tech; the styled
                    buttons above are what a mouse user sees. */}
                <input
                  ref={fileInputRef}
                  id="product-photo"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="sr-only"
                  onChange={onPhotoChosen}
                />
              </div>
              <FieldDescription>
                {pendingFile
                  ? photoStrings.pending(pendingFile.name)
                  : removePhoto && product?.imageUrl
                    ? photoStrings.removePending
                    : photoStrings.help}
              </FieldDescription>
              {photoError && (
                <p role="alert" className="text-destructive text-sm">
                  {photoError}
                </p>
              )}
            </Field>
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

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { AlertTriangle } from "lucide-react"
import { StatusBadge } from "@/components/shared/StatusBadge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { productStockLevelsQuery, useSetProductLowStockThreshold } from "@/api/queries/inventory"
import type { ProductDto, ProductStockLevelDto } from "@/api/types"
import { errorMessage } from "@/lib/apiErrors"
import { formatUnits } from "@/lib/format"
import { t } from "@/lib/i18n"
import { ProductMovementLedger } from "./ProductMovementLedger"
import { ProductAdjustmentForm, ProductPurchaseForm } from "./ProductStockForms"

const DATE_FORMAT = new Intl.DateTimeFormat("es-CL", { dateStyle: "medium" })

interface ProductStockDialogProps {
  product: ProductDto
  branchId: string
  /** Whoever may adjust stock may also record movements and set the reorder level. */
  canAdjust: boolean
  trigger: React.ReactElement
}

/**
 * The stock side of one Almacén product, opened from its row on the product list: current stock
 * (with a warning when negative), the reorder level, and the forms that record a purchase or a
 * manual adjustment, over that product's own ledger. This is the one place stock is read and
 * written -- there is no separate stock page for finished goods.
 */
export function ProductStockDialog({ product, branchId, canAdjust, trigger }: ProductStockDialogProps) {
  const strings = t.admin.products.stock.panel
  const [open, setOpen] = useState(false)
  const levelsResult = useQuery({
    ...productStockLevelsQuery(branchId),
    enabled: open && Boolean(branchId),
  })
  const level = levelsResult.data?.find((candidate) => candidate.productId === product.id)

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger} />
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{strings.title(product.name)}</DialogTitle>
          <DialogDescription>{strings.description}</DialogDescription>
        </DialogHeader>

        {levelsResult.isLoading ? (
          <Skeleton className="h-24 w-full" />
        ) : !level ? (
          <p role="alert" className="text-destructive text-sm">
            {strings.loadError}
          </p>
        ) : (
          <div className="flex flex-col gap-6">
            <StockSummary level={level} canAdjust={canAdjust} />
            {canAdjust && (
              <div className="grid gap-4 md:grid-cols-2">
                <ProductPurchaseForm branchId={branchId} productId={product.id} />
                <ProductAdjustmentForm branchId={branchId} productId={product.id} />
              </div>
            )}
            <ProductMovementLedger branchId={branchId} productId={product.id} />
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function StockSummary({ level, canAdjust }: { level: ProductStockLevelDto; canAdjust: boolean }) {
  const strings = t.admin.products.stock.panel
  const negative = level.currentUnits < 0
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-6">
        <div className="grid gap-1">
          <span className="text-muted-foreground text-sm">{strings.current}</span>
          <span
            className={`font-display text-4xl font-semibold tabular-nums ${negative ? "text-destructive" : ""}`}
            data-testid="current-stock"
          >
            {formatUnits(level.currentUnits)}
          </span>
        </div>
        <dl className="text-muted-foreground grid gap-1 text-sm">
          <div className="flex gap-2">
            <dt>{strings.received}:</dt>
            <dd className="tabular-nums">{formatUnits(level.receivedUnits)}</dd>
          </div>
          <div className="flex gap-2">
            <dt>{strings.sold}:</dt>
            <dd className="tabular-nums">{formatUnits(level.soldUnits)}</dd>
          </div>
          <div>{strings.since(DATE_FORMAT.format(new Date(level.trackedSince)))}</div>
        </dl>
        {!negative && level.currentUnits === 0 && (
          <StatusBadge status="outOfStock" />
        )}
        {level.lowStock && !negative && level.currentUnits > 0 && (
          <StatusBadge status="low" />
        )}
      </div>

      {negative && (
        <div
          role="alert"
          className="border-destructive/50 bg-destructive/5 flex flex-col gap-1 rounded-lg border p-3"
        >
          <p className="text-destructive flex items-center gap-2 text-sm font-medium">
            <AlertTriangle className="size-4" aria-hidden="true" />
            {strings.negativeTitle}
          </p>
          <p className="text-muted-foreground text-sm">{strings.negativeBody}</p>
        </div>
      )}

      <ReorderLevel level={level} canEdit={canAdjust} />
    </div>
  )
}

/** The reorder level: shown as a number (or "Sin nivel de reorden"), editable inline by whoever
 * may adjust stock. Saving an empty field clears it; the server owns the low-stock verdict. */
function ReorderLevel({ level, canEdit }: { level: ProductStockLevelDto; canEdit: boolean }) {
  const strings = t.admin.products.stock.panel
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState("")
  const mutation = useSetProductLowStockThreshold()

  const save = (thresholdUnits: number | null) =>
    mutation.mutate({ productId: level.productId, thresholdUnits }, { onSuccess: () => setEditing(false) })

  if (!editing) {
    const none = level.lowStockThresholdUnits === null
    return (
      <div className="flex items-center gap-2 text-sm">
        <span className="text-muted-foreground">{strings.reorderLevel}:</span>
        <span className={none ? "text-muted-foreground" : "font-medium"}>
          {none ? strings.noReorderLevel : formatUnits(level.lowStockThresholdUnits!)}
        </span>
        {canEdit && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setDraft(none ? "" : String(level.lowStockThresholdUnits))
              setEditing(true)
            }}
          >
            {strings.editReorderLevel}
          </Button>
        )}
      </div>
    )
  }

  const parsed = draft.trim() === "" ? null : Number(draft)
  const invalid = parsed !== null && (!Number.isInteger(parsed) || parsed < 0)
  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap items-center gap-2">
        <Input
          aria-label={strings.reorderLevelInput}
          inputMode="numeric"
          className="w-28"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
        />
        <Button size="sm" disabled={invalid || mutation.isPending} onClick={() => save(parsed)}>
          {strings.saveReorderLevel}
        </Button>
        <Button variant="ghost" size="sm" disabled={mutation.isPending} onClick={() => save(null)}>
          {strings.clearReorderLevel}
        </Button>
      </div>
      {mutation.isError && <p className="text-destructive text-xs">{errorMessage(mutation.error)}</p>}
    </div>
  )
}

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { AlertTriangle } from "lucide-react"
import { StatusBadge } from "@/components/shared/StatusBadge"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { stockLevelsQuery } from "@/api/queries/inventory"
import type { IngredientDto } from "@/api/types"
import { formatGramsAsKg } from "@/lib/format"
import { t } from "@/lib/i18n"
import { AdjustmentForm } from "./AdjustmentForm"
import { MovementLedger } from "./MovementLedger"
import { PurchaseEntryForm } from "./PurchaseEntryForm"

interface IngredientStockDialogProps {
  ingredient: IngredientDto
  branchId: string
  trigger: React.ReactElement
}

/**
 * The stock side of one ingredient, opened from its row on the Insumos list: current stock (with a
 * warning when negative), the purchase and manual-adjustment forms, and that ingredient's own
 * ledger. Same shape as the product *Stock* panel on `admin/products`. This is the one place
 * ingredient stock is written -- there is no separate stock-entry page.
 */
export function IngredientStockDialog({ ingredient, branchId, trigger }: IngredientStockDialogProps) {
  const strings = t.admin.ingredients.stock
  const [open, setOpen] = useState(false)
  const levelsResult = useQuery({ ...stockLevelsQuery(branchId), enabled: open && Boolean(branchId) })
  const level = levelsResult.data?.find((candidate) => candidate.ingredientId === ingredient.id)
  const negative = (level?.currentGrams ?? 0) < 0

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger} />
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{strings.title(ingredient.name)}</DialogTitle>
          <DialogDescription>{strings.description}</DialogDescription>
        </DialogHeader>

        {levelsResult.isLoading ? (
          <Skeleton className="h-24 w-full" />
        ) : levelsResult.isError ? (
          <p role="alert" className="text-destructive text-sm">
            {strings.loadError}
          </p>
        ) : (
          <div className="flex flex-col gap-6">
            <div className="flex flex-col gap-4">
              <div className="flex flex-wrap items-end gap-6">
                <div className="grid gap-1">
                  <span className="text-muted-foreground text-sm">{strings.current}</span>
                  <span
                    className={`font-display text-4xl font-semibold tabular-nums ${negative ? "text-destructive" : ""}`}
                    data-testid="current-stock"
                  >
                    {formatGramsAsKg(level?.currentGrams ?? 0)}
                  </span>
                </div>
                {level?.lowStock && !negative && (
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
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <PurchaseEntryForm branchId={branchId} ingredients={[ingredient]} fixedIngredientId={ingredient.id} />
              <AdjustmentForm branchId={branchId} ingredients={[ingredient]} fixedIngredientId={ingredient.id} />
            </div>

            <MovementLedger
              branchId={branchId}
              ingredients={[ingredient]}
              initialIngredientId={ingredient.id}
              hideFilter
            />
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

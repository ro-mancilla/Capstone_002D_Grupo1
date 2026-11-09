import { useState } from "react"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { useQuery } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { movementsQuery } from "@/api/queries/inventory"
import type { IngredientDto, StockMovementKind } from "@/api/types"
import { formatGramsAsKg } from "@/lib/format"
import { t } from "@/lib/i18n"

const ALL_INGREDIENTS = "__all__"

const KIND_LABELS: Record<StockMovementKind, string> = {
  PURCHASE: t.admin.stockEntry.ledger.kindPurchase,
  MANUAL_ADJUSTMENT: t.admin.stockEntry.ledger.kindAdjustment,
  FORMULA_DECREMENT: t.admin.stockEntry.ledger.kindFormulaDecrement,
}

const DATE_FORMAT = new Intl.DateTimeFormat("es-CL", { dateStyle: "medium", timeStyle: "short" })

interface MovementLedgerProps {
  branchId: string
  ingredients: IngredientDto[]
  /** Preselects a single ingredient on mount -- the dashboard `stock` page's negative-stock
   * "drill-in to the movement ledger" (plan 07 §4/§11 item 8) reuses this component as-is by
   * passing the offending ingredient's id, rather than forking a second ledger view. */
  initialIngredientId?: string
  /** The ledger of one ingredient (opened from its row): no "all ingredients" filter. */
  hideFilter?: boolean
}

const PAGE_SIZE = 20

/** Movement ledger view per ingredient (plan 07 §5 row 4): paged list, kg display of signed
 * grams. Filtering to a single ingredient is optional -- "todos los insumos" reads the whole
 * branch ledger. */
export function MovementLedger({ branchId, ingredients, initialIngredientId, hideFilter }: MovementLedgerProps) {
  const strings = t.admin.stockEntry.ledger
  const [ingredientId, setIngredientId] = useState<string>(initialIngredientId ?? ALL_INGREDIENTS)
  const [limit, setLimit] = useState(PAGE_SIZE)

  const movementsResult = useQuery(
    movementsQuery({
      branchId,
      ingredientId: ingredientId === ALL_INGREDIENTS ? undefined : ingredientId,
      limit,
      offset: 0,
    })
  )

  const ingredientNameById = new Map(ingredients.map((i) => [i.id, i.name]))
  const movements = movementsResult.data ?? []
  const ingredientItems = [
    { value: ALL_INGREDIENTS, label: strings.allIngredients },
    ...ingredients.map((ingredient) => ({ value: ingredient.id, label: ingredient.name })),
  ]

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-display text-lg font-semibold">{strings.title}</h2>
        {!hideFilter && (
        <ChoiceSelect
          options={ingredientItems}
          value={ingredientId}
          onValueChange={(value) => {
            setIngredientId(value ?? ALL_INGREDIENTS)
            setLimit(PAGE_SIZE)
          }}
          className="w-56"
        />
        )}
      </div>

      {movementsResult.isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : movements.length === 0 ? (
        <p className="text-muted-foreground text-sm">{strings.empty}</p>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{strings.columns.date}</TableHead>
                <TableHead>{strings.columns.ingredient}</TableHead>
                <TableHead>{strings.columns.quantity}</TableHead>
                <TableHead>{strings.columns.kind}</TableHead>
                <TableHead>{strings.columns.note}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {movements.map((movement) => (
                <TableRow key={movement.id}>
                  <TableCell>{DATE_FORMAT.format(new Date(movement.occurredAt))}</TableCell>
                  <TableCell>
                    {ingredientNameById.get(movement.ingredientId) ?? t.common.unnamed}
                  </TableCell>
                  <TableCell className={movement.deltaGrams < 0 ? "text-destructive" : ""}>
                    {movement.deltaGrams > 0 ? "+" : ""}
                    {formatGramsAsKg(movement.deltaGrams)}
                  </TableCell>
                  <TableCell>{KIND_LABELS[movement.kind]}</TableCell>
                  <TableCell>{movement.note ?? "—"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {movements.length >= limit && (
            <Button variant="outline" onClick={() => setLimit((current) => current + PAGE_SIZE)}>
              {strings.loadMore}
            </Button>
          )}
        </>
      )}
    </div>
  )
}

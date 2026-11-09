import { useState } from "react"
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
import { productMovementsQuery } from "@/api/queries/inventory"
import type { ProductStockMovementKind } from "@/api/types"
import { formatUnits } from "@/lib/format"
import { t } from "@/lib/i18n"

const strings = t.admin.products.stock.ledger

const KIND_LABELS: Record<ProductStockMovementKind, string> = {
  OPENING_COUNT: strings.kindOpening,
  PURCHASE: t.admin.stockEntry.ledger.kindPurchase,
  MANUAL_ADJUSTMENT: t.admin.stockEntry.ledger.kindAdjustment,
}

const DATE_FORMAT = new Intl.DateTimeFormat("es-CL", { dateStyle: "medium", timeStyle: "short" })

const PAGE_SIZE = 20

interface ProductMovementLedgerProps {
  branchId: string
  productId: string
}

/** What was recorded by hand for one Almacén product in one branch: the opening count, purchases
 * and adjustments. Sales are not rows here -- they are netted at query time into the stock figure. */
export function ProductMovementLedger({ branchId, productId }: ProductMovementLedgerProps) {
  const [limit, setLimit] = useState(PAGE_SIZE)
  const movementsResult = useQuery(productMovementsQuery({ branchId, productId, limit, offset: 0 }))
  const movements = movementsResult.data ?? []

  return (
    <div className="flex flex-col gap-3">
      <h3 className="font-display text-base font-semibold">{strings.title}</h3>
      {movementsResult.isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : movements.length === 0 ? (
        <p className="text-muted-foreground text-sm">{strings.empty}</p>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{strings.columns.date}</TableHead>
                <TableHead>{strings.columns.quantity}</TableHead>
                <TableHead>{strings.columns.kind}</TableHead>
                <TableHead>{strings.columns.note}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {movements.map((movement) => (
                <TableRow key={movement.id}>
                  <TableCell>{DATE_FORMAT.format(new Date(movement.occurredAt))}</TableCell>
                  <TableCell className={movement.deltaUnits < 0 ? "text-destructive" : ""}>
                    {movement.deltaUnits > 0 ? "+" : ""}
                    {formatUnits(movement.deltaUnits)}
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

import { errorMessage } from "@/lib/apiErrors"
import { readOutcome } from "@/lib/readOutcome"
import { ModuleDisabledState } from "@/components/shared/ModuleDisabledState"
import { ReadFailure } from "@/components/shared/ReadFailure"
import { toast } from "sonner"
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Croissant, PlusIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { StatusBadge } from "@/components/shared/StatusBadge"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { productsQuery, useDeleteProduct } from "@/api/queries/catalog"
import { formulasQuery } from "@/api/queries/formulas"
import { ingredientsQuery, productStockLevelsQuery } from "@/api/queries/inventory"
import { SSE_POLLING_FALLBACK } from "@/api/queries/kpis"
import { can } from "@/lib/roles"
import { hasModule } from "@/lib/entitlements"
import { formatUnits } from "@/lib/format"
import type { ProductStockLevelDto } from "@/api/types"
import { useAuth } from "@/features/auth/AuthContext"
import { formatCurrencyMinor } from "@/lib/format"
import { t } from "@/lib/i18n"
import { FormulaEditorDialog } from "./FormulaEditorDialog"
import { ProductFormDialog } from "./ProductFormDialog"
import { ProductStockDialog } from "./ProductStockDialog"

/** `admin/products`: the one place a product is added, edited, seen and deleted, and where an
 * Almacén product's stock is read and recorded (opening count at creation, current stock per row,
 * purchases and adjustments from the row's *Stock* button). Create/edit via `ProductFormDialog`,
 * soft-delete (DELETE = `active=false`).
 *
 * A `BRANCH_MANAGER` reaches this page too, for the stock side only: catalog writes are a full-admin
 * permission server-side, so they get the list and the *Stock* buttons but not New/Edit/Delete.
 * Stock is per branch, so the page works in one branch at a time (a branch manager's own, or the
 * picker's for a manager of several). */
export function ProductsPage() {
  const { session } = useAuth()
  const currencyCode = session?.currencyCode ?? "CLP"
  const [search, setSearch] = useState("")
  const [includeInactive, setIncludeInactive] = useState(false)
  const productsResult = useQuery(productsQuery(includeInactive))
  const deleteProduct = useDeleteProduct()
  const canEditCatalog = can(session?.role, "MANAGE_FULL_ADMIN")
  const canAdjustStock = can(session?.role, "MANAGE_BRANCH_STOCK")
  const branches = session?.branches ?? []
  const canSelectBranch = can(session?.role, "SELECT_BRANCH") && branches.length > 1
  const [branchId, setBranchId] = useState<string>(session?.branchId ?? branches[0]?.id ?? "")
  const branchName = branches.find((branch) => branch.id === branchId)?.name
  const branchItems = branches.map((branch) => ({ value: branch.id, label: branch.name }))
  const levelsResult = useQuery({
    ...productStockLevelsQuery(branchId),
    enabled: Boolean(branchId),
    ...SSE_POLLING_FALLBACK,
  })
  const levelByProduct = new Map<string, ProductStockLevelDto>(
    (levelsResult.data ?? []).map((level) => [level.productId, level])
  )

  // A formula belongs to a Panadería product and is edited from its row. The formula reads sit
  // behind INVENTORY_FORMULAS, and the write needs catalog-write, so without both the button is
  // simply absent -- and a failed formula read must not take the whole product list with it, hence
  // these stay out of `readOutcome` below.
  const canEditFormulas = canEditCatalog && hasModule(session, "INVENTORY_FORMULAS")
  const formulasResult = useQuery({ ...formulasQuery(), enabled: canEditFormulas })
  const ingredientsResult = useQuery({ ...ingredientsQuery(false), enabled: canEditFormulas })
  const formulasByProduct = new Map((formulasResult.data ?? []).map((formula) => [formula.productId, formula]))
  const formulaReady = canEditFormulas && formulasResult.isSuccess && ingredientsResult.isSuccess

  const strings = t.admin.products
  const products = (productsResult.data ?? []).filter((product) =>
    product.name.toLowerCase().includes(search.toLowerCase())
  )

  async function handleDelete(id: string) {
    if (!window.confirm(strings.deleteConfirm)) return
    try {
      await deleteProduct.mutateAsync(id)
    } catch (error) {
      // Without this the rejection was unhandled and the row simply stayed put, giving the
      // manager no reason why (issue #3).
      toast.error(errorMessage(error))
    }
  }

  // A failed read must not read as an empty bakery (issue #11).
  const outcome = readOutcome(productsResult)
  if (outcome.status === "moduleDisabled") return <ModuleDisabledState />
  if (outcome.status === "failed") return <ReadFailure message={outcome.message} onRetry={outcome.retry} />
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="page-title">{strings.title}</h1>
        <div className="flex items-center gap-2">
          {canSelectBranch && (
            <ChoiceSelect
              options={branchItems}
              value={branchId}
              onValueChange={setBranchId}
              className="w-48"
              aria-label={strings.branchLabel}
            />
          )}
          {canEditCatalog && (
            <ProductFormDialog
              currencyCode={currencyCode}
              branchId={branchId || undefined}
              branchName={branchName}
              trigger={
                <Button>
                  <PlusIcon className="size-4" />
                  {strings.newProduct}
                </Button>
              }
            />
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={strings.searchPlaceholder}
          className="max-w-xs"
        />
        <label className="flex items-center gap-2 text-sm">
          <Switch checked={includeInactive} onCheckedChange={setIncludeInactive} />
          {strings.showInactive}
        </label>
      </div>

      {productsResult.isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : products.length === 0 ? (
        <p className="text-muted-foreground text-sm">{strings.empty}</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{strings.columns.name}</TableHead>
              <TableHead>{strings.columns.type}</TableHead>
              <TableHead>{strings.columns.soldBy}</TableHead>
              <TableHead>{strings.columns.price}</TableHead>
              <TableHead>{strings.columns.stock}</TableHead>
              <TableHead>{strings.columns.status}</TableHead>
              <TableHead className="text-right">{strings.columns.actions}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {products.map((product) => {
              const priceMinor =
                product.soldBy === "WEIGHT" ? product.pricePerKgMinor : product.priceMinor
              return (
                <TableRow key={product.id}>
                  <TableCell className="font-medium">
                    <span className="flex items-center gap-3">
                      {/* Lazy and `decoding="async"`: this table can run to a few hundred rows and
                          the photos are served from whatever host the tenant pointed at, so they
                          must never hold up the row they belong to. `onError` hides a broken
                          image rather than leaving the browser's torn-page icon in the column. */}
                      {product.imageUrl ? (
                        <img
                          src={product.imageUrl}
                          alt=""
                          loading="lazy"
                          decoding="async"
                          className="bg-muted size-10 shrink-0 rounded-md object-cover"
                          onError={(event) => {
                            event.currentTarget.style.visibility = "hidden"
                          }}
                        />
                      ) : (
                        <span
                          aria-hidden
                          className="bg-muted text-muted-foreground flex size-10 shrink-0 items-center justify-center rounded-md"
                        >
                          <Croissant className="size-4" />
                        </span>
                      )}
                      {product.name}
                    </span>
                  </TableCell>
                  <TableCell>{product.productType}</TableCell>
                  <TableCell>
                    {product.soldBy === "WEIGHT" ? strings.soldByWeight : strings.soldByUnit}
                  </TableCell>
                  <TableCell>
                    <span className="ds-price">
                      {priceMinor != null ? formatCurrencyMinor(priceMinor, currencyCode) : "—"}
                    </span>
                  </TableCell>
                  <TableCell>
                    <StockCell level={product.origin === "ALMACEN" ? levelByProduct.get(product.id) : undefined} />
                  </TableCell>
                  <TableCell>
                    <Badge variant={product.active ? "success" : "outline"}>
                      {product.active ? strings.statusActive : strings.statusInactive}
                    </Badge>
                  </TableCell>
                  <TableCell className="flex justify-end gap-2 text-right">
                    {product.origin === "ALMACEN" && branchId && (
                      <ProductStockDialog
                        product={product}
                        branchId={branchId}
                        canAdjust={canAdjustStock}
                        trigger={<Button variant="outline">{strings.stock.open}</Button>}
                      />
                    )}
                    {formulaReady && product.origin === "PANADERIA" && (
                      <FormulaEditorDialog
                        product={product}
                        formula={formulasByProduct.get(product.id)}
                        ingredients={ingredientsResult.data ?? []}
                        trigger={
                          <Button variant="outline">
                            {formulasByProduct.has(product.id)
                              ? t.admin.formulas.editFormula
                              : t.admin.formulas.newFormula}
                          </Button>
                        }
                      />
                    )}
                    {canEditCatalog && (
                      <ProductFormDialog
                        currencyCode={currencyCode}
                        product={product}
                        branchId={branchId || undefined}
                        branchName={branchName}
                        reorderLevel={levelByProduct.get(product.id)?.lowStockThresholdUnits}
                        trigger={<Button variant="outline">{t.common.edit}</Button>}
                      />
                    )}
                    {canEditCatalog && product.active && (
                      <Button variant="ghost" onClick={() => handleDelete(product.id)}>
                        {t.common.delete}
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

/** Current stock of an Almacén row, with the warning states beside it. A Panadería product has no
 * product stock, and an Almacén product whose level has not loaded shows a dash, never a made-up 0. */
function StockCell({ level }: { level: ProductStockLevelDto | undefined }) {
  const strings = t.admin.products.stock
  if (!level) return <span className="text-muted-foreground">{strings.notTracked}</span>
  const negative = level.currentUnits < 0
  return (
    <span className="flex flex-wrap items-center gap-2">
      <span className={`tabular-nums ${negative ? "text-destructive font-medium" : ""}`}>
        {formatUnits(level.currentUnits)}
      </span>
      {negative ? (
        <StatusBadge status="negative" />
      ) : level.currentUnits === 0 ? (
        // Same states as the till: out at exactly 0, low above 0 up to the reorder level.
        <StatusBadge status="outOfStock" />
      ) : (
        level.lowStock && <StatusBadge status="low" />
      )}
    </span>
  )
}

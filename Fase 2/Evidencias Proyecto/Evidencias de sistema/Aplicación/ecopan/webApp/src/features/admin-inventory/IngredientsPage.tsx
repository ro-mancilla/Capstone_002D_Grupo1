import { useState } from "react"
import { Link } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { PlusIcon } from "lucide-react"
import { readOutcome } from "@/lib/readOutcome"
import { ModuleDisabledState } from "@/components/shared/ModuleDisabledState"
import { ReadFailure } from "@/components/shared/ReadFailure"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { StatusBadge, type StockStatus } from "@/components/shared/StatusBadge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ingredientsQuery, stockLevelsQuery } from "@/api/queries/inventory"
import { SSE_POLLING_FALLBACK } from "@/api/queries/kpis"
import type { StockLevelDto } from "@/api/types"
import { useAuth } from "@/features/auth/AuthContext"
import { formatGramsAsKg } from "@/lib/format"
import { t } from "@/lib/i18n"
import { can } from "@/lib/roles"
import { IngredientFormDialog } from "./IngredientFormDialog"
import { IngredientStockDialog } from "./IngredientStockDialog"

/**
 * `admin/ingredients`: the one Insumos page. The list with current stock and status (low /
 * negative), ingredient create/edit with its reorder level, and -- from each row's *Stock* button --
 * purchases and adjustments. Inventory means ingredients only; Almacén product stock lives on
 * `admin/products`.
 *
 * A `BRANCH_MANAGER` reaches it for the stock side: ingredient writes are a full-admin permission
 * server-side, so they see the list and the *Stock* buttons but not New/Edit. Stock is per branch,
 * so the page works in one branch at a time (their own, or the picker's for a manager of several).
 * Deactivating an ingredient is the edit form's *Activo* switch -- there is no hard delete.
 */
export function IngredientsPage() {
  const { session } = useAuth()
  const strings = t.admin.ingredients
  const canEdit = can(session?.role, "MANAGE_FULL_ADMIN")
  const branches = session?.branches ?? []
  const canSelectBranch = can(session?.role, "SELECT_BRANCH") && branches.length > 1
  const [branchId, setBranchId] = useState<string>(session?.branchId ?? branches[0]?.id ?? "")
  const branchItems = branches.map((branch) => ({ value: branch.id, label: branch.name }))
  const [includeInactive, setIncludeInactive] = useState(true)

  const ingredientsResult = useQuery(ingredientsQuery(includeInactive))
  const levelsResult = useQuery({
    ...stockLevelsQuery(branchId),
    enabled: Boolean(branchId),
    ...SSE_POLLING_FALLBACK,
  })
  const ingredients = ingredientsResult.data ?? []
  const levelById = new Map<string, StockLevelDto>(
    (levelsResult.data ?? []).map((level) => [level.ingredientId, level])
  )

  // A failed read must not read as an empty bakery (issue #11).
  const outcome = readOutcome(ingredientsResult)
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
          {canEdit && (
            <IngredientFormDialog
              trigger={
                <Button>
                  <PlusIcon className="size-4" />
                  {strings.newIngredient}
                </Button>
              }
            />
          )}
        </div>
      </div>

      <label className="flex w-fit items-center gap-2 text-sm">
        <Switch checked={includeInactive} onCheckedChange={setIncludeInactive} />
        {strings.showInactive}
      </label>

      {ingredientsResult.isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : ingredients.length === 0 ? (
        <div className="text-muted-foreground flex flex-col gap-1 text-sm">
          <p>{strings.empty}</p>
          <p>{canEdit ? strings.emptyCanCreate : strings.emptyCannotCreate}</p>
          <p>
            {strings.formulasHint}{" "}
            <Link to="/catalog/products" className="text-foreground underline underline-offset-2">
              {strings.goToProducts}
            </Link>
          </p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{strings.columns.name}</TableHead>
              <TableHead>{strings.columns.stock}</TableHead>
              <TableHead>{strings.columns.reorderLevel}</TableHead>
              <TableHead>{strings.columns.status}</TableHead>
              <TableHead className="text-right">{strings.columns.actions}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {ingredients.map((ingredient) => {
              const level = levelById.get(ingredient.id)
              const negative = (level?.currentGrams ?? 0) < 0
              // An inactive ingredient is never flagged; a healthy one shows no badge at all.
              const status: StockStatus | null = !ingredient.active
                ? "inactive"
                : negative
                  ? "negative"
                  : level?.lowStock
                    ? "low"
                    : null
              const needsRestock = status === "negative" || status === "low"
              return (
                <TableRow key={ingredient.id}>
                  <TableCell className="font-medium">{ingredient.name}</TableCell>
                  <TableCell className={negative ? "text-destructive" : ""}>
                    {level ? (
                      <span className="tabular-nums">{formatGramsAsKg(level.currentGrams)}</span>
                    ) : (
                      <span className="text-muted-foreground">{strings.noStockYet}</span>
                    )}
                  </TableCell>
                  <TableCell>
                    {ingredient.lowStockThresholdGrams != null
                      ? formatGramsAsKg(ingredient.lowStockThresholdGrams)
                      : strings.noReorderLevel}
                  </TableCell>
                  <TableCell>
                    {status && <StatusBadge status={status} />}
                  </TableCell>
                  <TableCell className="flex justify-end gap-2 text-right">
                    {branchId && (
                      <IngredientStockDialog
                        ingredient={ingredient}
                        branchId={branchId}
                        trigger={
                          <Button variant={needsRestock ? "default" : "outline"}>
                            {needsRestock ? strings.registerPurchase : strings.stock.open}
                          </Button>
                        }
                      />
                    )}
                    {canEdit && (
                      <IngredientFormDialog
                        ingredient={ingredient}
                        trigger={<Button variant="outline">{t.common.edit}</Button>}
                      />
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

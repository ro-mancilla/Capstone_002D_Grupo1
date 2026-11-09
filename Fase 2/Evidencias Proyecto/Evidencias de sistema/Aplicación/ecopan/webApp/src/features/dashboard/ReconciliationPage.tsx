import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Download, Info, TriangleAlert } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ModuleDisabledState } from "@/components/shared/ModuleDisabledState"
import { ReadFailure } from "@/components/shared/ReadFailure"
import { reconciliationQuery } from "@/api/queries/inventory"
import type { ReconciliationRowDto } from "@/api/types"
import { useAuth } from "@/features/auth/AuthContext"
import { useIsMobile } from "@/hooks/use-mobile"
import { readOutcome } from "@/lib/readOutcome"
import { can } from "@/lib/roles"
import { t } from "@/lib/i18n"
import { cn } from "@/lib/utils"
import {
  dateRangeProblem,
  defaultRange,
  formatLedgerFixed,
  formatShortDay,
  formatSignedKg,
  presetDays,
  reconciliationCsv,
  reconciliationTotals,
  reportingToday,
  reviewFlag,
  type RangePreset,
  type ReviewFlag,
} from "./reconciliation"

function downloadCsv(filename: string, csv: string) {
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }))
  const link = document.createElement("a")
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

const PRESET_ORDER: RangePreset[] = ["thisMonth", "lastMonth", "last30", "custom"]

function flagText(flag: ReviewFlag): string {
  const strings = t.dashboard.reconciliation
  return flag.kind === "negativeClosing"
    ? strings.flagNegativeClosing
    : strings.flagAdjustment(formatSignedKg(flag.grams), flag.grams < 0)
}

/** A figure in the ledger: a bare grey 0 when there is nothing, fixed decimals otherwise. */
function Figure({ grams, className }: { grams: number; className?: string }) {
  return <span className={cn(grams === 0 && "text-ink-subtle", className)}>{formatLedgerFixed(grams)}</span>
}

/** `analytics/reconciliation` (HU-ANA-0006, `INVENTORY_FORMULAS`-gated): the ingredient ledger
 * over a closed day range -- opening, purchases, formula decrements, manual adjustments, closing.
 * Closing equals the current stock when the range ends today (and nothing is dated in the
 * future); a backdated entry synced later can change a past period. */
export function ReconciliationPage() {
  const { session } = useAuth()
  const strings = t.dashboard.reconciliation
  const isMobile = useIsMobile()
  const branches = session?.branches ?? []
  const canSelectBranch = can(session?.role, "SELECT_BRANCH")
  const [branchId, setBranchId] = useState<string>(session?.branchId ?? branches[0]?.id ?? "")
  const [preset, setPreset] = useState<RangePreset>("last30")
  const [range, setRange] = useState(() => defaultRange())

  const choosePreset = (next: RangePreset) => {
    setPreset(next)
    if (next !== "custom") setRange(presetDays(next, reportingToday()))
  }

  const problem = dateRangeProblem(range.from, range.to)
  const result = useQuery({
    ...reconciliationQuery({ branchId, from: range.from, to: range.to }),
    enabled: Boolean(branchId) && problem === null,
  })

  const outcome = readOutcome(result)
  const report = problem === null ? result.data : undefined
  const rows = report?.rows ?? []
  const totals = reconciliationTotals(rows, report?.batchCount ?? 0)
  // Label map for the branch trigger -- Base UI renders the raw value without it.
  const branchItems = branches.map((branch) => ({ value: branch.id, label: branch.name }))
  const problemMessage =
    problem === "incomplete"
      ? strings.incompleteRange
      : problem === "inverted"
        ? strings.invalidRange
        : problem === "tooLong"
          ? strings.rangeTooLong
          : null
  // The closing balance is today's stock only if the range reaches today.
  const endsBeforeToday = problem === null && range.to < reportingToday()

  const exportCsv = () => {
    const headers = strings.csvHeaders
    if (!report) return
    const csv = reconciliationCsv(
      report,
      [
        headers.ingredient,
        headers.opening,
        headers.purchases,
        headers.decrements,
        headers.adjustments,
        headers.closing,
        headers.batches,
      ],
      strings.total,
      strings.unnamedIngredient
    )
    downloadCsv(`${strings.csvFileName}_${range.from}_${range.to}.csv`, csv)
  }

  const rangeText =
    problem === null ? strings.rangeText(formatShortDay(range.from), formatShortDay(range.to)) : null

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <h1 className="page-title">{strings.title}</h1>
          <p className="text-ink-muted text-sm">{strings.description}</p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <Button type="button" variant="outline" disabled={rows.length === 0} onClick={exportCsv}>
            <Download className="size-4" aria-hidden="true" />
            {strings.exportCsv}
          </Button>
          <span className="text-ink-muted text-xs">{strings.csvHint}</span>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {canSelectBranch && branches.length > 1 && (
          <Select
            items={branchItems}
            value={branchId}
            onValueChange={(value) => setBranchId(value ?? branchId)}
          >
            <SelectTrigger className="w-56" aria-label={strings.branchLabel}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {branches.map((branch) => (
                <SelectItem key={branch.id} value={branch.id}>
                  {branch.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
        <div className="flex flex-wrap items-center gap-1" role="group" aria-label={strings.presetsLabel}>
          {PRESET_ORDER.map((value) => (
            <Button
              key={value}
              type="button"
              size="sm"
              variant={preset === value ? "default" : "outline"}
              aria-pressed={preset === value}
              onClick={() => choosePreset(value)}
            >
              {strings.presets[value]}
            </Button>
          ))}
        </div>
        {preset === "custom" ? (
          <>
            <label className="text-muted-foreground flex items-center gap-1 text-sm">
              {strings.fromLabel}
              <Input
                type="date"
                className="w-40"
                value={range.from}
                onChange={(event) => setRange((current) => ({ ...current, from: event.target.value }))}
              />
            </label>
            <label className="text-muted-foreground flex items-center gap-1 text-sm">
              {strings.toLabel}
              <Input
                type="date"
                className="w-40"
                value={range.to}
                onChange={(event) => setRange((current) => ({ ...current, to: event.target.value }))}
              />
            </label>
          </>
        ) : (
          rangeText && <span className="text-ink-muted text-sm tabular-nums">{rangeText}</span>
        )}
      </div>

      {endsBeforeToday && (
        <div
          role="note"
          className="bg-info-bg text-info border-info-line flex items-start gap-2 rounded-lg border px-4 py-3 text-sm"
        >
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          {strings.retroactiveNotice}
        </div>
      )}

      {problemMessage ? (
        <p className="text-destructive text-sm" role="alert">
          {problemMessage}
        </p>
      ) : outcome.status === "moduleDisabled" ? (
        <ModuleDisabledState />
      ) : outcome.status === "failed" ? (
        <ReadFailure message={outcome.message} onRetry={outcome.retry} />
      ) : outcome.status === "loading" ? (
        <Skeleton className="h-64 w-full" />
      ) : rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">{strings.empty}</p>
      ) : isMobile ? (
        <LedgerCards rows={rows} />
      ) : (
        <LedgerTable rows={rows} totals={totals} />
      )}

      {rows.length > 0 && <p className="text-ink-muted text-xs">{strings.caption}</p>}
    </div>
  )
}

function LedgerTable({
  rows,
  totals,
}: {
  rows: ReconciliationRowDto[]
  totals: ReturnType<typeof reconciliationTotals>
}) {
  const strings = t.dashboard.reconciliation
  const ops = strings.operators
  return (
    <div className="bg-card overflow-hidden rounded-[22px] border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>
              {strings.columns.ingredient}
              {strings.unitHeader}
            </TableHead>
            <TableHead className="text-right">{strings.columns.opening}</TableHead>
            <TableHead className="text-right">{strings.columns.purchases}</TableHead>
            <TableHead className="text-right">{strings.columns.decrements}</TableHead>
            <TableHead className="text-right">{strings.columns.adjustments}</TableHead>
            <TableHead className="text-right">{strings.columns.closing}</TableHead>
            <TableHead className="w-16 text-right">{strings.columns.batches}</TableHead>
          </TableRow>
          <TableRow aria-hidden="true" className="text-ink-muted font-mono text-xs">
            <TableHead />
            <TableHead />
            <TableHead className="text-right">{ops.plus}</TableHead>
            <TableHead className="text-right">{ops.minus}</TableHead>
            <TableHead className="text-right">{ops.plusMinus}</TableHead>
            <TableHead className="text-right">{ops.equals}</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => {
            const flag = reviewFlag(row)
            return (
              <TableRow key={row.ingredientId} className={cn("h-[52px]", flag && "bg-danger-bg hover:bg-danger-bg")}>
                <TableCell>
                  <div className="font-medium">{row.name || strings.unnamedIngredient}</div>
                  {flag && (
                    <div className="text-danger mt-0.5 flex items-center gap-1 text-xs font-medium">
                      <TriangleAlert className="size-3" aria-hidden="true" />
                      {strings.review} · {flagText(flag)}
                    </div>
                  )}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  <Figure grams={row.openingGrams} />
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  <Figure grams={row.purchasedGrams} />
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  <Figure grams={row.decrementedGrams} />
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  <Figure grams={row.adjustedGrams} />
                </TableCell>
                <TableCell className="text-right font-semibold tabular-nums">
                  <Figure grams={row.closingGrams} className={row.closingGrams < 0 ? "text-danger" : undefined} />
                </TableCell>
                <TableCell className="text-right tabular-nums">{row.batchCount}</TableCell>
              </TableRow>
            )
          })}
        </TableBody>
        <TableFooter>
          <TableRow className="bg-flour-inset font-semibold">
            <TableCell>{strings.total}</TableCell>
            <TableCell className="text-right tabular-nums">
              <Figure grams={totals.openingGrams} />
            </TableCell>
            <TableCell className="text-right tabular-nums">
              <Figure grams={totals.purchasedGrams} />
            </TableCell>
            <TableCell className="text-right tabular-nums">
              <Figure grams={totals.decrementedGrams} />
            </TableCell>
            <TableCell className="text-right tabular-nums">
              <Figure grams={totals.adjustedGrams} />
            </TableCell>
            <TableCell className="text-right tabular-nums">
              <Figure grams={totals.closingGrams} className={totals.closingGrams < 0 ? "text-danger" : undefined} />
            </TableCell>
            <TableCell className="text-right tabular-nums">{totals.batchCount}</TableCell>
          </TableRow>
        </TableFooter>
      </Table>
    </div>
  )
}

/** Phone layout (design 3c): one card per ingredient, the equation on one line. */
function LedgerCards({ rows }: { rows: ReconciliationRowDto[] }) {
  const strings = t.dashboard.reconciliation
  const ops = strings.operators
  return (
    <ul className="flex flex-col gap-3">
      {rows.map((row) => {
        const flag = reviewFlag(row)
        return (
          <li
            key={row.ingredientId}
            className={cn("bg-card rounded-2xl border px-4 py-3.5", flag && "bg-danger-bg")}
          >
            <div className="flex items-baseline justify-between gap-2">
              <span className="font-semibold">{row.name || strings.unnamedIngredient}</span>
              <span className={cn("text-lg font-semibold tabular-nums", row.closingGrams < 0 && "text-danger")}>
                {formatLedgerFixed(row.closingGrams)} kg
              </span>
            </div>
            <p className="text-ink-muted mt-1 text-[13px] tabular-nums">
              {formatLedgerFixed(row.openingGrams)} {ops.plus} {formatLedgerFixed(row.purchasedGrams)} {ops.minus}{" "}
              {formatLedgerFixed(Math.abs(row.decrementedGrams))} {ops.plusMinus} {formatLedgerFixed(row.adjustedGrams)}
            </p>
            {flag && (
              <p className="text-danger mt-1 flex items-center gap-1 text-xs font-medium">
                <TriangleAlert className="size-3" aria-hidden="true" />
                {strings.review} · {flagText(flag)}
              </p>
            )}
          </li>
        )
      })}
    </ul>
  )
}

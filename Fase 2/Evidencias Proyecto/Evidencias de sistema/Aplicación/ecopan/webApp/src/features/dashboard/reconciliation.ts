import type { ReconciliationReportDto, ReconciliationRowDto } from "@/api/types"

export type ReconciliationFigures = Omit<ReconciliationRowDto, "ingredientId" | "name">

/** Column totals. Gram columns sum across ingredients; `batchCount` is the report's period-level
 * distinct-batch count passed in, never a sum of the rows (one batch decrements many ingredients). */
export function reconciliationTotals(
  rows: ReconciliationRowDto[],
  batchCount: number
): ReconciliationFigures {
  return rows.reduce<ReconciliationFigures>(
    (sum, row) => ({
      openingGrams: sum.openingGrams + row.openingGrams,
      purchasedGrams: sum.purchasedGrams + row.purchasedGrams,
      decrementedGrams: sum.decrementedGrams + row.decrementedGrams,
      adjustedGrams: sum.adjustedGrams + row.adjustedGrams,
      closingGrams: sum.closingGrams + row.closingGrams,
      batchCount,
    }),
    {
      openingGrams: 0,
      purchasedGrams: 0,
      decrementedGrams: 0,
      adjustedGrams: 0,
      closingGrams: 0,
      batchCount,
    }
  )
}

const LEDGER_KG = new Intl.NumberFormat("es-CL", {
  minimumFractionDigits: 0,
  maximumFractionDigits: 3,
})

/** Kilos to the gram. `formatGramsAsKg` rounds to 2 decimals, which would hide a 1 g difference
 * -- the one thing a reconciliation must not do. */
export function formatLedgerKg(grams: number): string {
  return `${LEDGER_KG.format(grams / 1000)} kg`
}

const LEDGER_FIXED = new Intl.NumberFormat("es-CL", { minimumFractionDigits: 3, maximumFractionDigits: 3 })
const MINUS = "−"

/** The ledger table's cell: always three decimals so the commas line up (a balance book), a real
 * minus sign, and a bare "0" for nothing. */
export function formatLedgerFixed(grams: number): string {
  if (grams === 0) return "0"
  const text = LEDGER_FIXED.format(Math.abs(grams) / 1000)
  return grams < 0 ? `${MINUS}${text}` : text
}

/** An adjustment bigger than this share of what was bought is worth a look. */
export const ADJUSTMENT_REVIEW_SHARE = 0.05

export type ReviewFlag = { kind: "negativeClosing" } | { kind: "adjustment"; grams: number }

/** Why a row needs a look (design 3b), or `null`. A negative closing balance wins over a large
 * adjustment. The adjustment rule needs purchases to compare against, so it never fires without. */
export function reviewFlag(row: ReconciliationRowDto): ReviewFlag | null {
  if (row.closingGrams < 0) return { kind: "negativeClosing" }
  if (row.purchasedGrams > 0 && Math.abs(row.adjustedGrams) > row.purchasedGrams * ADJUSTMENT_REVIEW_SHARE) {
    return { kind: "adjustment", grams: row.adjustedGrams }
  }
  return null
}

/** Kilos trimmed and signed, e.g. "−12,5" -- for sentences, unlike the table's fixed decimals. */
export function formatSignedKg(grams: number): string {
  const text = LEDGER_KG.format(Math.abs(grams) / 1000)
  return grams < 0 ? `${MINUS}${text}` : `+${text}`
}

export type RangePreset = "thisMonth" | "lastMonth" | "last30" | "custom"

/** `{ from, to }` (ISO days) of a quick range, relative to the reporting-timezone `today`. */
export function presetDays(preset: Exclude<RangePreset, "custom">, today: string): { from: string; to: string } {
  if (preset === "last30") return { from: addDays(today, -(DEFAULT_RANGE_DAYS - 1)), to: today }
  const firstOfMonth = `${today.slice(0, 8)}01`
  if (preset === "thisMonth") return { from: firstOfMonth, to: today }
  const lastOfPrevious = addDays(firstOfMonth, -1)
  return { from: `${lastOfPrevious.slice(0, 8)}01`, to: lastOfPrevious }
}

const SHORT_DATE = new Intl.DateTimeFormat("es-CL", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" })

/** "8 sep 2026" for an ISO day. */
export function formatShortDay(isoDay: string): string {
  return SHORT_DATE.format(new Date(`${isoDay}T00:00:00Z`)).replace(/\./g, "").replace("sept", "sep")
}

/** A spreadsheet runs a cell that starts with one of these as a formula (CSV injection). */
const FORMULA_TRIGGER = /^[=+\-@\t\r]/

function csvCell(value: string | number): string {
  // Numbers are the report's own figures and stay raw (a negative balance must remain a number);
  // only text -- an ingredient name anyone with catalog access can choose -- can carry a formula.
  const text =
    typeof value === "string" && FORMULA_TRIGGER.test(value) ? `'${value}` : String(value)
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** CSV in exact integer grams (no locale decimal separators to trip a spreadsheet), CRLF rows,
 * and a leading BOM so Excel reads the accents in ingredient names as UTF-8. */
export function reconciliationCsv(
  report: ReconciliationReportDto,
  headers: string[],
  totalLabel: string,
  unnamedLabel: string
): string {
  const { rows } = report
  const line = (cells: (string | number)[]) => cells.map(csvCell).join(",")
  const figures = (label: string, f: ReconciliationFigures) =>
    line([
      label,
      f.openingGrams,
      f.purchasedGrams,
      f.decrementedGrams,
      f.adjustedGrams,
      f.closingGrams,
      f.batchCount,
    ])
  const body = rows.map((row) => figures(row.name || unnamedLabel, row))
  return `﻿${[line(headers), ...body, figures(totalLabel, reconciliationTotals(rows, report.batchCount))].join("\r\n")}`
}

/** The deployment's reporting timezone (`reporting.tz` on the backend, default the same). The
 * server reads `from`/`to` as days in this zone, so "today" for the default range must be too --
 * the viewer's own clock would put a late-evening Santiago user, or someone abroad, a day off. */
const REPORTING_TZ = "America/Santiago"
const DEFAULT_RANGE_DAYS = 30
export const MAX_RANGE_DAYS = 366

/** `YYYY-MM-DD` of the reporting-timezone calendar day containing `now`. */
export function reportingToday(now: Date = new Date()): string {
  // `en-CA` formats as ISO `YYYY-MM-DD`.
  return new Intl.DateTimeFormat("en-CA", { timeZone: REPORTING_TZ }).format(now)
}

function dayNumber(isoDate: string): number {
  const [year, month, day] = isoDate.split("-").map(Number)
  return Date.UTC(year, month - 1, day) / 86_400_000
}

function addDays(isoDate: string, days: number): string {
  return new Date((dayNumber(isoDate) + days) * 86_400_000).toISOString().slice(0, 10)
}

export function defaultRange(now: Date = new Date()): { from: string; to: string } {
  const to = reportingToday(now)
  return { from: addDays(to, -(DEFAULT_RANGE_DAYS - 1)), to }
}

export type DateRangeProblem = "incomplete" | "inverted" | "tooLong"

/** Why `[from, to]` cannot be requested, or `null` if it can. An emptied date input is not the
 * same mistake as swapped dates, and the page says which. */
export function dateRangeProblem(from: string, to: string): DateRangeProblem | null {
  if (!from || !to) return "incomplete"
  if (from > to) return "inverted"
  if (dayNumber(to) - dayNumber(from) + 1 > MAX_RANGE_DAYS) return "tooLong"
  return null
}

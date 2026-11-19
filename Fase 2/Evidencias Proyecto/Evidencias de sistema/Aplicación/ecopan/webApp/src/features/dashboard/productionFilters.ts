import { useState } from "react"
import { addDays, endOfReportingDay, reportingDate, startOfReportingDay } from "@/lib/reportingDay"
import type { DashboardFilters, DateRangePreset } from "./filters"

export interface CustomDays {
  fromDay: string
  toDay: string
}

const PRESET_LENGTH = { today: 1, "7d": 7, "30d": 30 } as const

/**
 * The epoch-millis `{ from, to }` the K6 request sends, as whole days of the reporting timezone
 * (`lib/reportingDay`): from the start of the first day to the *end* of the last.
 *
 * `to` is the end of the day, not "now" -- the shared `useDashboardFilters` memoises its range at
 * mount, so a `to` of "now" would freeze and batches logged afterwards would never show even
 * though the page polls. A day-end bound stays correct for as long as the page is open.
 */
export function resolveProductionRange(
  preset: DateRangePreset,
  custom: CustomDays,
  now: number
): { from: number; to: number } {
  const { fromDay, toDay } = resolveDays(preset, custom, now)
  return { from: startOfReportingDay(fromDay), to: endOfReportingDay(toDay) }
}

/** The same number of whole days immediately before the chosen range -- what the KPI cards and the
 * bars' tick marks compare against ("frente a los 7 días anteriores"). */
export function resolvePreviousRange(
  preset: DateRangePreset,
  custom: CustomDays,
  now: number
): { from: number; to: number } {
  const { fromDay, toDay } = resolveDays(preset, custom, now)
  const length = dayCount(fromDay, toDay)
  const prevTo = addDays(fromDay, -1)
  return { from: startOfReportingDay(addDays(prevTo, 1 - length)), to: endOfReportingDay(prevTo) }
}

function dayCount(fromDay: string, toDay: string): number {
  const ms = Date.parse(`${toDay}T00:00:00Z`) - Date.parse(`${fromDay}T00:00:00Z`)
  return Math.max(1, Math.round(ms / 86_400_000) + 1)
}

function resolveDays(preset: DateRangePreset, custom: CustomDays, now: number): CustomDays {
  if (preset === "custom") return custom
  const today = reportingDate(now)
  return { fromDay: addDays(today, 1 - PRESET_LENGTH[preset]), toDay: today }
}

/** Noon UTC on `day`: both the UTC and the browser-local date of this instant are `day` in every
 * timezone from UTC-12 to UTC+11, which is what the date inputs read back. */
function noonUtc(day: string): number {
  return Date.parse(`${day}T12:00:00Z`)
}

/** `YYYY-MM-DD` of a browser-local instant -- what a `<input type="date">` hands back. */
function localDay(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * Filter state for the production page: the same shape `DashboardFilterBar` expects, but dates
 * are reporting-timezone days. `filters.from`/`to` are noon UTC on the first/last day, which the bar
 * only uses to show the date inputs; the request itself uses `range`.
 */
export function useProductionFilters(defaultBranchId?: string, initialPreset: DateRangePreset = "7d") {
  const [branchId, setBranchId] = useState<string | undefined>(defaultBranchId)
  const [preset, setPreset] = useState<DateRangePreset>(initialPreset)
  const [custom, setCustom] = useState<CustomDays>(() => resolveDays(initialPreset, { fromDay: "", toDay: "" }, Date.now()))

  // Not memoised on purpose: "today" must be re-read on each render, not frozen at mount.
  const now = Date.now()
  const days = resolveDays(preset, custom, now)
  const filters: DashboardFilters = {
    branchId,
    preset,
    from: noonUtc(days.fromDay),
    to: noonUtc(days.toDay),
  }

  return {
    filters,
    range: resolveProductionRange(preset, custom, now),
    previousRange: resolvePreviousRange(preset, custom, now),
    dayCount: dayCount(days.fromDay, days.toDay),
    setBranchId,
    setPreset: (next: DateRangePreset) => {
      // Entering "custom" starts from what was on screen, not from a stale earlier custom range.
      if (next === "custom") setCustom(days)
      setPreset(next)
    },
    setCustomRange: (from: number, to: number) => setCustom({ fromDay: localDay(from), toDay: localDay(to) }),
  }
}

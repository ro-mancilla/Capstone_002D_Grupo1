import { useMemo, useState } from "react"

export type DateRangePreset = "today" | "7d" | "30d" | "custom"

export interface DashboardFilters {
  /** `undefined` = all branches (GM only, plan 07 §4). */
  branchId?: string
  preset: DateRangePreset
  from: number
  to: number
}

function startOfDay(date: Date): Date {
  const copy = new Date(date)
  copy.setHours(0, 0, 0, 0)
  return copy
}

function daysAgo(days: number, now: Date): Date {
  return new Date(now.getTime() - days * 86_400_000)
}

/** Epoch-millis `{ from, to }` for a preset, matching the backend's `from`/`to` query params
 * (epoch millis, `AnalyticsRouter.kt`'s `rangeStart`/`rangeEnd`). `to` is "now" -- the range is
 * always up-to-the-minute, never end-of-day, so "today" includes sales that happen after the
 * page was opened once refetched. */
export function presetRange(preset: Exclude<DateRangePreset, "custom">, now: Date = new Date()): {
  from: number
  to: number
} {
  const to = now.getTime()
  switch (preset) {
    case "today":
      return { from: startOfDay(now).getTime(), to }
    case "7d":
      return { from: startOfDay(daysAgo(6, now)).getTime(), to }
    case "30d":
      return { from: startOfDay(daysAgo(29, now)).getTime(), to }
  }
}

/**
 * Global dashboard filters (plan 07 §4: "branch (or 'all' for GM), date range presets"). One hook
 * shared by every `features/dashboard/*` page rather than each page hand-rolling its own
 * branch/date state -- the filter *bar component* (`DashboardFilterBar`) is the other half of
 * this reuse (plan 07 §11 item 8's "one implementation reused across these pages").
 */
export function useDashboardFilters(defaultBranchId?: string) {
  const [branchId, setBranchId] = useState<string | undefined>(defaultBranchId)
  const [preset, setPreset] = useState<DateRangePreset>("7d")
  const [customRange, setCustomRange] = useState(() => presetRange("7d"))

  const filters: DashboardFilters = useMemo(() => {
    if (preset === "custom") return { branchId, preset, ...customRange }
    return { branchId, preset, ...presetRange(preset) }
  }, [branchId, preset, customRange])

  return {
    filters,
    setBranchId,
    setPreset,
    setCustomRange: (from: number, to: number) => setCustomRange({ from, to }),
  }
}

/** `YYYY-MM-DD` for every day in `[from, to]` inclusive -- the x-axis tick set every chart on
 * these pages renders against, so a day with zero rows still gets a tick (plan 07 §10 AC). */
export function enumerateDays(from: number, to: number): string[] {
  const days: string[] = []
  const cursor = startOfDay(new Date(from))
  const end = startOfDay(new Date(to))
  while (cursor.getTime() <= end.getTime()) {
    days.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return days
}

/** Fills every day in `[from, to]` with a zero-valued row when the source data has no entry for
 * it (plan 07 §10 AC: "empty-day gaps rendered as zero/empty rather than silently skipped").
 * `dateOf` extracts the `YYYY-MM-DD` key from a source row; `zero` builds the placeholder row for
 * a day with no data at all. Rows for the same day are summed via `merge`, not overwritten --
 * multi-branch "all branches" queries return one row per (day, branch).
 */
export function fillDateRange<T extends { date: string }>(
  from: number,
  to: number,
  rows: T[],
  zero: (date: string) => T,
  merge: (accumulated: T, row: T) => T
): T[] {
  const byDate = new Map<string, T>()
  for (const row of rows) {
    const existing = byDate.get(row.date)
    byDate.set(row.date, existing ? merge(existing, row) : row)
  }

  const result: T[] = []
  const cursor = startOfDay(new Date(from))
  const end = startOfDay(new Date(to))
  while (cursor.getTime() <= end.getTime()) {
    const key = cursor.toISOString().slice(0, 10)
    result.push(byDate.get(key) ?? zero(key))
    cursor.setDate(cursor.getDate() + 1)
  }
  return result
}

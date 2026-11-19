import type { WasteEstimateDto } from "@/api/types"

export interface WasteDayTotal {
  date: string
  wasteGrams: number
}

/** Estimated waste per day, summed over product types. Rows the backend marks not comparable
 * (`estimatedWasteGrams === null`, a unit-sold type) add nothing: counting them as zero would
 * claim "no waste" about something that was never measured. Sorted by date. */
export function wasteByDay(rows: WasteEstimateDto[]): WasteDayTotal[] {
  const byDate = new Map<string, number>()
  for (const row of rows) {
    if (row.estimatedWasteGrams === null) continue
    byDate.set(row.date, (byDate.get(row.date) ?? 0) + row.estimatedWasteGrams)
  }
  return [...byDate.entries()]
    .map(([date, wasteGrams]) => ({ date, wasteGrams }))
    .sort((a, b) => a.date.localeCompare(b.date))
}

/** The period's estimated waste, with the same exclusion as [wasteByDay]. */
export function totalWasteGrams(rows: WasteEstimateDto[]): number {
  return rows.reduce((sum, row) => sum + (row.estimatedWasteGrams ?? 0), 0)
}

/**
 * Calendar days in the bakery's reporting timezone.
 *
 * The backend buckets sales, batches and forecasts into days of `reporting.tz` (default
 * `America/Santiago`, `AnalyticsServiceImpl`), so a "day" filter built from the *browser's*
 * midnight selects a different 24 hours whenever the browser is elsewhere. Pages that filter by
 * day ask for the bakery's day instead. There is no endpoint that exposes the configured zone, so
 * this mirrors the backend default.
 */
export const REPORTING_TZ = "America/Santiago"

const PARTS = new Intl.DateTimeFormat("en-CA", {
  timeZone: REPORTING_TZ,
  hourCycle: "h23",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
})

function zonedParts(ms: number) {
  const values: Record<string, number> = {}
  for (const part of PARTS.formatToParts(new Date(ms))) {
    if (part.type !== "literal") values[part.type] = Number(part.value)
  }
  return values
}

/** `YYYY-MM-DD` of the reporting-timezone day containing the instant. */
export function reportingDate(ms: number): string {
  const { year, month, day } = zonedParts(ms)
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`
}

function parseDate(date: string): [number, number, number] {
  const [year, month, day] = date.split("-").map(Number)
  return [year, month, day]
}

/** `date` shifted by whole calendar days (no timezone involved). */
export function addDays(date: string, days: number): string {
  const [year, month, day] = parseDate(date)
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10)
}

/** Epoch millis of 00:00 on `date` in the reporting timezone. */
export function startOfReportingDay(date: string): number {
  const [year, month, day] = parseDate(date)
  const wallClockAsUtc = Date.UTC(year, month - 1, day)
  // The zone's offset depends on the instant being asked about, so converge on it: guess UTC,
  // read the zone's wall clock there, correct by the difference, repeat.
  let guess = wallClockAsUtc
  for (let i = 0; i < 3; i++) {
    const p = zonedParts(guess)
    const shownAsUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second)
    guess += wallClockAsUtc - shownAsUtc
  }
  return guess
}

/** Epoch millis of the last millisecond of `date` in the reporting timezone. */
export function endOfReportingDay(date: string): number {
  return startOfReportingDay(addDays(date, 1)) - 1
}

/** The `count` reporting-zone `yyyy-MM-dd` days ending on the day containing `endMs`, oldest first. */
export function reportingDays(endMs: number, count: number): string[] {
  const last = reportingDate(endMs)
  return Array.from({ length: count }, (_, i) => addDays(last, i - (count - 1)))
}

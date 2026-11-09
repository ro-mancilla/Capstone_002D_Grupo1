/**
 * The reporting timezone: the one the backend buckets analytics and forecast days in
 * (`reporting.tz` / `REPORTING_TZ`, default `America/Santiago`). "Today" on the forecast page is
 * this zone's calendar day, never the browser's, so a manager travelling (or a UTC test runner)
 * sees the same days the backend scored. Keep in step with the backend default.
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

function partsAt(epochMs: number) {
  const out: Record<string, number> = {}
  for (const part of PARTS.formatToParts(epochMs)) {
    if (part.type !== "literal") out[part.type] = Number(part.value)
  }
  return out as Record<"year" | "month" | "day" | "hour" | "minute" | "second", number>
}

/** Offset of the reporting zone from UTC at [epochMs], in millis (negative west of Greenwich). */
function offsetAt(epochMs: number): number {
  const p = partsAt(epochMs)
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second)
  return asUtc - Math.floor(epochMs / 1000) * 1000
}

/** `yyyy-MM-dd` of the reporting-zone calendar day containing [epochMs]. */
export function reportingDate(epochMs: number): string {
  const p = partsAt(epochMs)
  return `${p.year}-${String(p.month).padStart(2, "0")}-${String(p.day).padStart(2, "0")}`
}

/**
 * Epoch millis of 00:00 in the reporting zone on the day containing [epochMs], shifted by
 * [dayOffset] *calendar* days (not 24 h multiples: a DST change makes a day 23 or 25 hours long).
 */
export function reportingDayStart(epochMs: number, dayOffset = 0): number {
  const p = partsAt(epochMs)
  const midnightAsUtc = Date.UTC(p.year, p.month - 1, p.day + dayOffset)
  // The offset can differ at the target instant (DST), so iterate it to a fixed point.
  let guess = midnightAsUtc - offsetAt(epochMs)
  for (let i = 0; i < 2; i++) guess = midnightAsUtc - offsetAt(guess)
  // Santiago springs forward at midnight, so 00:00 can simply not exist: the day then starts at
  // its first real instant (01:00), and the fixed point above lands an hour into the day before.
  const target = new Date(midnightAsUtc).toISOString().slice(0, 10)
  while (reportingDate(guess) < target) guess += 3_600_000
  return guess
}

import { REPORTING_TZ, reportingDate } from "@/lib/reportingTime"
import { t } from "@/lib/i18n"

const TIME = new Intl.DateTimeFormat("es-CL", { timeZone: REPORTING_TZ, hour: "2-digit", minute: "2-digit", hourCycle: "h23" })
const DAY_MONTH = new Intl.DateTimeFormat("es-CL", { timeZone: REPORTING_TZ, day: "numeric", month: "short" })
const WEEKDAY = new Intl.DateTimeFormat("es-CL", { timeZone: REPORTING_TZ, weekday: "long" })
const FULL = new Intl.DateTimeFormat("es-CL", { timeZone: REPORTING_TZ, day: "numeric", month: "short", year: "numeric" })

const dayMs = 86_400_000

/** "jueves 8 oct" -- the reporting-zone day containing [ms]. */
export function longDayLabel(ms: number): string {
  return `${WEEKDAY.format(ms)} ${DAY_MONTH.format(ms).replace(".", "")}`
}

/** "7 oct 2026, 06:15" for table footers. */
export function fullStamp(ms: number): string {
  return `${FULL.format(ms).replace(/\./g, "")}, ${TIME.format(ms)}`
}

/** Freshness of a forecast: "hoy, 06:15" / "ayer, 00:05" / "5 oct, 00:05". `stale` once it is more than a day old. */
export function freshness(generatedAt: number, now: number) {
  const strings = t.dashboard.forecast
  const time = TIME.format(generatedAt)
  const today = reportingDate(now)
  const yesterday = reportingDate(now - dayMs)
  const day = reportingDate(generatedAt)
  const label =
    day === today
      ? strings.stampToday(time)
      : day === yesterday
        ? strings.stampYesterday(time)
        : strings.stampOlder(DAY_MONTH.format(generatedAt).replace(".", ""), time)
  return { label, stale: day !== today && day !== yesterday }
}

/** The 14 `yyyy-MM-dd` days of the chart: 6 back, today, 7 ahead. */
export function chartDays(today: string): string[] {
  const base = Date.parse(`${today}T12:00:00Z`)
  return Array.from({ length: 14 }, (_, i) => new Date(base + (i - 6) * dayMs).toISOString().slice(0, 10))
}

const INITIALS = ["D", "L", "M", "X", "J", "V", "S"]

/** Axis label: weekday initial + day number ("L 5"). */
export function axisLabel(date: string): { weekday: string; day: number } {
  const d = new Date(`${date}T12:00:00Z`)
  return { weekday: INITIALS[d.getUTCDay()], day: d.getUTCDate() }
}

/**
 * Unit conversions + `Intl`-backed formatters (plan 07 §2/§5/§8). All wire values are integers
 * (minor currency units, grams, basis points); every conversion to/from a human-entered decimal
 * lives here, in exactly one place per direction, so no form hand-rolls its own math.
 *
 * CLP is zero-decimal -- never blindly divide by 100. `Intl.NumberFormat`'s `currencyDisplay`
 * fraction digits already account for this per currency, so `minorToDecimal`/`decimalToMinor`
 * derive the scale from `Intl.NumberFormat(...).resolvedOptions().maximumFractionDigits` instead
 * of hardcoding a divisor.
 */

const LOCALE = "es-CL"

function fractionDigitsFor(currencyCode: string): number {
  const parts = new Intl.NumberFormat(LOCALE, { style: "currency", currency: currencyCode })
  return parts.resolvedOptions().maximumFractionDigits ?? 2
}

/** Minor units (integer, wire) -> decimal (display/entry), e.g. CLP 1500 -> 1500, USD 1500 ->
 * 15.00's underlying number 15. */
export function minorToDecimal(minor: number, currencyCode: string): number {
  const digits = fractionDigitsFor(currencyCode)
  return minor / 10 ** digits
}

/** Decimal (display/entry) -> minor units (integer, wire). Rounds to guard against float drift
 * (e.g. 15.00 * 100 producing 1499.9999999999998). */
export function decimalToMinor(decimal: number, currencyCode: string): number {
  const digits = fractionDigitsFor(currencyCode)
  return Math.round(decimal * 10 ** digits)
}

/** `amountMinor` (integer) -> formatted currency string, e.g. CLP 1500 -> "$1.500", USD 1500 ->
 * "$15.00". Never divides by a hardcoded 100 -- goes through `minorToDecimal` above. */
export function formatCurrencyMinor(minor: number, currencyCode: string): string {
  const decimal = minorToDecimal(minor, currencyCode)
  return new Intl.NumberFormat(LOCALE, { style: "currency", currency: currencyCode }).format(
    decimal
  )
}

// One stated precision per context. Weights are whole grams on the wire, so three decimals of kg
// is exact: stock, ledger, deduction previews and totals all use it, and a preview's lines sum to
// its total with nothing lost. Forecast quantities are estimates, so they read at 0,1 kg.
const KG_EXACT = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 3,
  maximumFractionDigits: 3,
})
const KG_TENTHS = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
})

/** grams (integer, wire) -> kg (decimal, display/entry). */
export function gramsToKg(grams: number): number {
  return grams / 1000
}

/** kg (decimal, display/entry) -> grams (integer, wire). Rounds -- grams is the smallest unit
 * the wire format carries. */
export function kgToGrams(kg: number): number {
  return Math.round(kg * 1000)
}

/** grams -> "X,XXX kg" (es-CL decimal comma): the exact gram, never rounded or truncated. */
export function formatGramsAsKg(grams: number): string {
  return `${KG_EXACT.format(gramsToKg(grams))} kg`
}

/** grams -> "X,X kg": a forecast, rounded half away from zero to 0,1 kg (see [t.dashboard.forecast.suggested.rounding]). */
export function formatSuggestedKg(grams: number): string {
  return `${KG_TENTHS.format(Math.sign(grams) * Math.round(Math.abs(grams) / 100) / 10)} kg`
}

/**
 * Formula lines stay stored as basis points relative to flour (10000 bp = flour itself), but the
 * bakery *reads and writes* them as "kilos per kilo of flour": 6500 bp -> 0.65 kg of water per
 * kg of flour, 200 bp -> 0.02 kg of salt. Same number the baker's percentage expressed, one
 * unit the whole system already uses (kg) instead of a second one (%) -- and the editor's
 * preview can then be "for this much flour, use this much of everything else".
 */
export function bpToKgPerKgFlour(bp: number): number {
  return bp / 10000
}

/** kg per kg of flour (decimal, entry) -> basis points (integer, wire). 1 bp = 0.1 g per kg of
 * flour, so 4 decimals round-trip exactly; anything finer is rounded. */
export function kgPerKgFlourToBp(kgPerKgFlour: number): number {
  return Math.round(kgPerKgFlour * 10000)
}

const UNIT_COUNT = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 0 })

/** whole units (integer, wire) -> "24 u." -- resold products are counted, never weighed. */
export function formatUnits(units: number): string {
  return `${UNIT_COUNT.format(units)} u.`
}

const PERCENT = new Intl.NumberFormat(LOCALE, { style: "percent", maximumFractionDigits: 1 })

/** fraction -> "12,3 %" (es-CL). */
export function formatPercent(fraction: number): string {
  return PERCENT.format(fraction)
}

const DAY_LABEL = new Intl.DateTimeFormat(LOCALE, { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" })

const SHORT_DAY_LABEL = new Intl.DateTimeFormat(LOCALE, { day: "numeric", month: "short", timeZone: "UTC" })

/** `yyyy-MM-dd` -> "8 oct": the compact axis tick, short enough for seven of them on a phone. */
export function formatShortDayLabel(date: string): string {
  const parsed = new Date(`${date}T12:00:00Z`)
  if (Number.isNaN(parsed.getTime())) return date
  return SHORT_DAY_LABEL.format(parsed).replace(/\./g, "")
}

/** `yyyy-MM-dd` calendar date -> "mié 8 oct" (es-CL). The date is a plain calendar day, so it is
 * read at UTC noon and formatted in UTC: no browser timezone can shift it a day. */
export function formatDayLabel(date: string): string {
  const parsed = new Date(`${date}T12:00:00Z`)
  if (Number.isNaN(parsed.getTime())) return date
  return DAY_LABEL.format(parsed).replace(/\./g, "").replace(",", "")
}

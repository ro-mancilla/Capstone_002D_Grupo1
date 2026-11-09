/**
 * Shared chart theming (plan 07 §4): series colors take `var(--chart-1)`..`var(--chart-5)`
 * straight out of `index.css`'s `@theme inline` block -- Recharts passes `stroke`/`fill` through
 * verbatim as SVG presentation attributes, and browsers resolve `var()` in those at paint time,
 * so charts re-theme on the `.dark` class toggle with zero re-render logic (verified against
 * Recharts' own docs/examples, which use the identical "pass a CSS custom property string as a
 * color prop" pattern -- plan 07 §1's context7 pre-check).
 */
export const CHART_COLORS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
] as const

/**
 * The colour for series `index`, in fixed slot order.
 *
 * Callers must not hand this an index past the last slot: the modulo is a crash guard, not a
 * feature, and a wrapped index paints two different series the same colour. Anything with more
 * categories than slots folds its tail into one "Otros" bucket before it gets here -- see
 * `SalesPage`'s `MAX_STACK_SERIES`.
 */
export function chartColor(index: number): string {
  return CHART_COLORS[index % CHART_COLORS.length]
}

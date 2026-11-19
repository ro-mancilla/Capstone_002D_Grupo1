import { useState } from "react"
import { ChevronDown } from "lucide-react"
import { formatPercent } from "@/lib/format"
import { t } from "@/lib/i18n"
import { ACCURACY_MIN_DAYS, ACCURACY_WINDOW_DAYS, type Accuracy } from "./forecastAccuracy"
import type { AccuracyStatus } from "./ForecastUnitSection"

export interface AccuracyRow {
  productId: string
  name: string
  value: Accuracy
}

/**
 * "¿Qué tan cerca estuvo el pronóstico?" for one unit group: the pooled deviation as the headline
 * (the WAPE, called *desvío promedio* -- "WAPE" lives only in the explanation), a neutral bar per
 * product ordered from the largest deviation (never green/red: a bar is not a verdict), and
 * coverage as one cell per window day.
 */
export function AccuracyBlock({
  status,
  overall,
  rows,
}: {
  status: AccuracyStatus
  overall: Accuracy | undefined
  rows: AccuracyRow[]
}) {
  const strings = t.dashboard.forecast.accuracy
  const [open, setOpen] = useState(false)

  return (
    <div className="flex flex-col gap-3 text-sm" data-testid="accuracy-block">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <p className="font-medium">{strings.title}</p>
          <p className="text-muted-foreground text-xs">{strings.subtitle}</p>
        </div>
        <button
          type="button"
          className="text-primary text-sm font-medium inline-flex items-center gap-1"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
        >
          {strings.howTitle}
          <ChevronDown className={`size-4 transition-transform ${open ? "rotate-180" : ""}`} aria-hidden />
        </button>
      </div>
      {open && <p className="text-muted-foreground max-w-prose text-sm leading-relaxed">{strings.howBody}</p>}

      {status === "loading" ? (
        <p className="text-muted-foreground">{strings.loading}</p>
      ) : status === "error" ? (
        <p className="text-destructive">{strings.error}</p>
      ) : !overall ? (
        <p className="text-muted-foreground">{strings.insufficient(ACCURACY_MIN_DAYS)}</p>
      ) : (
        <>
          <div className="flex items-baseline gap-3">
            <span className="text-4xl font-semibold tabular-nums">{formatPercent(overall.wape)}</span>
            <span className="text-muted-foreground max-w-xs">{strings.sentence(formatPercent(overall.wape))}</span>
          </div>
          <div className="flex flex-col gap-1.5">
            <div className="flex gap-0.5" aria-hidden>
              {Array.from({ length: ACCURACY_WINDOW_DAYS }, (_, i) => (
                <span key={i} className={`h-2 flex-1 rounded-sm ${i < overall.days ? "bg-ink-body" : "bg-muted"}`} />
              ))}
            </div>
            <span className="text-muted-foreground text-xs">
              {strings.coverage(overall.days, ACCURACY_WINDOW_DAYS)}
              {overall.unforecastedDays > 0 && ` · ${strings.unforecasted(overall.unforecastedDays)}`}
            </span>
          </div>
          {rows.length > 1 && (
            <ul className="border-border flex flex-col border-t">
              {rows.map((row) => (
                <li
                  key={row.productId}
                  className="border-border grid h-10 grid-cols-[minmax(0,9rem)_minmax(0,1fr)_3.5rem] items-center gap-3 border-b"
                >
                  <span className="truncate" title={row.name}>
                    {row.name}
                  </span>
                  <span className="bg-muted relative h-2 overflow-hidden rounded-full">
                    <span
                      className="bg-ink-body absolute inset-y-0 left-0 rounded-full"
                      style={{ width: `${Math.min(100, row.value.wape * 100)}%` }}
                    />
                  </span>
                  <span className="text-right font-semibold tabular-nums">{formatPercent(row.value.wape)}</span>
                </li>
              ))}
            </ul>
          )}
          <p className="text-muted-foreground text-xs">{strings.footnote}</p>
        </>
      )}
    </div>
  )
}

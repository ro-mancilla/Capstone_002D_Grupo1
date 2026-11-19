import { useEffect, useRef, useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Clock } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { FORECAST_VS_ACTUAL_KEY, FORECASTS_KEY_PREFIX, forecastGeneratedAtQuery, useRequestForecastRefresh } from "@/api/queries/forecast"
import { useAuth } from "@/features/auth/AuthContext"
import { can } from "@/lib/roles"
import { t } from "@/lib/i18n"
import { freshness } from "./forecastFormat"

const POLL_MS = 3_000
/** Stop waiting for new rows after this long; the worker may be down, and the page must not spin forever. */
const GIVE_UP_MS = 120_000

/**
 * "Actualizado: <generated_at>" for the branch's forecast, plus -- for roles F2 allows
 * (`MODULES_TOGGLE`, i.e. GM/ADMIN, mirrored client-side by `MANAGE_FULL_ADMIN`) -- the
 * *Actualizar pronóstico* button. F2 answers 202 and the worker writes the rows later, so after a
 * request this polls F1 until a newer `generatedAt` lands, then refreshes the chart's K8 query.
 * There is no SSE event for forecasts (events.ts), hence polling. Mount with `key={branchId}` so
 * switching branch discards the wait and the last-seen timestamp of the previous one.
 * `pollMs`/`giveUpMs` exist so tests don't wait in real time.
 */
export function ForecastRefresh({
  branchId,
  pollMs = POLL_MS,
  giveUpMs = GIVE_UP_MS,
}: {
  branchId: string
  pollMs?: number
  giveUpMs?: number
}) {
  const { session } = useAuth()
  const strings = t.dashboard.forecast
  const queryClient = useQueryClient()
  const request = useRequestForecastRefresh()
  const canRefresh = can(session?.role, "MANAGE_FULL_ADMIN")
  // generatedAt the page showed when the request was made (0 = none yet); null when idle. Comparing
  // server timestamps to each other, not to the browser clock, keeps clock skew out of it.
  const [awaitingAfter, setAwaitingAfter] = useState<number | null>(null)

  const { data } = useQuery({
    ...forecastGeneratedAtQuery(branchId),
    enabled: Boolean(branchId),
    refetchInterval: awaitingAfter == null ? false : pollMs,
  })
  const generatedAt = data && data.length > 0 ? Math.max(...data.map((row) => row.generatedAt)) : null

  // New rows landed (from this request or the nightly job): refresh the chart.
  const lastSeen = useRef<number | null>(null)
  useEffect(() => {
    if (generatedAt == null) return
    if (lastSeen.current != null && generatedAt > lastSeen.current) {
      queryClient.invalidateQueries({ queryKey: FORECAST_VS_ACTUAL_KEY })
      // Every other F1 reader (e.g. a suggested-production table) shows rows and a "Generado" time
      // that just went stale; the freshness stamp lives under this prefix too.
      queryClient.invalidateQueries({ queryKey: FORECASTS_KEY_PREFIX })
    }
    lastSeen.current = generatedAt
    if (awaitingAfter != null && generatedAt > awaitingAfter) setAwaitingAfter(null)
  }, [generatedAt, awaitingAfter, queryClient])

  useEffect(() => {
    if (awaitingAfter == null) return
    const timer = setTimeout(() => {
      setAwaitingAfter(null)
      toast.error(strings.refreshTimedOut)
    }, giveUpMs)
    return () => clearTimeout(timer)
  }, [awaitingAfter, giveUpMs, strings.refreshTimedOut])

  const onRefresh = () => {
    const baseline = generatedAt ?? 0
    request.mutate(
      { branchId },
      {
        onSuccess: () => {
          toast.success(strings.refreshRequested)
          setAwaitingAfter(baseline)
        },
        onError: () => toast.error(strings.refreshFailed),
      }
    )
  }

  const stamp = generatedAt != null ? freshness(generatedAt, Date.now()) : null
  // Fresh: a quiet button; stale or never generated: the primary action.
  const emphasise = generatedAt == null || stamp?.stale === true

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex flex-wrap items-center gap-3">
        <span className={`flex items-center gap-1.5 text-sm ${stamp?.stale ? "text-warning" : "text-muted-foreground"}`}>
          <Clock className="size-4" aria-hidden />
          {awaitingAfter != null
            ? strings.refreshing
            : stamp
              ? stamp.label
              : strings.neverUpdated}
        </span>
        {canRefresh && (
          <Button
            variant={emphasise ? "default" : "ghost"}
            onClick={onRefresh}
            disabled={!branchId || request.isPending || awaitingAfter != null}
          >
            {generatedAt == null ? strings.generate : strings.refresh}
          </Button>
        )}
      </div>
      {stamp?.stale && awaitingAfter == null && (
        <span className="text-muted-foreground text-xs">
          {canRefresh ? strings.staleHint : strings.staleHintNoRefresh}
        </span>
      )}
    </div>
  )
}

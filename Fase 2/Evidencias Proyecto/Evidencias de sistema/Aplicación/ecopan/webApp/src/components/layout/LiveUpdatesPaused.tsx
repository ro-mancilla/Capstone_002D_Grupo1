import { useId } from "react"
import { CloudOff } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { useLiveUpdatesPaused } from "@/api/queries/events"
import { t } from "@/lib/i18n"

/**
 * "En vivo en pausa" (#140): shown in the header while the SSE stream is down and retrying, so a
 * dashboard that is not live never looks live. The session is kept; it clears on the next open,
 * which refetches once. A rejected refresh signs out instead and never gets here.
 */
export function LiveUpdatesPaused() {
  const paused = useLiveUpdatesPaused()
  const descriptionId = useId()
  if (!paused) return null
  return (
    <>
      <Badge variant="warning" role="status" aria-describedby={descriptionId} title={t.events.paused.description}>
        <CloudOff data-icon="inline-start" aria-hidden />
        {t.events.paused.label}
      </Badge>
      <span id={descriptionId} hidden>
        {t.events.paused.description}
      </span>
    </>
  )
}

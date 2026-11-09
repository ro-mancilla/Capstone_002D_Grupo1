import { TriangleAlert } from "lucide-react"
import { Button } from "@/components/ui/button"
import { t } from "@/lib/i18n"

/**
 * A read that failed for a reason other than a disabled module (that one has its own panel).
 * Says what went wrong in Spanish and offers a retry where retrying could help -- the alternative
 * this replaces was an empty state, which told the manager the bakery had no data.
 */
export function ReadFailure({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-10 text-center">
      <TriangleAlert className="text-muted-foreground size-8" />
      <p className="max-w-sm text-sm font-medium">{message}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          {t.common.retry}
        </Button>
      )}
    </div>
  )
}

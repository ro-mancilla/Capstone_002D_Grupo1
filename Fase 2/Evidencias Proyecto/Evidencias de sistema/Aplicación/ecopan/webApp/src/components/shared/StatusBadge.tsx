import { ArrowDown, PackageX, TriangleAlert } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { t } from "@/lib/i18n"

export type StockStatus = "low" | "negative" | "outOfStock" | "inactive"

const STATUS: Record<StockStatus, { icon?: LucideIcon; variant: "warning" | "destructive" | "outline" }> = {
  low: { icon: ArrowDown, variant: "warning" },
  negative: { icon: TriangleAlert, variant: "destructive" },
  outOfStock: { icon: PackageX, variant: "warning" },
  inactive: { variant: "outline" },
}

/** A stock state as icon + word, never colour alone. A healthy row shows nothing at all (no "OK"),
 * so the badge only exists for states that need a look. Same words, icons and tones as the
 * Compose `StatusPill`. */
export function StatusBadge({ status }: { status: StockStatus }) {
  const { icon: Icon, variant } = STATUS[status]
  return (
    <Badge variant={variant}>
      {Icon && <Icon aria-hidden="true" strokeWidth={2} />}
      {t.stockStatus[status]}
    </Badge>
  )
}

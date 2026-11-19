import { createFileRoute } from "@tanstack/react-router"
import { z } from "zod"
import { ProductionPage } from "@/features/dashboard/ProductionPage"
import { requireEntitlements } from "@/lib/guards"

/** Opened from a "lote listo" notice: `today` preset, that branch, and the product to highlight. */
const productionSearchSchema = z.object({
  branchId: z.string().optional(),
  today: z.boolean().optional(),
  highlight: z.string().optional(),
})

export const Route = createFileRoute("/_authed/analytics/production")({
  validateSearch: productionSearchSchema,
  beforeLoad: requireEntitlements("/analytics/production"),
  component: ProductionPage,
})

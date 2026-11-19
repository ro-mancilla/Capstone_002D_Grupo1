import { createFileRoute } from "@tanstack/react-router"
import { WastePage } from "@/features/dashboard/WastePage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/analytics/waste")({
  beforeLoad: requireEntitlements("/analytics/waste"),
  component: WastePage,
})

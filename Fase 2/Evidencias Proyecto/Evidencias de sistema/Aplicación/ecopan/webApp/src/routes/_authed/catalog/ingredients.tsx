import { createFileRoute } from "@tanstack/react-router"
import { IngredientsPage } from "@/features/admin-inventory/IngredientsPage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/catalog/ingredients")({
  beforeLoad: requireEntitlements("/catalog/ingredients"),
  component: IngredientsPage,
})

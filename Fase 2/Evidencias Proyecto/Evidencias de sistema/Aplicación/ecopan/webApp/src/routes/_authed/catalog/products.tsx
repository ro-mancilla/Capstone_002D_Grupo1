import { createFileRoute } from "@tanstack/react-router"
import { ProductsPage } from "@/features/admin-catalog/ProductsPage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/catalog/products")({
  beforeLoad: requireEntitlements("/catalog/products"),
  component: ProductsPage,
})

import { Link } from "@tanstack/react-router"
import { Boxes, TriangleAlert } from "lucide-react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { t } from "@/lib/i18n"

/**
 * "Por reponer" (design 3e): how many ingredients and Almacén products are at or under their
 * reorder point, a danger line when any are below zero, and a link to where it is fixed. With
 * nothing pending the card says so in words instead of showing a bare 0.
 */
export function LowStockCard({
  ingredients,
  products,
  negativeIngredients,
  negativeProducts,
  ingredientsEnabled,
  isLoading,
}: {
  ingredients: number
  products: number
  negativeIngredients: number
  negativeProducts: number
  ingredientsEnabled: boolean
  isLoading?: boolean
}) {
  const strings = t.dashboard.overview.lowStock
  const total = ingredients + products
  const negativeText = strings.negative(negativeIngredients, negativeProducts)
  // Ingredients are where most of the restocking happens; fall back to Almacén when there are none.
  const target = ingredients > 0 || !products ? "/catalog/ingredients" : "/catalog/products"

  return (
    <Card className="@container/card gap-3">
      <CardHeader>
        <CardDescription className="flex items-center gap-1.5">
          <Boxes className="size-3.5 opacity-70" aria-hidden="true" />
          {!isLoading && total === 0 ? strings.labelEmpty : strings.label}
        </CardDescription>
        {isLoading ? (
          <Skeleton className="mt-1 h-8 w-28" />
        ) : total > 0 ? (
          <CardTitle className="ds-numeric text-[28px] @[220px]/card:text-[32px]">{total}</CardTitle>
        ) : null}
      </CardHeader>
      {!isLoading && (
        <CardContent className="flex flex-col gap-2 text-sm">
          {total === 0 ? (
            <p className="text-ink-muted">{strings.none}</p>
          ) : (
            <>
              <p className="text-ink-muted text-xs">
                {ingredientsEnabled ? strings.breakdown(ingredients, products) : strings.productsOnly(products)}
              </p>
              {negativeText && (
                <p className="text-danger flex items-center gap-1.5 text-xs font-medium">
                  <TriangleAlert className="size-3.5" aria-hidden="true" />
                  {negativeText}
                </p>
              )}
              <Link to={target} className="text-primary text-xs font-medium underline-offset-4 hover:underline">
                {strings.seeAll(total)}
              </Link>
            </>
          )}
        </CardContent>
      )}
    </Card>
  )
}

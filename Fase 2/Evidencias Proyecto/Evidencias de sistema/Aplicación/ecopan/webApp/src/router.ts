import { createRouter } from "@tanstack/react-router"
import { routeTree } from "./routeTree.gen"
import type { RouterContext } from "./routes/__root"

/** Created once, module-scoped -- `context.auth` starts as a placeholder and is filled in on
 * every render by `<RouterProvider context={{ auth, queryClient }} />` in `main.tsx` (the
 * documented TanStack Router auth pattern: create the router outside the component tree, inject
 * live context via the `context` prop). */
export const router = createRouter({
  routeTree,
  context: {
    auth: undefined!,
    queryClient: undefined!,
  } satisfies RouterContext,
  defaultPreload: "intent",
})

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router
  }
}

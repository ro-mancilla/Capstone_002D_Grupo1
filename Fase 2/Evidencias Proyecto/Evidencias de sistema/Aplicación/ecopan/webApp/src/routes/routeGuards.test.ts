import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { DESTINATIONS } from "@/lib/destinations"

/**
 * `requireEntitlements` takes a destination path, so a typo is a type error -- but naming the
 * *wrong* destination still compiles. `organization/tenant` guarding `"/organization/users"` would type-check
 * and silently apply another route's rules.
 *
 * This walks the route files themselves and checks each guard names the route it sits in, which
 * is the one thing the type cannot say.
 */
const ROUTES_DIR = join(__dirname, "_authed")

function routeFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) return routeFiles(full)
    return entry.name.endsWith(".tsx") ? [full] : []
  })
}

interface GuardedRoute {
  file: string
  routePath: string
  guardedPath: string
}

function guardedRoutes(): GuardedRoute[] {
  return routeFiles(ROUTES_DIR).flatMap((file) => {
    const source = readFileSync(file, "utf8")
    const guard = source.match(/requireEntitlements\(\s*"([^"]+)"\s*\)/)
    if (!guard) return []

    const route = source.match(/createFileRoute\(\s*"([^"]+)"\s*\)/)
    expect(route, `${file} calls requireEntitlements but has no createFileRoute`).not.toBeNull()

    return [{ file, routePath: route![1], guardedPath: guard![1] }]
  })
}

describe("route guards name their own destination", () => {
  it("finds the guarded routes at all (so a broken matcher fails loudly)", () => {
    expect(guardedRoutes().length).toBeGreaterThanOrEqual(10)
  })

  it("guards each route with the path that route actually serves", () => {
    for (const { file, routePath, guardedPath } of guardedRoutes()) {
      // `/_authed/catalog/products` is served at `/catalog/products`; the layout segment is a
      // grouping prefix, not part of the URL. A trailing-slash index route keeps its parent path.
      const served = routePath.replace(/^\/_authed/, "").replace(/\/$/, "") || "/"

      expect(guardedPath, `${file} guards ${guardedPath} but serves ${served}`).toBe(served)
    }
  })

  it("only guards paths the destination table knows about", () => {
    const known = new Set(DESTINATIONS.map((destination) => destination.to))

    for (const { file, guardedPath } of guardedRoutes()) {
      expect(known.has(guardedPath), `${file} guards ${guardedPath}, absent from DESTINATIONS`).toBe(true)
    }
  })

  /** The table and the route files are two lists of the same thing. Every destination must have a
   * guarded route (an unguarded one would be reachable by URL whatever the sidebar says), and
   * every guarded route must be a destination (checked above), so neither can become an orphan. */
  it("guards a route for every destination in the table", () => {
    const guarded = new Set(guardedRoutes().map((route) => route.guardedPath))

    for (const destination of DESTINATIONS) {
      expect(guarded.has(destination.to), `${destination.to} has no route guarding it`).toBe(true)
    }
  })
})

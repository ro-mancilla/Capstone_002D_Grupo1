import { describe, expect, it } from "vitest"
import { DESTINATIONS, buildNavSections, firstEntitled, isEntitled, resolveLanding } from "./destinations"
import { t } from "./i18n"
import { managerSession } from "@/test/fixtures"
import type { Session } from "@/api/types"

const session = managerSession

function shownDestinations(s: Session | null): string[] {
  return buildNavSections(s).flatMap((section) => section.items.map((item) => item.to))
}

describe("destinations (one table behind the sidebar and the route guards)", () => {
  /**
   * The property the whole module exists for. Before this table, "which modules does this
   * destination need" was stated once in the sidebar and again in each route file, and the two
   * had drifted: the Panel section stayed visible with `ANALYTICS_DASHBOARD` off, while the
   * route refused it.
   */
  it("shows a destination in the sidebar exactly when the guards would admit it", () => {
    const sessions: (Session | null)[] = [
      null,
      session({}),
      session({ role: "BRANCH_MANAGER" }),
      session({ enabledModules: ["POS_SALES"] }),
      session({ role: "BRANCH_MANAGER", enabledModules: ["POS_SALES"] }),
      session({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD"] }),
      session({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }),
      session({ role: "BRANCH_MANAGER", enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }),
    ]

    for (const s of sessions) {
      const shown = new Set(shownDestinations(s))
      for (const destination of DESTINATIONS) {
        expect(
          shown.has(destination.to),
          `${destination.to} for role ${s?.role ?? "none"} with [${s?.enabledModules ?? []}]`
        ).toBe(isEntitled(s, destination.to))
      }
    }
  })

  it("hides the Análisis section entirely when ANALYTICS_DASHBOARD is off", () => {
    const sections = buildNavSections(session({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }))

    expect(sections.map((s) => s.label)).not.toContain(t.nav.sectionAnalytics)
    expect(shownDestinations(session({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }))).not.toContain(
      "/analytics"
    )
  })

  it("never resolves the landing destination to a route the guards would refuse", () => {
    const sessions: Session[] = [
      session({}),
      session({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }),
      session({ role: "BRANCH_MANAGER", enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }),
      session({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD"] }),
    ]

    for (const s of sessions) {
      const landing = firstEntitled(s)
      expect(landing, `role ${s.role} with [${s.enabledModules}]`).not.toBeNull()
      expect(isEntitled(s, landing!)).toBe(true)
    }
  })

  it("lands a manager somewhere else when ANALYTICS_DASHBOARD is off", () => {
    const landing = firstEntitled(session({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }))

    expect(landing).not.toBeNull()
    expect(landing?.startsWith("/analytics")).toBe(false)
  })

  it("reports no landing destination when the session is entitled to nothing", () => {
    // A role with no capabilities at all. (A branch manager with every module off is no longer
    // stranded: `products` is core catalog and needs no module.)
    const stranded = session({ role: "BAKER", enabledModules: ["POS_SALES"] })

    expect(shownDestinations(stranded)).toEqual([])
    expect(firstEntitled(stranded)).toBeNull()
    expect(firstEntitled(null)).toBeNull()
  })

  it("keeps INVENTORY_FORMULAS and DEMAND_FORECASTING behaving as before", () => {
    const withoutFormulas = shownDestinations(session({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD"] }))
    expect(withoutFormulas).not.toContain("/analytics/stock")
    expect(withoutFormulas).not.toContain("/analytics/product-stock")
    expect(withoutFormulas).not.toContain("/catalog/ingredients")
    expect(withoutFormulas).toContain("/catalog/products")
    expect(withoutFormulas).not.toContain("/analytics/forecast")

    const withForecasting = shownDestinations(
      session({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD", "DEMAND_FORECASTING"] })
    )
    expect(withForecasting).toContain("/analytics/forecast")

    // `branches` stays reachable with MULTI_BRANCH_ROLES off, by design.
    const withoutMultiBranch = shownDestinations(session({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD"] }))
    expect(withoutMultiBranch).toContain("/organization/branches")
  })

  it("groups the sidebar by task: Análisis, Catálogo, Organización, in that order", () => {
    const sections = buildNavSections(session({}))

    expect(sections.map((s) => [s.label, s.items.map((i) => [i.label, i.to])])).toEqual([
      [
        "Análisis",
        [
          ["Resumen", "/analytics"],
          ["Ventas", "/analytics/sales"],
          ["Pronóstico", "/analytics/forecast"],
          ["Producción", "/analytics/production"],
          ["Mermas", "/analytics/waste"],
          ["Inventario reconciliado", "/analytics/reconciliation"],
        ],
      ],
      [
        "Catálogo",
        [
          ["Productos", "/catalog/products"],
          ["Insumos", "/catalog/ingredients"],
        ],
      ],
      [
        "Organización",
        [
          ["Sucursales", "/organization/branches"],
          ["Usuarios", "/organization/users"],
          ["Módulos", "/organization/modules"],
          ["Panadería", "/organization/tenant"],
        ],
      ],
    ])
  })

  it("keeps every URL under its section's prefix, so a path says which task it belongs to", () => {
    const prefixes: Record<string, string> = {
      [t.nav.sectionAnalytics]: "/analytics",
      [t.nav.sectionCatalog]: "/catalog",
      [t.nav.sectionOrganization]: "/organization",
    }
    for (const section of buildNavSections(session({}))) {
      for (const item of section.items) expect(item.to.startsWith(prefixes[section.label])).toBe(true)
    }
  })

  it("gives a BRANCH_MANAGER Análisis, Productos, Insumos and Sucursales, and hides Organización's admin-only entries", () => {
    const sections = buildNavSections(session({ role: "BRANCH_MANAGER" }))

    expect(sections.map((s) => s.label)).toEqual([t.nav.sectionAnalytics, t.nav.sectionCatalog, t.nav.sectionOrganization])
    expect(sections[1].items.map((i) => i.to)).toEqual(["/catalog/products", "/catalog/ingredients"])
    expect(sections[2].items.map((i) => i.to)).toEqual(["/organization/branches"])
  })

  it("drops a whole section, never greys it, when nothing in it is entitled", () => {
    // POS-only branch manager: no analytics module, no ingredients; Catálogo and Organización remain.
    const posOnly = buildNavSections(session({ role: "BRANCH_MANAGER", enabledModules: ["POS_SALES"] }))
    expect(posOnly.map((s) => s.label)).toEqual([t.nav.sectionCatalog, t.nav.sectionOrganization])
    expect(posOnly[0].items.map((i) => i.to)).toEqual(["/catalog/products"])
  })

  it("states each destination's own gates, with no section-level gate left to inherit", () => {
    // Forecast needs the analytics module on top of its own; sales needs analytics + the capability.
    expect(isEntitled(session({ enabledModules: ["DEMAND_FORECASTING"] }), "/analytics/forecast")).toBe(false)
    expect(isEntitled(session({ enabledModules: ["ANALYTICS_DASHBOARD", "DEMAND_FORECASTING"] }), "/analytics/forecast")).toBe(true)
    expect(isEntitled(session({ enabledModules: ["ANALYTICS_DASHBOARD"] }), "/analytics/waste")).toBe(false)
    expect(isEntitled(session({ enabledModules: ["ANALYTICS_DASHBOARD"] }), "/analytics/sales")).toBe(true)
    expect(isEntitled(session({ role: "BRANCH_MANAGER" }), "/organization/users")).toBe(false)
    // Producción reads /analytics/production-summary: analytics module + VIEW_DASHBOARD, nothing else.
    expect(isEntitled(session({ enabledModules: ["POS_SALES"] }), "/analytics/production")).toBe(false)
    expect(isEntitled(session({ role: "BAKER" }), "/analytics/production")).toBe(false)
    expect(isEntitled(session({ role: "BRANCH_MANAGER", enabledModules: ["ANALYTICS_DASHBOARD"] }), "/analytics/production")).toBe(true)
  })

  it("hides Inventario reconciliado unless both ANALYTICS_DASHBOARD and INVENTORY_FORMULAS are on", () => {
    const reconciliation = "/analytics/reconciliation"
    const inventoryOff = session({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD"] })
    const analyticsOff = session({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] })
    const bothOn = session({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD", "INVENTORY_FORMULAS"] })

    expect(shownDestinations(inventoryOff)).not.toContain(reconciliation)
    expect(isEntitled(inventoryOff, reconciliation)).toBe(false)
    expect(shownDestinations(analyticsOff)).not.toContain(reconciliation)
    expect(isEntitled(analyticsOff, reconciliation)).toBe(false)
    expect(shownDestinations(bothOn)).toContain(reconciliation)
    expect(isEntitled(bothOn, reconciliation)).toBe(true)
    // A role without the dashboard capability never sees it either.
    expect(isEntitled(session({ role: "BAKER" }), reconciliation)).toBe(false)
  })

  it("has one Sucursales destination, and no two sidebar entries share an icon", () => {
    const destinations = DESTINATIONS.map((destination) => destination.to as string)
    expect(destinations).not.toContain("/analytics/branches")
    expect(destinations.filter((to) => to.endsWith("/branches"))).toEqual(["/organization/branches"])

    const icons = DESTINATIONS.map((destination) => destination.icon)
    expect(new Set(icons).size).toBe(icons.length)
  })

  it("keeps Almacén stock out of the ingredients gate: Productos needs no module, and the old stock page is gone", () => {
    const posOnly = session({ role: "BRANCH_MANAGER", enabledModules: ["POS_SALES"] })

    expect(isEntitled(posOnly, "/catalog/products")).toBe(true)
    expect(isEntitled(posOnly, "/catalog/ingredients")).toBe(false)
    const everything = DESTINATIONS.map((destination) => destination.to as string)
    expect(everything).not.toContain("/analytics/product-stock")
    // One Insumos page replaced Stock de insumos, the Insumos CRUD and Ingreso de insumos.
    expect(everything).not.toContain("/analytics/stock")
    expect(everything).not.toContain("/catalog/stock-entry")
    expect(everything.filter((to) => to === "/catalog/ingredients")).toHaveLength(1)
  })

  it("honours a requested landing only when the session may open it", () => {
    const noAnalytics = session({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] })

    // `?redirect=` carried through login points wherever the visitor was refused, which for an
    // unauthenticated hit on the dashboard is a route this session still cannot open.
    expect(resolveLanding(noAnalytics, "/analytics")).not.toBe("/analytics")
    expect(resolveLanding(noAnalytics, "/analytics?range=7d")).not.toBe("/analytics")
    expect(isEntitled(noAnalytics, resolveLanding(noAnalytics, "/analytics")!)).toBe(true)

    // An entitled request is honoured, query string and all.
    expect(resolveLanding(noAnalytics, "/catalog/ingredients")).toBe("/catalog/ingredients")
    expect(resolveLanding(session({}), "/analytics/forecast?range=30d")).toBe("/analytics/forecast")

    // Something that is not a destination at all falls back rather than being trusted.
    expect(resolveLanding(session({}), "/not-a-route")).toBe("/analytics")
    expect(resolveLanding(session({}), undefined)).toBe("/analytics")
    expect(resolveLanding(session({ role: "BRANCH_MANAGER", enabledModules: ["POS_SALES"] }), "/analytics")).toBe("/catalog/products")
    expect(resolveLanding(session({ role: "BAKER", enabledModules: ["POS_SALES"] }), "/analytics")).toBeNull()
  })
})

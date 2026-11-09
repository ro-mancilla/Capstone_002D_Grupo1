import { Link } from "@tanstack/react-router"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Separator } from "@/components/ui/separator"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { ThemeToggle } from "@/components/shared/ThemeToggle"
import { LiveUpdatesPaused } from "@/components/layout/LiveUpdatesPaused"
import type { NavSection } from "@/lib/destinations"

/**
 * Sticky header carrying "where am I" (dashboard-01's `site-header`, plus a breadcrumb).
 *
 * The crumb is derived from the same `NavSection[]` the sidebar renders, so a route can never
 * show a title the nav disagrees with, and a module toggled off cannot leave a stale label
 * behind. Section > page, two levels -- this app's routes are never deeper than that.
 */
export function SiteHeader({
  sections,
  pathname,
}: {
  sections: NavSection[]
  pathname: string
}) {
  const section = sections.find((candidate) =>
    candidate.items.some((item) => item.to === pathname)
  )
  const current = section?.items.find((item) => item.to === pathname)
  // The section's own first item doubles as its landing page, so the parent crumb stays clickable
  // instead of being dead text.
  const sectionHome = section?.items[0]

  return (
    <header className="bg-background/85 sticky top-0 z-10 flex h-(--header-height) shrink-0 items-center gap-2 border-b backdrop-blur-sm">
      <div className="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1" />
        <Separator orientation="vertical" className="mx-1 h-4 data-vertical:self-auto" />
        <Breadcrumb>
          <BreadcrumbList>
            {section && sectionHome && (
              <>
                <BreadcrumbItem className="hidden sm:block">
                  {sectionHome.to === pathname ? (
                    <span className="text-muted-foreground">{section.label}</span>
                  ) : (
                    <BreadcrumbLink render={<Link to={sectionHome.to} />}>
                      {section.label}
                    </BreadcrumbLink>
                  )}
                </BreadcrumbItem>
                <BreadcrumbSeparator className="hidden sm:block" />
              </>
            )}
            <BreadcrumbItem>
              <BreadcrumbPage className="font-medium">{current?.label ?? ""}</BreadcrumbPage>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
        <div className="ml-auto flex items-center gap-2">
          <LiveUpdatesPaused />
          <ThemeToggle />
        </div>
      </div>
    </header>
  )
}

import { t } from "@/lib/i18n"

/** Placeholder for pages plan 07 items 4+ build out. Exists only so the route tree compiles and
 * the sidebar nav has real destinations to link to (plan 07 scope note for items 1-3). */
export function StubPage({ title }: { title: string }) {
  return (
    <div className="flex flex-col gap-2">
      <h1 className="page-title">{title}</h1>
      <p className="text-muted-foreground text-sm">{t.stub.comingSoon}</p>
    </div>
  )
}

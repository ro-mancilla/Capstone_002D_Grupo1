import { useQuery } from "@tanstack/react-query"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { tenantProfileQuery } from "@/api/queries/tenant"
import { t } from "@/lib/i18n"
import { moduleName } from "@/lib/labels"

/** `admin/tenant` (plan 07 §5 row: mostly a read-only inspection surface -- see plan's own note
 * that it's "the second seed profile" demo, `11-seeds-and-demo.md`). Name/currency are read here;
 * there is no `PUT /tenant` in 02 §3 beyond `PUT /tenant/modules` (item 7's module-toggle
 * surface) and `POST/PUT /tenant/branches` (item 7's branch CRUD), so this page has no form. */
export function TenantProfilePage() {
  const strings = t.admin.tenant
  const profileResult = useQuery(tenantProfileQuery())

  if (profileResult.isLoading || !profileResult.data) {
    return (
      <div className="flex flex-col gap-4">
        <h1 className="page-title">{strings.title}</h1>
        <Skeleton className="h-48 w-full max-w-lg" />
      </div>
    )
  }

  const profile = profileResult.data

  return (
    <div className="flex flex-col gap-6">
      <h1 className="page-title">{strings.title}</h1>

      <dl className="grid max-w-lg grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground text-sm">{strings.nameLabel}</dt>
          <dd className="text-base font-medium">{profile.name}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground text-sm">{strings.slugLabel}</dt>
          <dd className="text-base font-medium">{profile.slug}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground text-sm">{strings.currencyLabel}</dt>
          <dd className="text-base font-medium">{profile.currencyCode}</dd>
        </div>
      </dl>

      <div>
        <h2 className="text-muted-foreground mb-2 text-sm">{strings.modulesLabel}</h2>
        <div className="flex flex-wrap gap-2">
          {profile.enabledModules.map((module) => (
            <Badge key={module} variant="secondary">
              {moduleName(module)}
            </Badge>
          ))}
        </div>
      </div>

      <div>
        <h2 className="text-muted-foreground mb-2 text-sm">{strings.branchesLabel}</h2>
        <ul className="flex flex-col gap-1 text-sm">
          {profile.branches.map((branch) => (
            <li key={branch.id}>
              {branch.name}
              {!branch.active && (
                <Badge variant="outline" className="ml-2">
                  {t.stockStatus.inactive}
                </Badge>
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

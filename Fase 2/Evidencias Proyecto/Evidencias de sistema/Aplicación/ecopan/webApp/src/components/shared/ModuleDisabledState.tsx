import { PackageX } from "lucide-react"
import { t } from "@/lib/i18n"

/**
 * Shared "module not enabled" state (plan 07 §3/§11 item 7): rendered by any admin surface whose
 * data query 403s with a populated `ErrorBody.module` (`lib/apiErrors.ts`'s `isModuleDisabledError`)
 * -- e.g. a stale open tab whose module got toggled off in another tab/session. Generic across
 * module names by construction: nothing here is `INVENTORY_FORMULAS`-specific, it just names the
 * situation. `api/http.ts`'s module-disabled handler has already kicked off a tenant-profile
 * refetch by the time this renders, so the *next* navigation/guard check reflects reality; this
 * component only has to not crash the current one.
 */
export function ModuleDisabledState() {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed p-10 text-center">
      <PackageX className="text-muted-foreground size-8" />
      <p className="text-sm font-medium">{t.errors.moduleDisabled}</p>
      <p className="text-muted-foreground max-w-sm text-sm">{t.errors.moduleDisabledBody}</p>
    </div>
  )
}

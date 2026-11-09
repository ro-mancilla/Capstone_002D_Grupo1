import type { ModuleId, Role } from "@/api/types"
import { t } from "./i18n"

/**
 * The boundary where identifiers stop being identifiers and become words.
 *
 * Enum values, module ids and product-type keys are wire vocabulary: stable, uppercase, English,
 * and meaningful to the code alone. Every one of them that reaches a screen unconverted is a leak
 * -- `POS_SALES` in a badge, `BREAD` in a table cell -- and the leaks are easy to miss precisely
 * because the value renders without error. Anything user-visible goes through here.
 */

/** `POS_SALES` -> "Ventas (punto de venta)". Falls back to the humanised id, never the raw one,
 * so a module added to the API before its translation still reads as words. */
export function moduleName(id: ModuleId | string): string {
  const names = t.admin.modules.names as Record<string, string | undefined>
  return names[id] ?? humanizeCode(id)
}

/** `BRANCH_MANAGER` -> "Gerente de sucursal". */
export function roleName(role: Role | string): string {
  const names = t.admin.users.roles as Record<string, string | undefined>
  return names[role] ?? humanizeCode(role)
}

/** `SCHOOL_AND_OFFICE` -> "School and office": separators to spaces, sentence case. */
function humanizeCode(value: string): string {
  const words = value.toLowerCase().split(/[_-]+/).filter(Boolean)
  if (words.length === 0) return value
  return words
    .map((word, index) => (index === 0 ? word[0].toUpperCase() + word.slice(1) : word))
    .join(" ")
}

/** Estimator kind behind a forecast -> "Modelo (LightGBM)" / "Línea base". The baseline label
 * gives no reason: a series can be baseline for short history or for keys the model never saw. */
export function modelKindLabel(kind: ModelKind): string {
  return t.dashboard.forecast.models[kind]
}

/** Which estimator produced a forecast row; UNKNOWN when the version is missing or unrecognised. */
export type ModelKind = "MODEL" | "BASELINE" | "UNKNOWN"

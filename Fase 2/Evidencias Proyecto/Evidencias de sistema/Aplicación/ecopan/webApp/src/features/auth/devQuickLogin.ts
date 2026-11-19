import type { Role } from "@/api/types"

/**
 * One-tap sign-in for the seeded `reference` tenant, matching `DevQuickLoginProfile` in
 * `:feature:auth:impl` so all three clients offer the same shortcuts to the same accounts.
 *
 * CASHIER and BAKER are listed even though this dashboard rejects them: the rejection is a real
 * path worth being able to reach in one click, and hiding the two accounts would make the row
 * disagree with the one on the till and the baker's phone.
 */
export interface DevQuickLoginProfile {
  readonly role: Role
  readonly username: string
  readonly password: string
}

export const DEV_QUICK_LOGIN_TENANT = "reference"

export const DEV_QUICK_LOGIN_PROFILES: readonly DevQuickLoginProfile[] = [
  { role: "CASHIER", username: "cajero", password: "cajero123" },
  { role: "BAKER", username: "panadero", password: "panadero123" },
  { role: "BRANCH_MANAGER", username: "jefelocal", password: "jefelocal123" },
  { role: "GENERAL_MANAGER", username: "gerente", password: "gerente123" },
  { role: "ADMIN", username: "admin", password: "admin123" },
]

/**
 * On unless explicitly switched off, which is the same posture the Compose clients take with
 * `BuildKonfig.DEV_QUICK_LOGIN_ENABLED`.
 *
 * Default-on rather than keyed to `import.meta.env.DEV`, deliberately: the capstone demo runs a
 * production `vite build`, and a shortcut that silently disappears in the build you actually
 * present is worse than no shortcut. Set `VITE_DEV_QUICK_LOGIN=false` to drop it -- which is also
 * the one thing to do before this is ever served to anyone outside the project, since the row
 * publishes seeded passwords by design.
 */
export const devQuickLoginEnabled: boolean =
  import.meta.env.VITE_DEV_QUICK_LOGIN?.trim().toLowerCase() !== "false"

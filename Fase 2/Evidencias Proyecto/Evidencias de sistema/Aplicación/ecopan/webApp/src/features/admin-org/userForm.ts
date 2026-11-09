import { z } from "zod"
import { t } from "@/lib/i18n"
import type { Role, UpsertUserRequest, UserListItemDto } from "@/api/types"

/** Roles that must belong to exactly one branch (01 §4: they operate at a single physical
 * location) -- `GENERAL_MANAGER`/`ADMIN` are tenant-wide by default but the backend doesn't
 * forbid pinning them to a branch either, so the UI only *requires* a branch for these three. */
const BRANCH_SCOPED_ROLES: readonly Role[] = ["CASHIER", "BAKER", "BRANCH_MANAGER"]

export function isBranchScopedRole(role: Role): boolean {
  return BRANCH_SCOPED_ROLES.includes(role)
}

/** Sentinel for the branch `<Select>`'s "tenant-wide" option -- base-ui's `Select` doesn't
 * accept an empty-string item value, and `branchId: null` is the wire meaning (T4). */
export const TENANT_WIDE_BRANCH_VALUE = "__tenant_wide__"

/**
 * `isEdit` changes exactly one rule: password is required to *create* a user (T3 requires it
 * server-side too -- `TenantServiceImpl.createUser`), optional on edit (blank = "don't change",
 * per `UpsertUserRequest.password?` -- 02 T4, the server hashes and never echoes it back, so this
 * form must never be pre-filled from a GET response for password/PIN, see `userToFormValues`
 * below).
 */
export function buildUserFormSchema(isEdit: boolean) {
  return z
    .object({
      username: z.string().min(1, t.admin.users.form.validation.usernameRequired),
      displayName: z.string().min(1, t.admin.users.form.validation.displayNameRequired),
      role: z.enum(["CASHIER", "BAKER", "BRANCH_MANAGER", "GENERAL_MANAGER", "ADMIN"]),
      branchId: z.string(),
      password: z.string().optional(),
      pin: z
        .string()
        .optional()
        .refine((value) => !value || /^\d+$/.test(value), {
          message: t.admin.users.form.validation.pinNumeric,
        }),
      active: z.boolean(),
    })
    .check((ctx) => {
      const { role, branchId, password } = ctx.value
      if (!isEdit && !password) {
        ctx.issues.push({
          code: "custom",
          message: t.admin.users.form.validation.passwordRequired,
          input: password,
          path: ["password"],
        })
      }
      if (isBranchScopedRole(role) && branchId === TENANT_WIDE_BRANCH_VALUE) {
        ctx.issues.push({
          code: "custom",
          message: t.admin.users.form.validation.branchRequired,
          input: branchId,
          path: ["branchId"],
        })
      }
    })
}

export type UserFormValues = z.infer<ReturnType<typeof buildUserFormSchema>>

export function emptyUserFormValues(defaultBranchId: string | null): UserFormValues {
  return {
    username: "",
    displayName: "",
    role: "BRANCH_MANAGER",
    branchId: defaultBranchId ?? TENANT_WIDE_BRANCH_VALUE,
    password: "",
    pin: "",
    active: true,
  }
}

/** `UserListItemDto` -> form values. Deliberately never touches `password`/`pin` -- the DTO
 * doesn't even carry them (the server never echoes a hash or plaintext back), so there is
 * nothing to accidentally leak into the form either. */
export function userToFormValues(user: UserListItemDto): UserFormValues {
  return {
    username: user.username,
    displayName: user.displayName,
    role: user.role,
    branchId: user.branchId ?? TENANT_WIDE_BRANCH_VALUE,
    password: "",
    pin: "",
    active: user.active,
  }
}

export function toUpsertUserRequest(values: UserFormValues): UpsertUserRequest {
  return {
    username: values.username,
    displayName: values.displayName,
    role: values.role,
    branchId: values.branchId === TENANT_WIDE_BRANCH_VALUE ? null : values.branchId,
    password: values.password ? values.password : undefined,
    pin: values.pin ? values.pin : undefined,
    active: values.active,
  }
}

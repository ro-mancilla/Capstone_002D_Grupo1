import { z } from "zod"
import { t } from "@/lib/i18n"
import type { BranchDto, UpsertBranchRequest } from "@/api/types"

/** Branch create/edit form (plan 07 §5 row 5, T5 -- `MULTI_BRANCH_ROLES`-gated). */
export const branchFormSchema = z.object({
  name: z.string().min(1, t.admin.branches.form.validation.nameRequired),
  active: z.boolean(),
})

export type BranchFormValues = z.infer<typeof branchFormSchema>

export const emptyBranchFormValues: BranchFormValues = { name: "", active: true }

export function branchToFormValues(branch: BranchDto): BranchFormValues {
  return { name: branch.name, active: branch.active }
}

export function toUpsertBranchRequest(values: BranchFormValues): UpsertBranchRequest {
  return { name: values.name, active: values.active }
}

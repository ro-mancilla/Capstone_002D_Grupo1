import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { useCreateBranch, useUpdateBranch } from "@/api/queries/branches"
import { errorMessage } from "@/lib/apiErrors"
import type { BranchDto } from "@/api/types"
import { t } from "@/lib/i18n"
import {
  branchFormSchema,
  branchToFormValues,
  emptyBranchFormValues,
  toUpsertBranchRequest,
  type BranchFormValues,
} from "./branchForm"

interface BranchFormDialogProps {
  branch?: BranchDto
  trigger: React.ReactNode
}

/** Create/edit dialog for `admin/branches` (plan 07 §5 row 5). Only ever rendered when
 * `MULTI_BRANCH_ROLES` is on -- `BranchesPage` swaps this out for a read-only list otherwise. */
export function BranchFormDialog({ branch, trigger }: BranchFormDialogProps) {
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const createBranch = useCreateBranch()
  const updateBranch = useUpdateBranch()
  const isEdit = branch !== undefined
  const strings = t.admin.branches.form

  const form = useForm({
    resolver: zodResolver(branchFormSchema),
    defaultValues: branch ? branchToFormValues(branch) : emptyBranchFormValues,
  })

  useEffect(() => {
    if (!open) return
    form.reset(branch ? branchToFormValues(branch) : emptyBranchFormValues)
    setFormError(null)
  }, [open, branch, form])

  async function onSubmit(values: BranchFormValues) {
    setFormError(null)
    try {
      if (isEdit) {
        await updateBranch.mutateAsync({ id: branch.id, body: toUpsertBranchRequest(values) })
      } else {
        await createBranch.mutateAsync(toUpsertBranchRequest(values))
      }
      setOpen(false)
    } catch (error) {
      setFormError(errorMessage(error, strings.genericError))
    }
  }

  const isSubmitting = createBranch.isPending || updateBranch.isPending

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger as React.ReactElement} />
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? t.admin.branches.editBranch : t.admin.branches.newBranch}
          </DialogTitle>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="name"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="branch-name">{strings.nameLabel}</FieldLabel>
                  <Input
                    {...field}
                    id="branch-name"
                    placeholder={strings.namePlaceholder}
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            {isEdit && (
              <Controller
                name="active"
                control={form.control}
                render={({ field }) => (
                  <Field orientation="horizontal">
                    <FieldLabel htmlFor="branch-active">{strings.activeLabel}</FieldLabel>
                    <Switch
                      id="branch-active"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </Field>
                )}
              />
            )}
            {formError && (
              <p role="alert" className="text-destructive text-sm">
                {formError}
              </p>
            )}
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isEdit ? strings.submitEdit : strings.submitCreate}
            </Button>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}

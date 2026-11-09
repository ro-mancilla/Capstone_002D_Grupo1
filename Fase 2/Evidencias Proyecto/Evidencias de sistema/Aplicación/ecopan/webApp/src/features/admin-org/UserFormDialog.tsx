import { useEffect, useMemo, useState } from "react"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
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
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { useCreateUser, useUpdateUser } from "@/api/queries/users"
import { errorMessage } from "@/lib/apiErrors"
import { useAuth } from "@/features/auth/AuthContext"
import type { Role, UserListItemDto } from "@/api/types"
import { t } from "@/lib/i18n"
import {
  TENANT_WIDE_BRANCH_VALUE,
  buildUserFormSchema,
  emptyUserFormValues,
  isBranchScopedRole,
  toUpsertUserRequest,
  userToFormValues,
  type UserFormValues,
} from "./userForm"

interface UserFormDialogProps {
  user?: UserListItemDto
  trigger: React.ReactNode
}

const ROLES: Role[] = ["CASHIER", "BAKER", "BRANCH_MANAGER", "GENERAL_MANAGER", "ADMIN"]

/** Create/edit dialog for `admin/users` (plan 07 §5 row 6). Always-on, no module gate. Role is a
 * fixed seeded dropdown (not user-definable, 01 §4); the branch select hides the tenant-wide
 * option for branch-scoped roles (`isBranchScopedRole`) and is otherwise optional. */
export function UserFormDialog({ user, trigger }: UserFormDialogProps) {
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const { session } = useAuth()
  const createUser = useCreateUser()
  const updateUser = useUpdateUser()
  const isEdit = user !== undefined
  const strings = t.admin.users.form
  const branches = session?.branches ?? []

  const schema = useMemo(() => buildUserFormSchema(isEdit), [isEdit])

  const form = useForm({
    resolver: zodResolver(schema),
    defaultValues: user ? userToFormValues(user) : emptyUserFormValues(branches[0]?.id ?? null),
  })

  const defaultBranchId = branches[0]?.id ?? null
  useEffect(() => {
    if (!open) return
    form.reset(user ? userToFormValues(user) : emptyUserFormValues(defaultBranchId))
    setFormError(null)
  }, [open, user, defaultBranchId, form])

  const role = form.watch("role")
  const branchRequired = isBranchScopedRole(role)
  const roleItems = ROLES.map((role) => ({ value: role, label: t.admin.users.roles[role] }))
  const branchItems = [
    ...(branchRequired ? [] : [{ value: TENANT_WIDE_BRANCH_VALUE, label: t.userMenu.branchless }]),
    ...branches.map((branch) => ({ value: branch.id, label: branch.name })),
  ]

  async function onSubmit(values: UserFormValues) {
    setFormError(null)
    try {
      if (isEdit) {
        await updateUser.mutateAsync({ id: user.id, body: toUpsertUserRequest(values) })
      } else {
        await createUser.mutateAsync(toUpsertUserRequest(values))
      }
      setOpen(false)
    } catch (error) {
      setFormError(errorMessage(error, strings.genericError))
    }
  }

  const isSubmitting = createUser.isPending || updateUser.isPending

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger as React.ReactElement} />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? t.admin.users.editUser : t.admin.users.newUser}</DialogTitle>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="username"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="user-username">{strings.usernameLabel}</FieldLabel>
                  <Input
                    {...field}
                    id="user-username"
                    placeholder={strings.usernamePlaceholder}
                    aria-invalid={fieldState.invalid}
                    autoComplete="off"
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="displayName"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="user-display-name">{strings.displayNameLabel}</FieldLabel>
                  <Input
                    {...field}
                    id="user-display-name"
                    placeholder={strings.displayNamePlaceholder}
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="role"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor="user-role">{strings.roleLabel}</FieldLabel>
                  <ChoiceSelect
                    options={roleItems}
                    value={field.value}
                    onValueChange={field.onChange}
                    id="user-role"
                    className="w-full"
                  />
                </Field>
              )}
            />
            <Controller
              name="branchId"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="user-branch">{strings.branchLabel}</FieldLabel>
                  <ChoiceSelect
                    options={branchItems}
                    value={field.value}
                    onValueChange={field.onChange}
                    id="user-branch"
                    className="w-full"
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="password"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="user-password">
                    {isEdit ? strings.passwordLabelEdit : strings.passwordLabel}
                  </FieldLabel>
                  <Input
                    {...field}
                    id="user-password"
                    type="password"
                    autoComplete="new-password"
                    aria-invalid={fieldState.invalid}
                  />
                  {isEdit && <FieldDescription>{strings.passwordHintEdit}</FieldDescription>}
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="pin"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="user-pin">
                    {isEdit ? strings.pinLabelEdit : strings.pinLabel}
                  </FieldLabel>
                  <Input
                    {...field}
                    id="user-pin"
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    aria-invalid={fieldState.invalid}
                  />
                  {isEdit && <FieldDescription>{strings.pinHintEdit}</FieldDescription>}
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
                    <FieldLabel htmlFor="user-active">{strings.activeLabel}</FieldLabel>
                    <Switch
                      id="user-active"
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

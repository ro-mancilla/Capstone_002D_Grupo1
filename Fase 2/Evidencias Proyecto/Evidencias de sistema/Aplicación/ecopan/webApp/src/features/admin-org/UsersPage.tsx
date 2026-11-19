import { useQuery } from "@tanstack/react-query"
import { readOutcome } from "@/lib/readOutcome"
import { ModuleDisabledState } from "@/components/shared/ModuleDisabledState"
import { ReadFailure } from "@/components/shared/ReadFailure"
import { PlusIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { usersQuery } from "@/api/queries/users"
import { useAuth } from "@/features/auth/AuthContext"
import { t } from "@/lib/i18n"
import { UserFormDialog } from "./UserFormDialog"

/** `admin/users` (plan 07 §5 row 6): always-on user CRUD (`USERS_MANAGE`, never module-gated). */
export function UsersPage() {
  const strings = t.admin.users
  const { session } = useAuth()
  const usersResult = useQuery(usersQuery())
  const users = usersResult.data ?? []
  const branchesById = new Map((session?.branches ?? []).map((branch) => [branch.id, branch]))

  // A failed read must not read as an empty bakery (issue #11).
  const outcome = readOutcome(usersResult)
  if (outcome.status === "moduleDisabled") return <ModuleDisabledState />
  if (outcome.status === "failed") return <ReadFailure message={outcome.message} onRetry={outcome.retry} />
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="page-title">{strings.title}</h1>
        <UserFormDialog
          trigger={
            <Button>
              <PlusIcon className="size-4" />
              {strings.newUser}
            </Button>
          }
        />
      </div>

      {usersResult.isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : users.length === 0 ? (
        <p className="text-muted-foreground text-sm">{strings.empty}</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{strings.columns.username}</TableHead>
              <TableHead>{strings.columns.displayName}</TableHead>
              <TableHead>{strings.columns.role}</TableHead>
              <TableHead>{strings.columns.branch}</TableHead>
              <TableHead>{strings.columns.status}</TableHead>
              <TableHead className="text-right">{strings.columns.actions}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id}>
                <TableCell>{user.username}</TableCell>
                <TableCell>{user.displayName}</TableCell>
                <TableCell>{strings.roles[user.role]}</TableCell>
                <TableCell>
                  {user.branchId ? (branchesById.get(user.branchId)?.name ?? "—") : strings.tenantWide}
                </TableCell>
                <TableCell>
                  <Badge variant={user.active ? "success" : "outline"}>
                    {user.active ? strings.statusActive : strings.statusInactive}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  <UserFormDialog
                    user={user}
                    trigger={<Button variant="outline">{t.common.edit}</Button>}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

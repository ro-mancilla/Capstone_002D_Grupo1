import { describe, expect, it } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type { Session, UpsertUserRequest, UserListItemDto } from "@/api/types"
import { storeSession } from "@/api/auth"
import { setSessionTokens } from "@/api/http"
import { AuthProvider } from "@/features/auth/AuthContext"
import { Button } from "@/components/ui/button"
import { UserFormDialog } from "./UserFormDialog"

const EXISTING_USER: UserListItemDto = {
  id: "u2",
  username: "jperez",
  displayName: "Juan Pérez",
  role: "BRANCH_MANAGER",
  branchId: "b1",
  active: true,
}

function session(): Session {
  return {
    userId: "u1",
    tenantId: "t1",
    tenantSlug: "reference",
    branchId: null,
    role: "GENERAL_MANAGER",
    displayName: "Gina",
    tenantName: "Panadería Reference",
    currencyCode: "CLP",
    enabledModules: ["POS_SALES"],
    branches: [{ id: "b1", name: "Centro" }],
  }
}

function renderDialog(user?: UserListItemDto) {
  setSessionTokens({ accessToken: "token", refreshToken: "refresh" })
  storeSession(session())
  const queryClient = new QueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <UserFormDialog user={user} trigger={<Button>Abrir</Button>} />
      </AuthProvider>
    </QueryClientProvider>
  )
}

describe("UserFormDialog (plan 07 §5 row 6)", () => {
  it("never pre-fills password/PIN fields when editing an existing user", async () => {
    renderDialog(EXISTING_USER)
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Abrir" }))
    await screen.findByRole("heading", { name: "Editar usuario" })

    expect(screen.getByLabelText("Nueva contraseña")).toHaveValue("")
    expect(screen.getByLabelText("Nuevo PIN")).toHaveValue("")
  })

  it("requires a branch for a branch-scoped role and omits password/pin from the wire body when left blank on edit", async () => {
    let capturedBody: UpsertUserRequest | null = null
    server.use(
      mswHttp.put(`${API_BASE_URL}/tenant/users/${EXISTING_USER.id}`, async ({ request }) => {
        capturedBody = (await request.json()) as UpsertUserRequest
        return HttpResponse.json({ ...EXISTING_USER, ...capturedBody })
      })
    )

    renderDialog(EXISTING_USER)
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Abrir" }))
    await screen.findByRole("heading", { name: "Editar usuario" })

    await user.click(screen.getByRole("button", { name: "Guardar cambios" }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody!.password).toBeUndefined()
    expect(capturedBody!.pin).toBeUndefined()
    expect(capturedBody!.branchId).toBe("b1")
  })

  it("offers the fixed seeded role list, not a free-text field", async () => {
    renderDialog()
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Abrir" }))
    await screen.findByRole("heading", { name: "Nuevo usuario" })

    await user.click(screen.getByRole("combobox", { name: /Rol/i }))
    for (const label of ["Cajero", "Panadero", "Gerente de sucursal", "Gerente general", "Administrador"]) {
      expect(await screen.findByRole("option", { name: label })).toBeInTheDocument()
    }
  })
})

import { describe, expect, it } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type { LoginRequest } from "@/api/types"
import { renderApp } from "@/test/router-utils"

describe("LoginPage (plan 07 §2/§3)", () => {
  it("renders and posts the real POST /auth/login shape", async () => {
    let capturedBody: LoginRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/auth/login`, async ({ request }) => {
        capturedBody = await request.json()
        return new Response(
          JSON.stringify({
            accessToken: "a",
            refreshToken: "r",
            user: {
              id: "u1",
              displayName: "Gina General Manager",
              role: "GENERAL_MANAGER",
              branchId: null,
            },
            tenant: {
              id: "t1",
              name: "Panadería Reference",
              currencyCode: "CLP",
              enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD"],
            },
            branches: [{ id: "b1", name: "Main" }],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    renderApp("/login")

    const user = userEvent.setup()
    await screen.findByText(/iniciar sesión/i)

    await user.type(screen.getByLabelText(/panadería/i), "reference")
    await user.type(screen.getByLabelText(/usuario/i), "genmgr")
    await user.type(screen.getByLabelText(/contraseña/i), "genmgr123")
    await user.click(screen.getByRole("button", { name: /ingresar/i }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(capturedBody).toEqual({
      tenantSlug: "reference",
      username: "genmgr",
      password: "genmgr123",
    })
  })

  it("shows a friendly rejection for CASHIER/BAKER roles instead of the dashboard", async () => {
    server.use(
      mswHttp.post(`${API_BASE_URL}/auth/login`, () =>
        Response.json({
          accessToken: "a",
          refreshToken: "r",
          user: { id: "u2", displayName: "Cami Cashier", role: "CASHIER", branchId: "b1" },
          tenant: {
            id: "t1",
            name: "Panadería Reference",
            currencyCode: "CLP",
            enabledModules: ["POS_SALES"],
          },
          branches: [{ id: "b1", name: "Main" }],
        })
      )
    )

    renderApp("/login")
    const user = userEvent.setup()
    await screen.findByText(/iniciar sesión/i)

    await user.type(screen.getByLabelText(/panadería/i), "reference")
    await user.type(screen.getByLabelText(/usuario/i), "cashier")
    await user.type(screen.getByLabelText(/contraseña/i), "cashier123")
    await user.click(screen.getByRole("button", { name: /ingresar/i }))

    expect(await screen.findByText(/acceso no disponible/i)).toBeInTheDocument()
  })

  it("shows the invalid-credentials message on 401", async () => {
    renderApp("/login")
    const user = userEvent.setup()
    await screen.findByText(/iniciar sesión/i)

    await user.type(screen.getByLabelText(/panadería/i), "reference")
    await user.type(screen.getByLabelText(/usuario/i), "genmgr")
    await user.type(screen.getByLabelText(/contraseña/i), "wrong-password")
    await user.click(screen.getByRole("button", { name: /ingresar/i }))

    expect(
      await screen.findByText(/usuario, contraseña o panadería incorrectos/i)
    ).toBeInTheDocument()
  })

  it("asks to wait, not to check the password, when the server throttles sign-ins (429)", async () => {
    server.use(mswHttp.post(`${API_BASE_URL}/auth/login`, () => new HttpResponse(null, { status: 429, headers: { "Retry-After": "40" } })))
    renderApp("/login")
    const user = userEvent.setup()
    await screen.findByText(/iniciar sesión/i)

    await user.type(screen.getByLabelText(/panadería/i), "reference")
    await user.type(screen.getByLabelText(/usuario/i), "genmgr")
    await user.type(screen.getByLabelText(/contraseña/i), "whatever")
    await user.click(screen.getByRole("button", { name: /ingresar/i }))

    expect(await screen.findByText(/demasiados intentos/i)).toBeInTheDocument()
    expect(screen.queryByText(/incorrectos/i)).not.toBeInTheDocument()
  })
})

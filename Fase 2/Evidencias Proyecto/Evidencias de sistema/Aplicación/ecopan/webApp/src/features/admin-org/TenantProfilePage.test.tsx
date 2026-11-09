import { describe, expect, it } from "vitest"
import { render, screen } from "@testing-library/react"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import { setSessionTokens } from "@/api/http"
import { t } from "@/lib/i18n"
import { TenantProfilePage } from "./TenantProfilePage"

const TENANT_ID = "0b7c1f0e-5d0a-4a4e-9a57-3c2f6d1f9e11"

describe("TenantProfilePage", () => {
  it("shows the login slug under Identificador and never the tenant UUID", async () => {
    setSessionTokens({ accessToken: "token", refreshToken: "refresh" })
    server.use(
      mswHttp.get(`${API_BASE_URL}/tenant`, () =>
        HttpResponse.json({
          id: TENANT_ID,
          name: "Panadería Reference",
          slug: "reference",
          currencyCode: "CLP",
          enabledModules: [],
          branches: [],
        })
      )
    )

    const { container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <TenantProfilePage />
      </QueryClientProvider>
    )

    const label = await screen.findByText(t.admin.tenant.slugLabel, { selector: "dt" })
    expect(label.textContent).toBe("Identificador")
    expect(label.nextElementSibling?.textContent).toBe("reference")
    expect(container.textContent).not.toContain(TENANT_ID)
  })
})

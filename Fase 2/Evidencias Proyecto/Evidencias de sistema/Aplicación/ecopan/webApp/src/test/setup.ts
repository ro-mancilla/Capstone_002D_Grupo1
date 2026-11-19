import "@testing-library/jest-dom/vitest"
import { afterAll, afterEach, beforeAll } from "vitest"
import { server } from "@/mocks/server"

// import.meta.env.VITE_API_BASE_URL isn't set under vitest by default -- match the MSW fixtures'
// base URL so http.ts's requests actually hit the mocked handlers.
import.meta.env.VITE_API_BASE_URL = "http://localhost:8080"

// jsdom doesn't implement matchMedia -- `lib/theme.ts`'s system-preference detection needs it.
if (!window.matchMedia) {
  window.matchMedia = (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })
}

beforeAll(() => server.listen({ onUnhandledRequest: "error" }))
afterEach(() => {
  server.resetHandlers()
  window.localStorage.clear()
})
afterAll(() => server.close())

import { defineConfig, devices } from "@playwright/test"

const artifacts = process.env.ECOPAN_E2E_ARTIFACTS ?? "../../build/e2e-artifacts"

// The runner (scripts/e2e/run.sh web) serves the dashboard it built against the throwaway API here.
export default defineConfig({
  testDir: "./tests",
  outputDir: `${artifacts}/web/results`,
  // One stack per run, shared state in the ledger: serial keeps every test's data its own.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 60_000,
  reporter: [["list"], ["html", { outputFolder: `${artifacts}/web/report`, open: "never" }]],
  use: {
    baseURL: process.env.ECOPAN_E2E_DASHBOARD ?? "http://localhost:14173",
    locale: "es-CL",
    timezoneId: "America/Santiago",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] }, testIgnore: /api\// },
    // Safari's Web Locks differ; only the cross-tab refresh needs it (#150).
    { name: "webkit", use: { ...devices["Desktop Safari"] }, testMatch: /two-tabs\.spec\.ts/ },
    // Pipeline and storage checks that talk to the API (and stop its dependencies) with no browser; runs last.
    { name: "api", testMatch: /api\/.*\.spec\.ts/, dependencies: ["chromium", "webkit"] },
  ],
})

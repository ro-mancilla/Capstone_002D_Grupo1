# E2E coverage (#145)

Every user story of spec #145 and the test that covers it. A story with no test says so: that is the point of the table. The rows still marked "not yet" are the open work (#149, #159-#162). Baker rows (73-83) run from one shared `commonTest` on the Android emulator and the iOS simulator.

| # | Story | Covered by |
|---|---|---|
| 1 | one command per frontend that brings up the throwaway stack, seeds it and runs that frontend's e2e suite | `scripts/e2e/run.sh web` |
| 2 | the e2e suites never to touch my dev stack, its ports or its volumes (product photos live there) | runner guards (`scripts/e2e/run.sh`) |
| 3 | each run to start from the reference seed plus the demo steps (opening stock, forecasting on) | runner seeding step |
| 4 | a failed run to keep screenshots, the UI hierarchy and the service logs | runner failure artifacts (`build/e2e-artifacts`) |
| 5 | a `--keep` option that leaves the stack up after a run | runner `--keep` |
| 6 | tests to create the data they mutate (their own product, their own batch) | all suites (own data) |
| 7 | a small API helper, shared by all suites, for playing the other side of a journey (push a batch as the baker, record a sale as the cashier, change a price as the manager) | `e2e/web/tests/api-helper.spec.ts` |
| 8 | the suites to find elements by user-visible text and accessibility roles, falling back to stable test tags | all suites (role/text locators) |
| 9 | to sign in with my tenant, username and password | `e2e/web/tests/session.spec.ts` |
| 10 | a wrong password to show the Spanish invalid-credentials message | `e2e/web/tests/session.spec.ts` |
| 11 | a throttled sign-in to say "wait a moment", distinct from a wrong password | `e2e/web/tests/session-live.spec.ts` |
| 12 | to stay signed in across a page reload | `e2e/web/tests/session-live.spec.ts` |
| 13 | live updates to resume right after a reload | `e2e/web/tests/session-live.spec.ts` |
| 14 | a batch pushed by a baker to raise a "Lote listo" notice naming the product, dough weight and branch | `e2e/web/tests/session-live.spec.ts` |
| 15 | "Ver producción" on that notice to open the production page for that branch and product | `e2e/web/tests/session-live.spec.ts` |
| 16 | to see "En vivo en pausa" while the backend is unreachable and see it clear when it comes back, with the data refreshed | `e2e/web/tests/session-live.spec.ts` |
| 17 | both to keep working across a token refresh | `e2e/web/tests/two-tabs.spec.ts` |
| 18 | my session to end cleanly (back to the sign-in page) when my refresh token is revoked | `e2e/web/tests/session-live.spec.ts` |
| 19 | to sign out | `e2e/web/tests/session.spec.ts` |
| 20 | the analytics overview to show today's sales, revenue and production for my scope | `e2e/web/tests/session.spec.ts`, `e2e/web/tests/analytics.spec.ts` |
| 21 | the Ventas page to show sales by day and by product type over a chosen range and branch | `e2e/web/tests/analytics.spec.ts` |
| 22 | the Producción page to list batches for a day and branch | `e2e/web/tests/analytics.spec.ts` |
| 23 | the Merma page to show the waste estimate | `e2e/web/tests/analytics.spec.ts` |
| 24 | the reconciled inventory report for a day to balance opening + movements = closing per ingredient | `e2e/web/tests/inventory.spec.ts` |
| 25 | the Pronóstico page to show forecast vs. actual per product, labelled *Modelo* or *Línea base*, with *Precisión últimas 4 semanas* | `e2e/web/tests/analytics.spec.ts` |
| 26 | to request a forecast refresh | `e2e/web/tests/analytics.spec.ts` |
| 27 | to filter analytics by branch and date range (today, 7 days, 30 days, custom) | `e2e/web/tests/analytics.spec.ts` |
| 28 | analytics and forecasts limited to my own branch, with no way to pick another | `e2e/web/tests/analytics.spec.ts` |
| 29 | to create, edit and deactivate products (Tipo, sold by weight or unit, prices, barcode) | `e2e/web/tests/catalog.spec.ts` |
| 30 | to upload a product photo and see it on the product | `e2e/web/tests/catalog.spec.ts` |
| 31 | an oversized or non-image photo to be refused with a Spanish message while the old photo stays | `e2e/web/tests/catalog.spec.ts` |
| 32 | to remove a product photo | `e2e/web/tests/catalog.spec.ts` |
| 33 | to see Almacén product stock per branch and record a stock count | `e2e/web/tests/catalog.spec.ts` |
| 34 | to create and edit ingredients and their low-stock thresholds | `e2e/web/tests/inventory.spec.ts` |
| 35 | to record an ingredient purchase or a manual adjustment, and see the stock and movement list update | `e2e/web/tests/inventory.spec.ts` |
| 36 | to edit a product's formula (baker's percentages) | `e2e/web/tests/inventory.spec.ts` |
| 37 | to see the stock states (OK / *Stock bajo* / *Stock negativo*) per ingredient and branch | `e2e/web/tests/inventory.spec.ts` |
| 38 | to create, edit and deactivate users with a role and branch | `e2e/web/tests/organization.spec.ts` |
| 39 | to create and rename branches | `e2e/web/tests/organization.spec.ts` |
| 40 | to edit tenant details (name, currency display) | `e2e/web/tests/organization.spec.ts` (display only: the tenant page has no edit form, so editing is a product gap) |
| 41 | to turn modules on and off and see navigation and guarded pages follow immediately | `e2e/web/tests/organization.spec.ts` |
| 42 | a page whose module is off to explain that, rather than error | `e2e/web/tests/organization.spec.ts` |
| 43 | light and dark themes to work on every page | `e2e/web/tests/sweep.spec.ts` |
| 44 | every screen in Spanish with no raw codes or ids | `e2e/web/tests/sweep.spec.ts` |
| 45 | the dashboard usable with the collapsed sidebar | `e2e/web/tests/sweep.spec.ts` |
| 46 | to enroll it with the bakery, a manager's username and password, and a device name | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/TillTracerE2eTest.kt` |
| 47 | enrollment's error states (wrong credentials, needs a connection, too many attempts) shown as clear alerts | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/EnrollmentE2eTest.kt` |
| 48 | the first sync's progress shown during enrollment | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/EnrollmentE2eTest.kt` |
| 49 | to pick myself from the staff list and unlock with my PIN | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/PinLockE2eTest.kt` (the lock screen is driven after `SessionProvider.lock()`: nothing in the app locks the till, so it cannot be reached from the UI today: a product gap) |
| 50 | a wrong PIN to show an error, and five wrong PINs to lock me out for a while | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/PinLockE2eTest.kt` |
| 51 | to switch to another staff member without re-enrolling the till | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/PinLockE2eTest.kt` (the lock screen is driven after `SessionProvider.lock()`: nothing in the app locks the till, so it cannot be reached from the UI today: a product gap) |
| 52 | to browse products by category and search by name | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` |
| 53 | to sell a product by weight: tap it, read the weight (manual entry or simulated scale), add the line with the right price | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` (manual scale sells by typed weight; the simulated scale shows a stable reading and an armed Agregar, but clicking Agregar on the demo scale does not add the line yet: to investigate) |
| 54 | to sell a product by unit and step its quantity up and down | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/TillTracerE2eTest.kt`, `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` |
| 55 | to scan a barcode to add its product | not yet (the desktop test dispatcher injects key events with no characters, so a wedge scanner burst cannot be sent) |
| 56 | to see an Almacén stock badge on products and be warned when stock is low or negative | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` |
| 57 | to void a line or discard the whole sale | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` |
| 58 | to charge in cash with tendered amount, quick-cash chips and change due | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/TillTracerE2eTest.kt`, `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` |
| 59 | to charge by card through the simulated terminal (approve or decline) | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` |
| 60 | to charge by transfer or other method | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` |
| 61 | a receipt after each sale and the option to reprint it | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` (receipt shown; the app offers Reimprimir only after a failed print, which no driver here fails yet) |
| 62 | to open and close the till (drawer and session) | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SellingE2eTest.kt` |
| 63 | sales recorded offline to queue and sync once the backend is back, with the sync indicator showing pending → synced | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SyncE2eTest.kt` |
| 64 | a sale made on the till to appear in the dashboard's Ventas page | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SyncE2eTest.kt` |
| 65 | a batch logged by a baker to appear as a batch-ready notice on the POS screen | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SyncE2eTest.kt` |
| 66 | a price changed on the dashboard to reach the till on the next sync | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SyncE2eTest.kt` |
| 67 | a throttled or failing backend to show offline/retrying, not "Requiere atención", and to sync on its own once it recovers | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SyncE2eTest.kt` |
| 68 | As a cashier, when the device session is genuinely revoked, I want the till to ask a manager to reconnect (username and password) without losing queued sales | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SyncE2eTest.kt` |
| 69 | rejected operations listed in Settings with their reason, and to retry or discard each | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SyncE2eTest.kt` |
| 70 | to configure the scale (manual, simulated, serial with brand, port and baud), the printer and the drawer in Settings | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SettingsE2eTest.kt` (scale: manual, simulated and a serial draft that is never applied; printer and drawer pickers are listed but not driven yet) |
| 71 | the scripted demo mode to drive the simulated hardware | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SettingsE2eTest.kt` (the simulated scale, card terminal, drawer and on-screen receipt are exercised across the selling and settings journeys) |
| 72 | to switch light/dark theme and see the About information (version, bakery, branch, device) | `composeApp/src/jvmTest/kotlin/com/f776/ecopan/e2e/SettingsE2eTest.kt` |
| 73 | to enroll my phone and unlock with my PIN, exactly as on the till | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerTracerE2eTest.kt` (enrollment on the emulator; the PIN unlock is the till journey of #156 and is not repeated on the phone) |
| 74 | to pick a Panadería product, type the dough weight (Kg de masa) on the keypad, confirm, and see the batch logged | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerTracerE2eTest.kt` |
| 75 | Almacén products not offered for production | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerJourneysE2eTest.kt` |
| 76 | *Producción sugerida* for the day per product, expandable | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerJourneysE2eTest.kt` |
| 77 | to open the stock glance from Producción and see ingredient stock with its states | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerJourneysE2eTest.kt` |
| 78 | a batch logged offline to queue and sync when the phone is back online, with the sync indicator showing it | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerSyncE2eTest.kt` (on Android the pill reads "Requiere atención" while the server is down and the network is up, by design in `SyncEngineImpl`; the till reads "Sin conexión") |
| 79 | my logged batch to decrement the ingredients by the formula, visible on the dashboard's ingredient stock | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerJourneysE2eTest.kt` (25 kg of Marraqueta takes ~14,9 kg of flour off the branch stock, read from the API) and `e2e/web/tests/inventory.spec.ts` |
| 80 | my batch to reach the till and the dashboard as "batch ready" | partly: the phone's batch reaches the backend (`BakerTracerE2eTest.kt`); "batch ready" on the dashboard and the till is covered for a batch pushed through the API (`e2e/web/tests/session-live.spec.ts`, `SyncE2eTest.kt`), not from a phone |
| 81 | a throttled backend to never make me sign in again | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerSyncE2eTest.kt` |
| 82 | rejected batches listed in Settings with retry and discard | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerSyncE2eTest.kt` (a batch and its four ingredient movements are refused together) |
| 83 | Settings to show theme, notification/alarm permission status, and About | `mobile/e2e/src/commonTest/kotlin/com/f776/ecopan/mobile/e2e/BakerJourneysE2eTest.kt` (the alarm permission row does not exist on this phone, so only notifications are asserted) |
| 84 | the phone restored from a backup or transfer to start signed out | dropped: backup and restore are out of scope for this product |
| 85 | As the team, I want every feature listed above to have at least one e2e test, and a coverage table mapping feature → test | this table (kept by `e2e/cover.py <test> <stories>`); the audit of the open rows is #162 |
| 86 | As the team, I want the e2e suites documented in the README and CLAUDE.md commands section | `docs/e2e/README.md`, the README and the CLAUDE.md commands |

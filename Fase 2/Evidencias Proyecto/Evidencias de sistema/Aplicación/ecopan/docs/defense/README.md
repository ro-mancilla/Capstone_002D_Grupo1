# Defense pack (plan 11, DEMO-2 and DEMO-3)

`/spec/` is gitignored local scratch, so the plan's `spec/defense/` could never hold a tracked
checklist. This directory is its tracked home.

| Piece | Where | State |
|---|---|---|
| Run sheet (≈ 17 min) | [`../DEMO.md`](../DEMO.md) | written |
| One-command demo state | `scripts/demo-up.sh` (never `docker compose down -v`: it deletes the product photos) | written |
| SSE end-to-end probe (INFRA-6) | `scripts/verify-sse.sh [base-url]` | **passes on the local stack**; the run through Azure's ingress was dropped on 2026-10-04 (the dashboard falls back to 60 s polling) |
| Evidence pack | `scripts/evidence-pack.sh [--run]` → `docs/defense/evidence/` (gitignored, regenerate on demand) | generated from the repo |
| Evaluation record | [`../../ml/RESULTS.md`](../../ml/RESULTS.md) (copied into the pack) | pre-registered, scored |
| Screen recording | a person records it | **not done** |
| Timed rehearsals ×2, including the Azure re-up and the local fallback | a person runs them; log below | **not done** |

## What the defence claims (and does not)

- **Forecast accuracy.** The +36.7% … +45.4% gain over the baselines (five evaluation windows,
  `ml/RESULTS.md` §4.4) is measured on Corporación Favorita series the model was trained on. It is
  not a claim about the bakery. The pre-registered transfer experiment to unseen, gram-scale series
  **failed its bar** (§4.7: WAPE 0.975 for the model against 0.192 for the baseline). So every bakery
  series is served by the labelled *Línea base*, and the screen says so on each row. Quote §4.7
  for the bakery, never §4.4.
- **Waste.** Reduction is a *potential* benefit, not a measured one: there is no before/after at
  the client and the demo history is seeded. What the system does is make waste visible (the
  *Mermas* estimate) and offer a next-day suggestion (*Producción sugerida*). No percentage is
  claimed.

## Hardware in the demo

No physical scale, receipt printer or cash drawer has been validated. Every one of them has a mock
or manual driver that is complete and tested, and that is what the demo runs on:

| Interface | What the demo uses | Real driver, not yet validated on a device |
|---|---|---|
| Scale | `ManualScale` (keypad) or `MockScale` (simulated, scripted weights per product) | `SerialScale` |
| Receipt printer | `MockPrinter` (simulated: the ESC/POS receipt `EscPosPrinter` would send, previewed on screen) | `EscPosPrinter` |
| Cash drawer | `MockDrawer` (simulated: "Cajón abierto (simulado)" on a cash sale) | `PrinterKickDrawer` |
| Payment terminal | `SimulatedTerminal`, permanently | none planned |

The scripted demo mode is simulated end to end and says so on screen; it is not hardware
validation (details in `docs/hardware/supported-devices.md`).

`scripts/evidence-pack.sh` writes `mocks.txt` straight from the driver registry, so this table
cannot drift from the code. Tickets that explicitly need the physical device stay deferred until
one is available; the code behind them is built and exercised against mocks and a synthetic
capture (`docs/hardware/scale-protocol-notes.md`).

## Evidence checklist

- [ ] Recording of the full run sheet (screen capture, three clients visible)
- [x] Test-name slide: `evidence/test-names.txt`
- [x] Configuration diff of the two seeded bakeries: `evidence/config-diff.txt`
- [x] Evaluation record: `evidence/results.md`
- [x] Mock/real driver inventory: `evidence/mocks.txt`
- [x] SSE probe on the local stack (`scripts/verify-sse.sh`); the deployed-stack run is dropped

## Rehearsal log

Fill one row per full run of `docs/DEMO.md`. The acceptance bar is ≤ 30 min for the whole sheet,
with the local-compose fallback proven once.

| Date | Stack | Total time | Slowest beat | Notes |
|---|---|---|---|---|
| | | | | |

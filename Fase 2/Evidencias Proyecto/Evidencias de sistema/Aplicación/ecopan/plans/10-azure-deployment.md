# 10 — Azure Deployment (capstone-scale)

Depends on: `01` (binding), `02-backend.md` (Ktor modules, Settings), `07-web-dashboard.md` (Vite build), `08-ml-forecasting.md` (FastAPI service). Zero production code here — Dockerfile/Bicep/compose fragments are illustrative contracts for implementation agents.

**Design stance:** cheap, simple, reproducible. This is a capstone: one environment, one region, no HA, no staging slots, no Front Door/APIM/VNet peering. Everything must be tear-down-and-re-up-able from the repo in <30 min because the subscription is a student/personal one and the stack only needs to be live for dev pushes and the defense.

---

## 1. Topology

One resource group (`rg-ecopan`), one Azure Container Apps (ACA) environment, one ACR, one PostgreSQL Flexible Server, one Static Web App.

| Workload | Hosting | Ingress | Replicas | Notes |
|---|---|---|---|---|
| `backend-app` | Container App | **External** (public HTTPS), HTTP + SSE | 1 (min1/max1) | See §1.1 SSE config |
| `backend-worker` | Container App | None | 1 (min1/max1) | Pulls from RabbitMQ; no scale-to-zero (broker consumer must stay up) |
| `backend-notifier` | Container App | **Internal** | **1 fixed** | SSE fanout state is in `backend-app`'s registry; notifier just aggregates + publishes to `SsePushBroker` — single instance keeps aggregation trivially correct |
| `rabbitmq` | Container App | **Internal**, TCP 5672 | **1 fixed** | §2 — deliberate no-HA tradeoff |
| `ml-service` | Container App | **Internal** (only backend-worker calls it per `02` §7; clients read the `forecasts` cache via the backend's F1 endpoint) + optionally expose `/health` via backend-app proxy | 1, may scale to zero if cold-start acceptable — default min1 for demo safety | Python FastAPI, `ml/` |
| `webApp` | **Azure Static Web Apps** (Free tier) | Public HTTPS | n/a | §5 |
| PostgreSQL | Azure Database for PostgreSQL **Flexible Server**, Burstable `B1ms` | Public access + firewall allowlist (ACA outbound IP + dev IPs) | single AZ | Private endpoint/VNet integration rejected: cost + complexity for zero capstone value |

Consumption workload profile for all Container Apps. No dedicated plans.

### 1.1 SSE on backend-app (must not be skipped)

`GET /events/sse` holds long-lived connections. ACA ingress requirements:

- **Transport:** `http` (auto is fine; SSE is plain HTTP/1.1 chunked — do not set `tcp`).
- **Sticky sessions:** set ingress `stickySessions.affinity: sticky` — but the real mitigation is **min=max=1 replica** for `backend-app`, so every SSE client lands on the instance that owns the in-memory `SseConnectionRegistry`. Scaling backend-app beyond 1 would require moving the registry behind RabbitMQ fanout per instance — explicitly out of capstone scope; record as a known limit.
- **Idle timeout:** ACA ingress idle timeout defaults around 4 min for request timeout on Envoy; keep-alive comments (`: ping` every ~25 s) must be emitted by the Ktor SSE endpoint (implementation agent: verify Ktor SSE heartbeat API via context7). Client (`webApp` EventSource, POS SSE client) must auto-reconnect with `Last-Event-ID`.
- Ingress `allowInsecure: false`; ACA-provided `*.azurecontainerapps.io` TLS cert — no custom domain needed.

## 2. Broker decision: RabbitMQ as a Container App (DECIDED)

**Decision:** run `rabbitmq:4-management-alpine` (implementation agent pins exact tag) as a single-replica internal Container App. No mirrored queues, no quorum, no persistent volume by default.

- **Why not Azure Service Bus:** the template's broker layer (`backend/data` Kourier clients) speaks **AMQP 0-9-1**, which is RabbitMQ's protocol. Service Bus speaks AMQP 1.0 + its own SDK semantics; swapping means rewriting every broker client and the exchange/queue topology in `backend:app/worker/notifier`. Broker portability is not a thesis claim; spending code on it buys nothing. **Rejected — recorded.**
- **Why single replica / no HA is acceptable:** the broker carries *transient work signals* (batch-to-process, completion events, SSE push events), not the system of record. All durable facts (sales, batches, stock movements) are in Postgres via the sync ingest before any message is published (`01` §8). If RabbitMQ restarts and drops in-flight messages, worst case: a formula decrement or SSE push is missed — the decrement is the designated cut-line (degrades to manual adjustment) and SSE is best-effort notification. Restart = re-drive by re-publishing (admin) or just moving on. State that tradeoff in the defense if asked.
- Optional hardening if flaky in practice: mount an Azure Files volume for `/var/lib/rabbitmq` + declare queues durable. Do only if a real problem appears.
- Credentials: default user/pass set via ACA secrets (`RABBITMQ_DEFAULT_USER/PASS`), consumed by backend services as env vars. Management UI (15672) stays internal; reach it via `az containerapp exec` or temporary port exposure only.

## 3. Images & Dockerfiles (planned, per service)

All images built from repo Dockerfiles, pushed to ACR (`Basic` SKU). One Dockerfile per deployable:

- **`backend/app/Dockerfile`, `backend/worker/Dockerfile`, `backend/notifier/Dockerfile`** — identical shape:
  - Stage 1 `gradle`-capable builder (use `eclipse-temurin:17-jdk` + Gradle wrapper, or `gradle:8-jdk17`): `./gradlew :backend:app:installDist` (Ktor application plugin's `installDist` — verify task name via context7; the template modules use the Ktor/application plugin).
  - Stage 2 `eclipse-temurin:17-jre-alpine` (or `-jammy` if Netty/native libs complain): copy `build/install/<module>/`, `ENTRYPOINT ["bin/<module>"]`.
  - Build context = repo root (needs `backend/data` + `settings.gradle.kts`); `.dockerignore` excludes client modules to keep context small.
- **`ml/Dockerfile`** — uv-based:
  - `FROM python:3.12-slim`, install `uv` (copy from `ghcr.io/astral-sh/uv` distroless image — verify current recommended pattern via context7), `uv sync --frozen --no-dev`, `CMD ["uv", "run", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]`. Model artifact baked into the image (it's small, capstone) — no blob storage.
- No Dockerfile for `webApp` (SWA builds it) — but keep an optional `webApp/Dockerfile` (nginx serving `dist/`) documented as the fallback if SWA fights back (§5).

Tag scheme: `acr.azurecr.io/ecopan/<service>:<git-sha>` + `:latest`.

## 4. Config & secrets

Each backend module reads `Settings` from env vars (implementation agent: `02` defines the Settings classes; env names below are the contract). ACA **secrets** hold sensitive values; env vars reference them via `secretRef`.

| Env var | Services | Source |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (per `02` §7) | app, worker, notifier | host/port/name plain env; user/password ACA secret (Postgres admin or app user) |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | app, worker, notifier | host = internal ACA FQDN of the rabbitmq app; creds = ACA secret |
| `JWT_SECRET` | app | ACA secret — random 256-bit, generated at provision, HS256 (per `02`) |
| `JWT_ISSUER`, `JWT_AUDIENCE`, `ACCESS_TOKEN_TTL_MIN`, `REFRESH_TOKEN_TTL_DAYS` | app | plain env |
| `REPORTING_TZ` (per `02` §7 — analytics day bucketing) | app | plain env (`America/Santiago`) |
| `ML_BASE_URL`, `ML_API_KEY` (per `02` §7 — the **worker** calls the ML service) | worker | base URL = internal FQDN of ml-service; key = ACA secret |
| `DECREMENT_ENABLED` (per `02` §7 — the decrement cut-line switch) | worker | plain env (`true`) |
| `SEED_PROFILE` (boot-time seed, `02` §7 — **comma-separated profile list**, e.g. `panaderia-centro,unipan`; idempotent at startup, no seed endpoint exists per `01` §7) | app | plain env; see `11-seeds-and-demo.md` §1 |
| `MODEL_PATH` | ml | plain env |

Key Vault is **not** used — ACA secrets suffice at this scale; one less resource, one less RBAC dance. Recorded tradeoff.

## 5. webApp hosting — Azure Static Web Apps (DECIDED)

- **SWA Free tier**, deployed from `webApp/dist` (Vite build). Recommended over an nginx Container App: free, global CDN, built-in SPA fallback config (`staticwebapp.config.json` with `navigationFallback` → `/index.html` for TanStack Router), zero container upkeep.
- **API base URL is baked at build time** via Vite env: `VITE_API_BASE_URL=https://<backend-app-fqdn>` passed to `npm run build` in the deploy workflow. No runtime config injection needed for one environment. SSE (`EventSource`) hits the backend FQDN directly — backend-app must send permissive-enough CORS for the SWA origin (contract for `02`: CORS allowlist env var `CORS_ORIGINS`).
- Fallback (only if SWA linking/tooling misbehaves): nginx Container App serving `dist/` with `try_files ... /index.html`.

## 6. IaC — azd + Bicep (DECIDED, pick-one)

**Decision: Azure Developer CLI (`azd`) with a Bicep `infra/` folder** at repo root. One tool provisions + deploys + tears down; ideal for the "re-up for the defense" story. Alternative (raw `az deployment group create` + shell scripts) rejected: azd gives `azd up` / `azd down` for free and handles ACR build+push wiring.

Planned layout (implementation agent verifies current azd conventions with the azure best-practices tooling before writing):

```
azure.yaml            # azd services: backend-app, backend-worker, backend-notifier, ml (host: containerapp)
infra/
  main.bicep          # subscription→rg scope, params: location, postgresPassword, jwtSecret
  main.parameters.json
  modules/ (or resources.bicep): acr, aca-env + log analytics, rabbitmq containerapp,
    postgres-flexible (B1ms, 32GB, PG17 — match `02` §7's local compose), containerapps x4, swa
```

Seed data + migrations run as app-level tasks, not IaC (Exposed migrations on app boot per `02`; seeds load at boot from `SEED_PROFILE` per `01` §7 / `11` §1 — demo reset = restart `backend-app`, e.g. `az containerapp revision restart`).

## 7. Local dev parity — docker-compose

Reconciled with `02-backend.md` §7 — `02` owns the canonical compose file; this section defers to it. Root `docker-compose.yaml`:

- `postgres:17-alpine` (per `02` §7) — port 5432, volume, `POSTGRES_DB=ecopan`.
- `rabbitmq:4-management-alpine` — 5672 + 15672.
- `ml` (optional profile `--profile ml`): builds `ml/Dockerfile` — most backend dev doesn't need it.
- Backend modules run from the IDE/Gradle against compose infra (not containerized locally — faster loop). `.env.example` mirrors §4 env names exactly so local and Azure config are the same shape.

## 8. CI — GitHub Actions (minimal, deploy stays manual)

- **`ci.yml`** (on PR + push to main): JDK 17 + Gradle cache → `./gradlew build` scoped to backend modules + root `test` per CLAUDE.md; `webApp`: `npm ci && npm run build && npm test`; `ml`: `uv sync && uv run pytest`. Matrix/split as needed for time.
- **`deploy.yml`** (`workflow_dispatch` **only** — manual by design; a capstone doesn't need CD, and manual deploy avoids burning credit on every push): login via OIDC federated credential (no long-lived SP secret) → `azd deploy` (or per-service: `az acr build` per Dockerfile → `az containerapp update --image`) → build webApp with `VITE_API_BASE_URL` → SWA deploy action.
- No infra apply in CI — `azd provision` is run from a laptop, deliberately.

## 9. Cost & teardown

Rough monthly, single region (e.g. `eastus2` / `brazilsouth`), running 24/7:

| Resource | Tier | ~USD/mo |
|---|---|---|
| PostgreSQL Flexible | B1ms burstable + 32 GB | 13–18 |
| Container Apps (5 apps, min1, 0.25 vCPU/0.5 Gi each, consumption) | | 20–45 |
| ACR | Basic | 5 |
| Static Web App | Free | 0 |
| Log Analytics (ACA env default) | PAYG, low volume | 0–5 |
| **Total** | | **~40–75** |

**Teardown/re-up strategy:** `azd down --purge` when idle; `azd up` restores full demo state on its own — `SEED_PROFILE` loads both profiles idempotently at `backend-app` boot (`11` §1); a mid-demo reset is just a container restart (rehearse it so the duration is known, per `01` §7). Target: cold to demo-ready < 30 min, rehearsed once before the defense (work item below). Do NOT keep the stack up between dev pushes and defense week. Optionally stop (not delete) Postgres between sessions (`az postgres flexible-server stop`) — burstable servers can be stopped up to 7 days.

## 10. Work items

1. **INFRA-1:** `infra/` Bicep + `azure.yaml`; `azd provision` succeeds from clean subscription. *AC: all resources appear; `azd down --purge` leaves nothing.*
2. **INFRA-2:** Dockerfiles for app/worker/notifier (installDist pattern) + ml (uv). *AC: `docker build` each from repo root; images run locally against compose infra.*
3. **INFRA-3:** RabbitMQ Container App reachable from backend apps via internal FQDN. *AC: worker consumes a message published by app in Azure.*
4. **INFRA-4:** Secrets/env wiring per §4 for all services. *AC: no secret appears in Bicep params files committed to git (use `@secure()` + azd env).*
5. **INFRA-5:** SWA deploy with baked API base URL + CORS on backend-app. *AC: dashboard login works from the public SWA URL.*
6. **INFRA-6:** SSE through ACA ingress with heartbeats + reconnect. *AC: **full stack reachable at public URL; an SSE event is visible in the web dashboard from a synced baker batch** (batch pushed from mobile/emulator → worker decrement → notifier → SSE → dashboard toast/feed).* ← the headline acceptance test.
7. **INFRA-7:** `ci.yml` green on PR; `deploy.yml` manual run deploys all services end-to-end.
8. **INFRA-8:** Teardown/re-up rehearsal: `azd down` → `azd up` → seed → demo-ready, timed, documented in a short runbook (goes in `11`'s demo prep).

## Open questions

- Region: pick by latency-to-Chile (`brazilsouth`) vs. price (`eastus2`)? Default `eastus2` unless demo latency is noticeably bad.
- Does the ml-service need public `/health` for the defense narrative, or is the dashboard forecast chart evidence enough? Default: internal only.
- Azure for Students credit vs. PAYG subscription — affects whether B1ms Postgres or ACA consumption quotas bite; confirm before INFRA-1.
- Exposed migration strategy on boot (auto `SchemaUtils` vs. versioned migrations) is owned by `02` — deployment only needs to know it's boot-time and idempotent. Flag if `02` chooses otherwise.
- Persistent volume for RabbitMQ: only if dropped-message pain shows up during integration; decide by INFRA-3.

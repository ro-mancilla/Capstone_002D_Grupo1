# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root, or
- **`CONTEXT-MAP.md`** at the repo root if it exists: it points at one `CONTEXT.md` per context. Read each one relevant to the topic.
- **`docs/adr/`**: read ADRs that touch the area you're about to work in. In multi-context repos, also check `src/<context>/docs/adr/` for context-scoped decisions.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## File structure

Single-context repo (most repos):

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

Multi-context repo (presence of `CONTEXT-MAP.md` at the root):

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← system-wide decisions
└── src/
    ├── ordering/
    │   ├── CONTEXT.md
    │   └── docs/adr/                  ← context-specific decisions
    └── billing/
        ├── CONTEXT.md
        └── docs/adr/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders), but worth reopening because…_

## Existing primary sources

`CONTEXT.md` and `docs/adr/` don't exist yet. Until they do, these carry the
project's real language and decisions — read them rather than inferring domain
terms from code:

- `plans/00`–`11` — the design docs the implementation follows; code comments
  cite them as e.g. "plan 07 §4"
- `spec/SPECS.md` — the product spec
- `PROGRESS.md` — built vs. still promised
- `CODING_STYLE.md` — authoritative for KMP client code
- `ml/RESULTS.md` — the evaluation record, under pre-registration discipline

## One context, one glossary

ecopan is five stacks but one domain. The KMP clients, `backend`, and `webApp`
share a single bakery vocabulary — product, ingredient, formula, production
batch, stock movement, sale — differing only in serialization shape
(`Product` / `ProductTable` / `ProductDto`). Treat them as one context; don't
write a second glossary for a DTO.

`ml` is the one genuine language boundary: it speaks series, origins, horizons,
and backtest windows, and deliberately uses no bakery nouns. `POST /forecast` is
the translation layer, with `ForecastRefreshProcessorImpl` bucketing sale lines
into reporting-timezone demand days on the way in. Record forecasting terms as a
marked section of the root `CONTEXT.md`.

If a word ever means two things across that boundary, that's the signal to split
into `CONTEXT-MAP.md` + per-context glossaries — not before.

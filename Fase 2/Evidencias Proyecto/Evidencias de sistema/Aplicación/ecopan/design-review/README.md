# design-review

UI review passes against running builds, with the screenshots each one is based on.

| Report | Client | Captured via | Date |
|---|---|---|---|
| [REPORT-desktop-pos.md](REPORT-desktop-pos.md) | Desktop POS till (`:jvm:app`) | `compose-hot-reload` MCP | 2026-09-06 |
| [REPORT-mobile-baker.md](REPORT-mobile-baker.md) | Baker mobile (`:android:app`, phone + tablet) | `android` CLI | 2026-09-06 |

```
screenshots/
  desktop-pos/     # 01–10, referenced by REPORT-desktop-pos.md
  mobile-baker/    # 00–20, referenced by REPORT-mobile-baker.md
```

Both passes ran against the local `docker compose` stack with seed data. Neither covers the web
dashboard (`webApp`) or iOS.

## Findings both clients share

These are `core/ui` or data-layer issues, so a fix lands on both clients at once:

- **`secondary` and `outline` carry surface-weight tones** — every Material component that uses them
  as a foreground loses contrast. Mobile: navigation label 1.3:1, keypad outline 1.1:1, switch thumb
  1.2:1.
- **Batches are labelled with the product category, not the product name** — mobile shows it in the
  batch history, the till shows it in the batch-ready banner.
- **Mixed serif/monospace typefaces with no stated rule**, flagged independently in both passes.
- **The idle sync state reads "Requiere atención" in alarm red** before anything has gone wrong.

The desktop report's inconsistent-`Cerrar` finding does **not** reproduce on mobile: both mobile
dialogs use the plain-text treatment.

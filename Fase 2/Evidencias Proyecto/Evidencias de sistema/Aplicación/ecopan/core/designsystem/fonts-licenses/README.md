# Font licences — Compose clients

The `.ttf` files in `../src/commonMain/composeResources/font/` are redistributed with this
repository and inside every APK, desktop bundle and iOS framework it produces. Both families are
licensed under the **SIL Open Font License 1.1**, which requires the licence and its copyright
notice to travel with the font — so they live here rather than being linked to.

| Family | Files | Licence | Copyright |
|---|---|---|---|
| Outfit | `Outfit-Variable.ttf` (weights 100–900; 400, 500, 600 and 700 are used) | [OFL 1.1](Outfit-OFL.txt) | 2021 The Outfit Project Authors |
| IBM Plex Mono | `IBMPlexMono-{Regular,Medium,SemiBold,Bold}.ttf` | [OFL 1.1](IBMPlexMono-OFL.txt) | 2017 IBM Corp. |

Outfit is the one interface family, the same as the web dashboard (`@fontsource-variable/outfit`).
IBM Plex Mono is only for data that must line up: timestamps, ids, receipt lines. Outfit is bundled
as the upstream variable font (`google/fonts`, `ofl/outfit/Outfit[wght].ttf`) and instanced per weight
with `FontVariation`; Google Fonts publishes no static Outfit files.

Two obligations worth knowing before touching these:

- **A renamed or modified font must not keep a Reserved Font Name.** Neither family reserves a name
  today, but a subset or re-hinted derivative of one that does has to ship under a different name.
- **The licence text is not optional and not summarisable.** If a font is added to
  `composeResources/font/`, its `OFL.txt` (or equivalent) is added here in the same change.

# Font licences — web dashboard

These fonts arrive as `@fontsource*` npm packages, so their licences are present in
`node_modules` during development. That is not enough on its own: `npm run build` **inlines the
`.woff2` files into `dist/`**, and `dist/` is what gets deployed — at which point the fonts are
being redistributed without `node_modules` alongside them. Copies live here so the licence travels
with the artifact.

Both are **SIL Open Font License 1.1**.

| Family | Package | Used for | Licence |
|---|---|---|---|
| Outfit | `@fontsource-variable/outfit` | the whole interface, display through label (`--font-sans`) | [OFL 1.1](Outfit-OFL.txt) |
| IBM Plex Mono | `@fontsource/ibm-plex-mono` | ledger runs — timestamps, ids, key/value rows (`--font-mono`) | [OFL 1.1](IBMPlexMono-OFL.txt) |

Regenerate after changing the font set:

```shell
cp node_modules/@fontsource-variable/<family>/LICENSE licenses/<Family>-OFL.txt
```

The Compose clients bundle the same two families (Outfit + IBM Plex Mono) and carry their own
copies in `core/designsystem/fonts-licenses/`.

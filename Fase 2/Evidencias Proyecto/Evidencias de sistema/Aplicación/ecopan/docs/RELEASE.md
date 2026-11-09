# Release builds

What a release build needs to say, how to sign it, and how to check what came out. The release rules
and the guard below are what stop a build pointed at `localhost`, or carrying the demo sign-in, from
reaching anyone.

## Android (signed, minified)

```shell
eval "$(scripts/make-demo-keystore.sh)"      # a THROWAWAY key (public password); real keys come from CI secrets
./gradlew :android:app:assembleRelease \
  -Pecopan.backendUrl=https://ecopan-app.azurewebsites.net \
  -Pecopan.devQuickLogin=false \
  -Pecopan.requireSigning=true
scripts/verify-release.sh --apk android/app/build/outputs/apk/release/app-release.apk \
  --expect-url https://ecopan-app.azurewebsites.net
```

`checkReleaseConfiguration` runs before `assembleRelease` / `bundleRelease` and lists every problem at once:

| Problem | Say |
|---|---|
| Backend is the development default (`http://localhost:8080`) | `-Pecopan.backendUrl=https://<host>`, or `-Pecopan.allowLocalhostRelease=true` for a local rehearsal over `adb reverse` |
| `ecopan.devQuickLogin` not set (it defaults to ON: one-tap sign-in with the seeded passwords) | `-Pecopan.devQuickLogin=false`, or `=true` to keep it on purpose |
| Unsigned, with `-Pecopan.requireSigning=true` | set `ECOPAN_KEYSTORE_FILE`, `ECOPAN_KEYSTORE_PASSWORD`, `ECOPAN_KEY_ALIAS` (and `ECOPAN_KEY_PASSWORD` if it differs) |

Without signing variables the release still assembles, unsigned (CI's R8 checks need that). Debug builds are untouched.

`scripts/verify-release.sh` checks the artifact itself: signed with a non-debug certificate, the mapping file
exists and at least 90% of classes are renamed, the requested URL is baked in and the development default is
not, the version matches `version.properties`. The manual workflow `release-android` does all of this from
repository secrets (`ECOPAN_KEYSTORE_BASE64`, `ECOPAN_KEYSTORE_PASSWORD`, `ECOPAN_KEY_ALIAS`, `ECOPAN_KEY_PASSWORD`).

### Baseline Profile

The release APK ships a committed Baseline Profile and Startup Profile
(`android/app/src/main/generated/baselineProfiles/`), so every release build has them with no device and no
backend. They are not regenerated on assemble (`automaticGenerationDuringBuild = false`); regenerate by hand,
with an emulator or device on API 33+ and the seeded stack (opening stock and a forecast, see
`scripts/demo-seed.py`) reachable on `http://localhost:8080` (`adb reverse tcp:8080 tcp:<port>`):

```shell
./gradlew :android:app:generateBaselineProfile        # journeys: cold start, enrol + batch entry, sync
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest   # cold start, CompilationMode.None vs the profile
```

These run the `nonMinifiedRelease` / `benchmarkRelease` build types, which are debug-signed local builds and
**not** guarded by `checkReleaseConfiguration` (it only covers `assembleRelease` / `bundleRelease`). They need
the opposite of a release: the default backend address (`localhost`) and the dev quick-login row on
(`ecopan.devQuickLogin`, default ON), because the journeys enrol through it. So pass neither
`-Pecopan.backendUrl` nor `-Pecopan.devQuickLogin=false` to them, and never distribute what they build. The
committed profile is a list of method names: it carries neither the address nor the seeded passwords.

## Publishing a release

Push a tag equal to `version.properties` (`v1.0.33`): `.github/workflows/release.yml` builds the signed
Android APK and bundle plus the macOS, Windows and Linux installers against the repository variable
`ECOPAN_BACKEND_URL`, checks the Android and macOS artifacts with `verify-release.sh`, and attaches
everything to one GitHub release. Run by hand (`backend_url` input) it builds and keeps the artifacts
without publishing. It needs the signing secrets listed above; until a real key exists the release
workflow cannot sign. The desktop installers are not code-signed or notarised.

## macOS installer

```shell
./gradlew :jvm:app:packageReleaseDmg -Pecopan.backendUrl=https://... -Pecopan.devQuickLogin=false
scripts/verify-release.sh --dmg jvm/app/build/compose/binaries/main-release/dmg/EcoPan-*.dmg --expect-url https://...
```

The installer is **not code-signed or notarised** (no signing identity is configured), so macOS will warn on first
open, and `-Pecopan.requireSigning=true` always fails here. Windows and Linux installers are untested (#117).

A build succeeding does not prove the packaged app starts: the ProGuard-minified desktop app failed at runtime in
three ways that `./gradlew :jvm:app:run` never shows (see the commit history of `jvm/app/proguard-rules.pro`).
Launch the installed app after changing rules, dependencies or runtime modules.

## Icons

`branding/icon/build-icons.sh` regenerates every platform's icon from one Material glyph and the design
system's colours. Edit `BG` / `FG` in `build-icons.mjs` to recolour, or swap `bakery_dining.svg` for another icon.

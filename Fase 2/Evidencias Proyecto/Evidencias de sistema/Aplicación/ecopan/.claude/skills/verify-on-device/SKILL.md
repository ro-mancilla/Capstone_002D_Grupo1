---
name: verify-on-device
description: Prove a myschedule change works before calling it done: run the unit tests, drive the change on an emulator or simulator (screenshots you look at yourself), and for visual changes publish before and after images. Use when finishing a PR, verifying a UI or intent-filter change, taking screenshots for a PR, or asked "did you test this on device".
---

# Verify on device

A change is done when every acceptance criterion has been **seen** working in a running app. Passing tests and a green compile are not that. A PR body must say what was run and, under "Unverified", what wasn't.

## 1. Unit tests first

- One module while iterating: `./gradlew :<module>:jvmTest` (or `testDebugUnitTest` for `:android:app`).
- Modules that test the tour (`:core:guidance`, `:feature:home:impl`, `:feature:deadlines:impl`, `:feature:schedule:impl`) need JDK 21, as does `:android:app` (FileKit is Java 21 bytecode).
- Run the whole suite once at the end. When two PRs touch the same area, test the **combination** before merging (a renamed label broke another PR's test that way).
- A test that passes with your change but also passes with it reverted proves nothing: revert once and watch it fail.

## 2. Drive it on the emulator (behavior the JVM cannot show)

- Build from the branch's own worktree: `./gradlew :android:app:assembleDebug`, then `adb -s <serial> install -r .../app-debug.apk`. Never from a dirty main checkout.
- Seed instead of tapping through onboarding. Debug builds read launch extras (`ScreenshotCapture`, `android/app/src/sampleData`): `am start -n com.f776.myschedule/.MainActivity --ez screenshot_seed true`, plus `--es screenshot_now 2026-10-29T08:30`, `--ez screenshot_tours_seen true`, `--ez screenshot_seed_features true`, `--ez screenshot_whats_new_seen x`. `pm clear` first. Read that file's header for the full list; do not invent another seeding path.
- Set the locale and theme per run: `cmd locale set-app-locales com.f776.myschedule --locales es`, `cmd uimode night yes`, `settings put system font_scale 1.3`.
- Look at the picture yourself: `adb exec-out screencap -p > x.png`, then Read it. Screenshot coordinates come back scaled (displayed 900 of 1080 px: multiply by 1.2 for `input tap`). Find controls with `uiautomator dump` and `testTag` ids rather than guessing.
- Check you are on the right device. An old AVD (API 29) renders plain Material, with no floating dock and no Recess look. Use Medium_Phone (Android 17) for look, Pixel Tablet for tablet.
- A flaky or ANR result on a loaded machine is inconclusive, not a finding. Rerun when it is quiet.

### Opening a file or link from outside the app

Use the real route a user takes, not `am start -d`: Files → Downloads (`content://com.android.providers.downloads.documents/root/downloads`, opaque `msf:` URIs), then tap. `am start` with a path-ending-in-`.myschedule` URI hits the old path filter and passes on master too. Always run the **same steps on master as a control** and show the difference.

## 3. Before and after for visual changes: render on the JVM first

Screenshots of a Recess component or a screen state do not need an emulator. `RenderGalleryTest` (`:core:designsystem`, jvmTest) draws `RecessGallery` with Compose's `ImageComposeScene` and writes `gallery-light.png` and `gallery-dark.png`: about 5 seconds, no device, same fonts and tokens as the app.

```
RECESS_RENDER_DIR=/abs/dir ./gradlew :core:designsystem:jvmTest --tests '*RenderGalleryTest*'
```

- Run it once in a worktree of the base branch (before) and once on the PR branch (after), into two directories. Same command, same size, so the only difference is the change.
- A changed or new component must have a `RecessGallery` entry anyway (CLAUDE.md); that entry is what shows up. Add the states the change touches: disabled, long text, the longest language (Spanish), both themes.
- For a feature screen the gallery does not cover, write a `jvmTest` that composes the screen with fixed data and a fixed date (see `HomeLongNameTest` for how Home is composed), then call `saveScreenshot("name")` from `:core:test`. It writes `<SCREENSHOT_DIR>/name.png` and does nothing when the variable is unset, so it can stay in the test:

  ```
  SCREENSHOT_DIR=/abs/dir ./gradlew :feature:home:impl:jvmTest --tests '*MyScreenshotTest*' --rerun
  ```

  `--rerun` matters: Gradle skips an up-to-date test task and writes nothing. The window is `runDesktopComposeUiTest(width, height)` in dp (360 x 800 for a phone, 1400 x 900 for wide), at density 1. Set `Locale.setDefault` and the theme in the test for each language and mode, as the existing tests do.
- Read the PNGs yourself and crop to the changed area when the full sheet hides it.
- Use the emulator only for what the JVM cannot show: real device chrome (floating dock over a scrolled list, status bar), the OS handing over a file or link, notifications, performance, anything that needs a real window. Then follow section 2 and keep the same device, locale, theme, font scale, seed **and pinned clock** for both sides; a counter reading "2 of 5" before and "2 of 2" after is a different seed, not your change.
- Name files `before_*.png` and `after_*.png`. Host them on the orphan branch `pr-assets` (`pr-<number>/…png`) and link `https://github.com/GreatTusk/myschedule/blob/pr-assets/pr-<n>/<file>.png?raw=true`. Open the PR once to check they render; the repo is private.
- Say what the images show and what they don't (a default phone may not exhibit the bug at all).

### Animations: frames, a strip and a GIF

A transition (spotlight glide, crossfade, sheet) is shown as a **contact strip** of a few frames plus a GIF. Say what the strip shows frame by frame; a still of the end state proves nothing about motion.

On the JVM (preferred, deterministic): stop the test clock, trigger the change, then step it a frame at a time and save each one.

```kotlin
mainClock.autoAdvance = false
// ...show the screen, trigger the change (click Next)...
repeat(60) { frame ->
    mainClock.advanceTimeByFrame()          // 16 ms
    saveScreenshot("glide_%02d".format(frame))
}
```

`TourTransitionTest` steps frames like this to assert the dim and the spotlight on each one. Then, in the output directory (frames 0 to 59 at 60 fps; the 20 or so frames of a 320 ms glide are the interesting ones):

```
# GIF: every second frame, 30 fps, palette method (small and clean)
ffmpeg -framerate 60 -i glide_%02d.png -vf "select='not(mod(n,2))',setpts=N/(30*TB),fps=30,scale=300:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" glide.gif
# strip: pick frames, tile left to right
ffmpeg -framerate 60 -i glide_%02d.png -vf "select='eq(n,0)+eq(n,4)+eq(n,8)+eq(n,12)+eq(n,16)+eq(n,22)',scale=240:-1,tile=6x1" -frames:v 1 -vsync 0 strip.png
```

Look at the strip yourself. Keep the GIF under about 4 MB (a 300 px wide, 1 s glide is ~70 KB). Host both on `pr-assets` like other images; embed the strip as an image and the GIF beside it.

On a device (when the motion depends on a real window, scroll or the OS): `adb -s <serial> shell screenrecord --time-limit 8 /sdcard/r.mp4` while you tap, `adb pull`, then `ffmpeg -i r.mp4 -vf "fps=15,scale=480:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" r.gif` and frames with `ffmpeg -i r.mp4 -vf fps=15 v_%02d.png`. This is how the first #184 GIF was made. It is less repeatable (timing varies, the first frame can be a half-drawn screen), so do the same taps for master and the PR and say if the two runs differ in step or theme.

Also check what the motion does when the student has reduced motion on: it should jump, not glide.

## 4. iOS

- Kotlin: `./gradlew :<module>:iosSimulatorArm64Test`. UI: `xcodebuild test` from `iosApp/` (one at a time; see CLAUDE.md for launch arguments).
- System UI (share sheet, Files) is only proven by driving the real one. Check what is left behind (for example copies in `Inbox/`), not just that the screen appeared.
- State plainly what the simulator cannot show (camera, Mail and Messages hand-off, tours off on iOS).

## 5. Report

List each acceptance criterion with how it was seen (emulator, serial, build, steps), the master control where it applies, and what stayed unverified. Do not write "verified" for a unit test alone.

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
}

/**
 * Which machine the clients look for the backend on. Defaults to the developer's own.
 *
 * Set `-Pecopan.backendHost=<host>` (or the same key in `gradle.properties`) to point a build at a
 * backend somewhere else — a staging deployment, or another machine on the same network.
 */
val backendHost: String = providers.gradleProperty("ecopan.backendHost").orNull ?: "localhost"

/**
 * A whole base URL, for a backend that is not `http://<host>:8080` -- a deployed one behind HTTPS on 443
 * (`-Pecopan.backendUrl=https://ecopan-app.azurewebsites.net`). Wins over [backendHost] when set, so there is
 * still exactly one resolved constant.
 */
val backendUrl: String =
    providers.gradleProperty("ecopan.backendUrl").orNull ?: "http://$backendHost:8080"

/**
 * Whether the enrollment screen offers one-tap sign-in for the seeded `reference` users.
 *
 * **On by default**, and a property rather than a literal so it is one knob like [backendHost]:
 * `-Pecopan.devQuickLogin=false`. Default-on is deliberate -- the capstone demo runs from an
 * ordinary build, and a shortcut that only exists when someone remembers to pass a flag is a
 * shortcut nobody has when they need it. It is also the one thing to turn off before any build
 * leaves the project, since the row carries seeded passwords by design.
 */
val devQuickLogin: Boolean =
    providers.gradleProperty("ecopan.devQuickLogin").orNull?.toBooleanStrictOrNull() ?: true

/**
 * One backend address for every client, replacing the three hand-maintained per-platform constants
 * this used to be — an `androidMain` `10.0.2.2`, a `jvmMain` `localhost` and an `iosMain`
 * `localhost`, each its own thing to remember to change, and only one of them ever right.
 *
 * `localhost` on every target, including Android, because the Android client reaches the host
 * through `adb reverse tcp:8080 tcp:8080` (see README §1). That is a deliberate choice over the
 * more familiar `10.0.2.2`:
 *
 * - `adb reverse` tunnels over the adb connection, so it works identically on an emulator and on a
 *   **physical device over USB**. `10.0.2.2` is emulator-only, which the README used to have to
 *   list as an unfixed limitation.
 * - It needs no LAN address, so it survives changing Wi-Fi networks and does not expose the
 *   development backend to the network.
 * - `10.0.2.2` was in fact broken on the emulator image this was developed against: an app's
 *   socket to it sat in SYN_SENT until connect timed out, while `adb shell` and the emulator's own
 *   browser reached the identical host and port.
 */
buildkonfig {
    packageName = "com.f776.ecopan.data.sync.config"
    // Public, not the plugin's internal default: DEV_QUICK_LOGIN_ENABLED is read from
    // :feature:auth:impl, a different module, not just from this one like BACKEND_BASE_URL was.
    exposeObjectWithName = "BuildKonfig"

    defaultConfigs {
        buildConfigField(STRING, "BACKEND_BASE_URL", backendUrl)
        // Dev/prototyping convenience only (feature/auth's quick-login row). A single knob, the
        // same way BACKEND_BASE_URL is, rather than a per-platform constant -- see `devQuickLogin`.
        buildConfigField(BOOLEAN, "DEV_QUICK_LOGIN_ENABLED", devQuickLogin.toString())
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.data.sync.api)
            implementation(projects.data.tenant.api)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.datastore.preferences.core)
            // Only needed for the T8 in-memory-Room atomicity test (jvmTest) -- data/sync/impl
            // has no Room entities/DAOs of its own, `:core:database` owns those.
            implementation(libs.androidx.room.runtime)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            // Transport (03 §3/§4 -- `KtorSyncTransport`). Root `settings.gradle.kts` already
            // wires the official `ktorLibs` version catalog (3.5.1) for the whole build, not just
            // `backend/*` -- reused here rather than pinning a second ktor version.
            implementation(ktorLibs.client.core)
            implementation(ktorLibs.client.contentNegotiation)
            implementation(ktorLibs.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            // OkHttp, not CIO: CIO's selector-based connect cannot reach the host from an Android
            // emulator (every request to 10.0.2.2 dies with ConnectTimeoutException out of
            // `cio.Endpoint.connect`, while the emulator's own browser reaches the same URL
            // fine), and OkHttp is the engine ktor documents for Android anyway.
            implementation(ktorLibs.client.okhttp)
            // 03 §7's optional background outbox flush -- same WorkManager wiring as :data:reminder:impl.
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.koin.androidx.workmanager)
        }

        androidHostTest.dependencies {
            // Robolectric/androidx-test come from the androidKmpLibrary convention plugin; only the
            // WorkManager test harness is specific to OutboxFlushWorkerTest (03 §7).
            implementation(libs.androidx.work.testing)
        }

        jvmMain.dependencies {
            implementation(libs.sqlite.bundled)
            implementation(ktorLibs.client.cio)
        }

        iosMain.dependencies {
            implementation(ktorLibs.client.darwin)
        }

        commonTest.dependencies {
            implementation(ktorLibs.client.mock)
        }

        jvmTest.dependencies {
            // Test-only: the real STOCK_MOVEMENT pull applier, to prove end-to-end cross-device
            // sync against the actual production/inventory wiring rather than a generic stand-in
            // (EndToEndBatchSyncTest, plan 06 item 10). No main-source-set dependency direction
            // change -- :data:inventory:impl still only depends on :data:sync:api, never the reverse.
            implementation(projects.data.inventory.impl)
            // Test-only: the real BRANCH pull applier, so 03 §5 test 2 (`configLastWriteWinsByServerSeq`,
            // ConflictPolicyTest) proves LWW against the actual config applier. Same direction note
            // as above -- :data:tenant:impl still only depends on :data:sync:api, never the reverse.
            implementation(projects.data.tenant.impl)
            // Test-only: the real PRODUCTION_BATCH pull applier, so EndToEndBatchSyncTest can prove
            // 06 §12 item 10's actual wording (a batch logged offline surfacing on another device).
            implementation(projects.data.production.impl)
            implementation(projects.data.catalog.api)
            // SyncApplierCoverageTest asserts the real DI graph binds an applier per entity type.
            implementation(projects.data.catalog.impl)
        }
    }
}

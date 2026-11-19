plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        // `:composeApp` is one binary family serving two different products (SPECS.md Client 1 vs
        // Client 2), so it needs a source set for "the mobile client" that is neither common nor
        // per-target: the baker's Android and iOS builds share every line of their tab list and
        // their production wiring, and the till shares none of it. The default hierarchy template
        // has no android+ios intermediate, so it is declared here.
        val mobileMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(mobileMain)
        iosMain.get().dependsOn(mobileMain)

        iosMain.dependencies {
            implementation(libs.firebase.crashlytics)
        }

        // Client boundaries, expressed as dependencies rather than as discipline. `:mobile:*` has
        // no JVM target and `:jvm:*` has no mobile one, so neither client can reach the other's
        // screens -- there is nothing on the classpath to reach.
        mobileMain.dependencies {
            implementation(projects.mobile.feature.production.impl)
        }

        jvmMain.dependencies {
            implementation(projects.jvm.feature.pos.impl)
        }

        // The e2e harness restarts the app inside one JVM and so owns the preferences store's scope (see TillHarness).
        jvmTest.dependencies {
            implementation(projects.core.datastore)
        }

        commonMain.dependencies {
            implementation(projects.feature.auth.impl)
            implementation(projects.feature.settings.impl)

            implementation(projects.data.preferences.impl)
            implementation(projects.data.reminder.impl)
            implementation(projects.data.sync.api)
            implementation(projects.data.sync.impl)
            // AppViewModel gates SyncScheduler start/stop on a cached tenant (session-ready, 03 §7).
            implementation(projects.data.tenant.api)
            // AppViewModel also starts/stops BatchReadyStream on that same gate (06 §6).
            implementation(projects.data.production.api)
            implementation(projects.data.hardware.impl)

            implementation(libs.bundles.navigation3)
            implementation(libs.material3.adaptive.navigation.suite)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// The e2e suites (`scripts/e2e/run.sh till`) drive the real app against a throwaway backend. A plain
// `:composeApp:jvmTest` has no such backend, so they only run when the runner says so.
tasks.withType<Test>().configureEach {
    if (!providers.gradleProperty("ecopan.e2e").isPresent) filter.excludeTestsMatching("com.f776.ecopan.e2e.*")
}

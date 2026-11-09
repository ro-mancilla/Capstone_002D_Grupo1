import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.f776.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.crashlytics)
}

android {
    buildTypes {
        // The two build types the Baseline Profile plugin runs: nonMinifiedRelease generates the profile
        // (:baselineProfile), benchmarkRelease measures it (:benchmark). Declared here rather than left to
        // the plugin so they inherit the release configuration and are debug-signed, with the plugin
        // applying its own settings on top (not debuggable, profileable, nonMinifiedRelease unminified).
        // Neither is distributable: both are debug-signed, and both bake in the BuildKonfig defaults the
        // journeys need (the backend on localhost over `adb reverse`, the dev quick-login row), so the
        // release guard (CheckReleaseConfiguration) deliberately does not apply to them.
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        create("benchmarkRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // A local measuring build: its mapping file is of no use to Crashlytics.
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
        }
    }
}

// The journeys talk to a local backend over `adb reverse` (http://localhost:8080), which release's
// HTTPS-only network security config forbids; these two build types swap in one that allows cleartext
// to localhost only. Per variant rather than per source set, because the plugin copies release's source
// directories into both of its build types.
androidComponents {
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/profiling/res")
    }
    onVariants(selector().withBuildType("benchmarkRelease")) { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/profiling/res")
    }
}

baselineProfile {
    // One profile for the one release build type, kept in src/main/generated/baselineProfiles and
    // committed, so every build ships it without a device or a backend. Regenerate it by hand with
    // `./gradlew :android:app:generateBaselineProfile` (see BaselineProfileGenerator) when the journeys
    // or the app change a lot.
    mergeIntoMain = true
    saveInSrc = true
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.mobile.feature.production.api)
    implementation(projects.data.preferences.api)
    implementation(projects.data.reminder.impl)
    // MainActivity.onStop() hands the outbox to WorkManager (03 §7).
    implementation(projects.data.sync.api)
    implementation(projects.core.navigation)
    implementation(libs.moko.permissions)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineProfile"))
    implementation(platform(libs.firebase.bom))

    implementation(projects.core.ui)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.firebase.crashlytics)
    implementation(libs.bundles.navigation3)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.resources)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

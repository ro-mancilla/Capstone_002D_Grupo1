plugins {
    // Mobile-only like the screens it drives: Android + iOS, no JVM target.
    alias(libs.plugins.f776.kotlinMultiplatformMobile)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
}

kotlin {
    // gitlive's Crashlytics klib asks the linker for the Firebase frameworks, which only the iosApp host provides. The
    // journeys never call Crashlytics, so the simulator test binary links against empty stand-ins (scripts/e2e/ios-stubs.sh)
    // and lets any symbol resolve lazily.
    val iosStubs = rootProject.layout.buildDirectory.dir("ios-stubs").get().asFile
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
            linkerOpts("-F${iosStubs.absolutePath}", "-undefined", "dynamic_lookup")
        }
    }
    sourceSets {
        // The app's Compose resources (strings) reach a Kotlin/Native test executable only as resources of a main
        // dependency, so the module under test is one here even though only the tests use it.
        commonMain.dependencies { implementation(projects.composeApp) }
        commonTest.dependencies {
            implementation(projects.composeApp)
            implementation(projects.data.preferences.api)
            implementation(projects.data.tenant.api)
            implementation(libs.kotlin.test)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(ktorLibs.client.core)
        }
        // The device test target does not see commonTest on its own; the journeys live there to be shared with iOS.
        androidDeviceTest {
            dependsOn(commonTest.get())
            dependencies {
            implementation(ktorLibs.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.androidx.test.core)
            implementation(libs.kotlin.testJunit)
            // Espresso 3.7.0 reflects on InputManager.getInstance, which Android 17 (API 37) removed.
            implementation("androidx.test.espresso:espresso-core:3.8.0-alpha03")
            }
        }
        iosTest.dependencies {
            implementation(ktorLibs.client.darwin)
        }
    }
}

// Like :composeApp's e2e tests: they need the throwaway stack (`-Pecopan.e2e=true -Pecopan.backendUrl=...`), so a plain
// test run skips them.
tasks.withType<Test>().configureEach {
    if (!providers.gradleProperty("ecopan.e2e").isPresent) filter.excludeTestsMatching("com.f776.ecopan.mobile.e2e.*")
    filter.isFailOnNoMatchingTests = false
}

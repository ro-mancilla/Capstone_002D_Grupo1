plugins {
    // Mobile-only: Android + iOS source sets, no JVM target. See `com.f776.kotlinMultiplatformMobile`.
    alias(libs.plugins.f776.kotlinMultiplatformMobile)
    // Explicit now that there is no JVM target: an Android consumer used to resolve this module's
    // `jvm` variant as a fallback, which is exactly the accident that let a mobile-only feature
    // look like it built everywhere.
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.navigation3.runtime)
        }
    }
}

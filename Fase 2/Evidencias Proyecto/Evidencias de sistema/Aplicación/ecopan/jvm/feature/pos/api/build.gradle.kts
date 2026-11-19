plugins {
    // Desktop-only: JVM target only, no Android or iOS artifact at all.
    // See `com.f776.kotlinMultiplatformDesktop`.
    alias(libs.plugins.f776.kotlinMultiplatformDesktop)
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

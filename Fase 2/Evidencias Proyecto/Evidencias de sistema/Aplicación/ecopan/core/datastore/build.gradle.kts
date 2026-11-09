plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)

            api(libs.datastore.preferences.core)
            api(libs.kotlinx.io.core)
        }
    }
}

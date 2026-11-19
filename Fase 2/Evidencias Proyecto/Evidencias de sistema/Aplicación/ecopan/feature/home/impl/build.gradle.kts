plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.navigation)
            api(projects.core.ui)

            api(projects.feature.home.api)
            implementation(projects.data.reminder.impl)
            implementation(projects.data.example.api)
            implementation(projects.data.example.impl)

            implementation(libs.koin.compose.viewmodel)

            implementation(libs.bundles.navigation3)

            implementation(libs.kotlinx.serialization.json)

            implementation(libs.datastore.preferences.core)
        }
    }
}
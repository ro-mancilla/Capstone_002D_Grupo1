plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.data.preferences.api)
            implementation(projects.core.datastore)
            implementation(libs.koin.core)
        }
    }
}
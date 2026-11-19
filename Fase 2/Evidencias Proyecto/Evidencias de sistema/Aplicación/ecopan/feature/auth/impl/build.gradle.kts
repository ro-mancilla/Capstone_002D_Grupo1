plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.ui)

            implementation(projects.core.domain)
            implementation(projects.core.crypto)
            implementation(projects.data.tenant.api)
            implementation(projects.data.tenant.impl)
            implementation(projects.data.sync.api)
            implementation(projects.data.sync.impl)

            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.shell.api)

            implementation(projects.core.designsystem)
            implementation(projects.data.tenant.api)
            implementation(projects.data.sync.api)

            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

plugins {
    // Mobile-only: Android + iOS source sets, no JVM target. See `com.f776.kotlinMultiplatformMobile`.
    alias(libs.plugins.f776.kotlinMultiplatformMobile)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.navigation)
            api(projects.core.ui)

            api(projects.mobile.feature.production.api)
            implementation(projects.data.production.api)
            implementation(projects.data.production.impl)
            implementation(projects.data.inventory.api)
            implementation(projects.data.inventory.impl)
            implementation(projects.data.catalog.api)
            implementation(projects.data.catalog.impl)
            implementation(projects.feature.shell.api)
            implementation(projects.feature.shell.impl)
            implementation(projects.data.tenant.api)
            implementation(projects.data.tenant.impl)
            // api only -- SyncDataModule is bound once in :composeApp's AppModule, same as
            // :jvm:feature:pos:impl does for the POS status bar (03 §6.1).
            implementation(projects.data.sync.api)

            implementation(libs.koin.compose.viewmodel)

            implementation(libs.bundles.navigation3)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
    }
}

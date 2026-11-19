plugins {
    // Desktop-only: JVM target only. `androidKmpLibrary` is deliberately absent -- applying it
    // would produce an Android artifact for a screen that needs a scale and a cash drawer.
    // See `com.f776.kotlinMultiplatformDesktop`.
    alias(libs.plugins.f776.kotlinMultiplatformDesktop)
    alias(libs.plugins.f776.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.navigation)
            api(projects.core.ui)
            api(projects.jvm.feature.pos.api)

            implementation(projects.core.domain)
            implementation(projects.data.catalog.api)
            implementation(projects.data.catalog.impl)
            implementation(projects.data.hardware.api)
            implementation(projects.data.hardware.impl)
            implementation(projects.data.production.api)
            implementation(projects.data.production.impl)
            implementation(projects.data.sales.api)
            implementation(projects.data.sales.impl)
            implementation(projects.data.sync.api)
            implementation(projects.feature.shell.api)
            implementation(projects.feature.shell.impl)
            implementation(projects.data.tenant.api)
            implementation(projects.data.tenant.impl)

            implementation(libs.koin.compose.viewmodel)

            implementation(libs.bundles.navigation3)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
    }
}

plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.data.catalog.api)
            implementation(projects.core.database)
            implementation(projects.data.tenant.api)
            // Config pull appliers (PRODUCT/FORMULA) implement SyncApplier.
            implementation(projects.data.sync.api)
            implementation(projects.data.tenant.impl)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

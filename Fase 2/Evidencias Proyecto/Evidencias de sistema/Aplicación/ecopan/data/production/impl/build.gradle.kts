plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.data.production.api)
            implementation(projects.core.database)
            implementation(projects.data.inventory.api)
            implementation(projects.data.inventory.impl)
            implementation(projects.data.catalog.api)
            implementation(projects.data.catalog.impl)
            implementation(projects.data.sync.api)
            implementation(projects.data.tenant.api)
            implementation(projects.data.tenant.impl)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

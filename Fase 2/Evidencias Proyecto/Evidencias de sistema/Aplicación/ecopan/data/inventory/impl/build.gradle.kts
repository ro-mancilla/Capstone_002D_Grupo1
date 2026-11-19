plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.data.inventory.api)
            implementation(projects.core.database)
            implementation(projects.data.tenant.api)
            implementation(projects.data.tenant.impl)
            implementation(projects.data.sync.api)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

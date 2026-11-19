plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.coroutines.core)
            // Wire DTOs (domain.tenant.wire) shared by :data:tenant:impl's login call and
            // :data:sync:impl's real SyncAuthProvider refresh call.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

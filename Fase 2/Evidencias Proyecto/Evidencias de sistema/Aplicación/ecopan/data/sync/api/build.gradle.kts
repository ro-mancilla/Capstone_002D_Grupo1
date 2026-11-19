plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `outboxRow` returns an `OutboxEntity`, so consumers need it on their classpath.
            // data -> core is the allowed direction (CODING_STYLE 2.2); the reverse is what
            // `OutboxEntity`'s KDoc rules out, which is why the enum mapping lives here.
            api(projects.core.database)
            implementation(libs.kotlinx.coroutines.core)
            // JsonElement in the wire DTOs (03 §3.2/§4.1) and SyncApplier's payload param.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

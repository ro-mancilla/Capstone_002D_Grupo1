plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `indicator` is a `Flow<SyncIndicatorState>`, so consumers need the type.
            api(projects.core.designsystem)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

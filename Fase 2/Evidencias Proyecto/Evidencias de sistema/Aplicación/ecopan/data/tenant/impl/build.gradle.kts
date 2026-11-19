plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.data.tenant.api)
            implementation(projects.core.database)
            implementation(projects.core.crypto)
            implementation(projects.core.datastore)
            implementation(projects.data.sync.api)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.datastore.preferences.core)
            implementation(libs.kotlinx.serialization.json)

            // Real `/auth/login` call (`AuthRepositoryImpl`, 01 §4/03 §8) -- same official ktor
            // version catalog `:data:sync:impl` already wires from root `settings.gradle.kts`.
            implementation(ktorLibs.client.core)
            implementation(ktorLibs.client.contentNegotiation)
            implementation(ktorLibs.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            // OkHttp, not CIO: CIO's selector-based connect cannot reach the host from an Android
            // emulator (every request to 10.0.2.2 dies with ConnectTimeoutException out of
            // `cio.Endpoint.connect`, while the emulator's own browser reaches the same URL
            // fine), and OkHttp is the engine ktor documents for Android anyway.
            implementation(ktorLibs.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(ktorLibs.client.cio)
        }

        iosMain.dependencies {
            implementation(ktorLibs.client.darwin)
        }

        commonTest.dependencies {
            implementation(ktorLibs.client.mock)
        }
    }
}

plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.roomMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
        }

        commonMain.dependencies {
            api(projects.core.domain)
            // For `ecopanAppDataDir()` on desktop. The database and the preferences store hold two
            // halves of one device identity; they resolved their directory separately until a
            // working-directory-keyed preferences file left tills enrolled but credential-less.
            implementation(projects.core.datastore)

            implementation(libs.koin.core)
            implementation(libs.kotlinx.datetime)

            implementation(libs.kotlinx.serialization.json)

            api(libs.kotlinx.io.core)
        }
    }
}
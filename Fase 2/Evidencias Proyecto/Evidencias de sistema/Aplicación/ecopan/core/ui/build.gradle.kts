plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The design system (theme, tokens, stateless components) is part of this module's
            // public surface: features that depend on :core:ui get EcopanTheme without a second dep.
            api(projects.core.designsystem)
            api(projects.core.locale)
            api(libs.kotlinx.datetime)
            api(libs.compose.adaptive.navigation)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.bundles.navigation3)

            implementation(libs.kotlinx.serialization.json)
            api(libs.material.kolor)

            // Image loading. `api`, not `implementation`: the app module configures the singleton
            // ImageLoader, so it needs coil3 on its own compile classpath.
            api(libs.coil.compose)
            api(libs.coil.network.ktor3)
        }

        // Coil's ktor3 fetcher builds a default HttpClient, which needs an engine on the runtime
        // classpath. These are the same three engines `:data:sync:impl` already picks per target,
        // so this adds no second HTTP stack to the build -- see that module for why okhttp on
        // Android and CIO on desktop.
        androidMain.dependencies {
            implementation(ktorLibs.client.okhttp)
        }

        iosMain.dependencies {
            implementation(ktorLibs.client.darwin)
        }

        jvmMain.dependencies {
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(ktorLibs.client.cio)
        }
    }
}
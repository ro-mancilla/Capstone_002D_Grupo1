import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
}

/**
 * `version.properties` at the repo root was already the place the version lived — it just had no
 * consumer, so "Versión" and "Build" had nowhere to read from and the About card could not show
 * them. Parsed here rather than duplicated: bumping the file is still the only edit a release
 * needs.
 */
val appVersion: String = providers.fileContents(
    rootProject.layout.projectDirectory.file("version.properties")
).asText.map { text ->
    val fields = text.lineSequence()
        .mapNotNull { line -> line.substringBefore('#').split('=', limit = 2).takeIf { it.size == 2 } }
        .associate { (key, value) -> key.trim() to value.trim() }
    "${fields["versionMajor"] ?: 0}.${fields["versionMinor"] ?: 0}.${fields["versionPatch"] ?: 0}"
}.get()

val appBuild: String = providers.fileContents(
    rootProject.layout.projectDirectory.file("version.properties")
).asText.map { text ->
    text.lineSequence().firstOrNull { it.startsWith("versionCode") }?.substringAfter('=')?.trim() ?: "0"
}.get()

buildkonfig {
    packageName = "com.f776.ecopan.feature.settings.config"
    // Public for the same reason :data:sync:impl's is: the generated object is read from a
    // composable in this module, and the plugin's internal default would hide it from Compose's
    // generated code in the same module's other source sets.
    exposeObjectWithName = "SettingsBuildKonfig"

    defaultConfigs {
        // Deliberately not a build *timestamp*: that changes on every compile, so every build
        // would be a cache miss for every consumer of this module. The version code is the
        // identifier a release actually bumps.
        buildConfigField(STRING, "APP_VERSION", appVersion)
        buildConfigField(STRING, "APP_BUILD", appBuild)
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.navigation)
            api(projects.feature.settings.api)
            implementation(projects.data.preferences.impl)
            implementation(projects.data.reminder.impl)
            implementation(projects.data.hardware.api)
            implementation(projects.data.hardware.impl)
            // The About card names the bakery this device is enrolled to and the device itself.
            implementation(projects.data.tenant.api)
            implementation(projects.data.tenant.impl)
            // Rejected operations are reviewed from here, so this module reads the sync queue.
            implementation(projects.data.sync.api)
            implementation(projects.data.sync.impl)

            implementation(libs.koin.compose.viewmodel)

            implementation(libs.bundles.navigation3)

            implementation(libs.kotlinx.serialization.json)
        }
    }
}

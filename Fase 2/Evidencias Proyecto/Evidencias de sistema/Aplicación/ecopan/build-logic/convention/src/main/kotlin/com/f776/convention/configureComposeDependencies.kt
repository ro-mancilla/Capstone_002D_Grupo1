package com.f776.convention

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds Compose to whichever targets the module actually declared.
 *
 * The per-target blocks are looked up rather than assumed because the client-exclusive tiers do not
 * have every target: `:mobile:feature:*` (`com.f776.kotlinMultiplatformMobile`) has no `jvmMain`,
 * and `:jvm:feature:*` (`com.f776.kotlinMultiplatformDesktop`) has no `androidMain`. Naming a
 * source set that a module never created fails the build at configuration time, which is what
 * previously forced every Compose module to be built for every platform.
 */
internal fun Project.configureComposeDependencies(
    extension: KotlinMultiplatformExtension,
    composeDeps: ComposePlugin.Dependencies
) {
    extension.apply {
        sourceSets.apply {
            commonMain.dependencies {
                implementation(libs.findLibrary("compose-runtime").get())
                implementation(libs.findLibrary("compose-foundation").get())
                implementation(libs.findLibrary("compose-material3").get())
                implementation(libs.findLibrary("compose-ui").get())
                implementation(libs.findLibrary("compose-resources").get())
                implementation(libs.findLibrary("compose-ui-tooling-preview").get())
                implementation(libs.findLibrary("compose-adaptive-navigation").get())
                implementation(libs.findLibrary("compose-material-icons-extended").get())
                implementation(libs.findLibrary("androidx-lifecycle-viewmodelCompose").get())
                implementation(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
                implementation(libs.findLibrary("adaptive.layout").get())
            }

            commonTest.dependencies {
                implementation(libs.findLibrary("compose-ui-test").get())
            }

            findByName("androidMain")?.dependencies {
                implementation(libs.findLibrary("compose-ui-tooling-preview").get())
                implementation(libs.findLibrary("androidx.activity.compose").get())
            }

            findByName("jvmMain")?.dependencies {
                implementation(composeDeps.desktop.currentOs)
                implementation(libs.findLibrary("kotlinx-coroutinesSwing").get())
            }
        }
    }

    if (configurations.findByName("androidRuntimeClasspath") != null) {
        dependencies {
            add("androidRuntimeClasspath", libs.findLibrary("compose-ui-tooling").get())
        }
    }
}

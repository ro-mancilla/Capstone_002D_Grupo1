package com.f776.convention

import org.gradle.api.Project
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun Project.configureSourceSets(
    extension: KotlinMultiplatformExtension,
    includeDesktop: Boolean = true,
    includeIos: Boolean = true
) = extension.apply {
    jvmToolchain(17)

    if (includeDesktop) {
        jvm()
    }

    if (includeIos) {
        configureIosTargets(this)
    }

    applyDefaultHierarchyTemplate()
}

internal fun Project.configureCommonSourceSets(extension: KotlinMultiplatformExtension) = extension.apply {
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets.all {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    sourceSets.commonMain.dependencies {
        implementation(libs.findLibrary("kermit").get())
    }

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
        implementation(project(":core:test"))
    }
}

private const val DATABASE_MODULE_IOS_NAME = "Database"

internal fun Project.configureIosTargets(
    kotlinMultiplatformExtension: KotlinMultiplatformExtension
) {
    // Top-level modules such as :composeApp have nothing left after drop(2), so fall back to
    // the module name itself — otherwise the framework would be built with an empty baseName.
    //
    // The segments are concatenated in PascalCase rather than joined with the default ", ":
    // a framework name is an identifier, and a path deeper than three segments (as the
    // client-exclusive tiers are, e.g. `:mobile:feature:production:impl`) used to produce
    // "production, impl" and fail the Kotlin/Native compiler with "Name contains illegal
    // characters". Shallower paths are unaffected — `:core:database` still resolves to
    // [DATABASE_MODULE_IOS_NAME].
    val baseNameIos = path.split(":")
        .drop(2)
        .joinToString(separator = "") { it.uppercaseFirstChar() }
        .ifEmpty { path.removePrefix(":").uppercaseFirstChar() }

    listOf(
        kotlinMultiplatformExtension.iosArm64(),
        kotlinMultiplatformExtension.iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = baseNameIos
            isStatic = true
            if (baseNameIos == DATABASE_MODULE_IOS_NAME) {
                linkerOpts.add("-lsqlite3")
            }
        }
    }
}
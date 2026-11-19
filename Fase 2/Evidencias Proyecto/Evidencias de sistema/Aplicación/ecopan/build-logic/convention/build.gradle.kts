import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.f776.buildlogic"

// Configure the build-logic plugins to target JDK 17
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    // Not compileOnly: `KoverConventionPlugin` applies Kover through `pluginManager.apply(...)`.
    implementation(libs.kover.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}


gradlePlugin {
    plugins {
        register("composeMultiplatform"){
            id = libs.plugins.f776.composeMultiplatform.get().pluginId
            implementationClass = "ComposeConventionPlugin"
        }

        register("glanceWidget"){
            id = libs.plugins.f776.glanceWidget.get().pluginId
            implementationClass = "GlanceWidgetConventionPlugin"
        }

        register("kotlinMultiplatform"){
            id = libs.plugins.f776.kotlinMultiplatform.get().pluginId
            implementationClass = "KotlinMultiplatformConventionPlugin"
        }

        register("kotlinMultiplatformMobile"){
            id = libs.plugins.f776.kotlinMultiplatformMobile.get().pluginId
            implementationClass = "KotlinMultiplatformMobileConventionPlugin"
        }

        register("kotlinMultiplatformDesktop"){
            id = libs.plugins.f776.kotlinMultiplatformDesktop.get().pluginId
            implementationClass = "KotlinMultiplatformDesktopConventionPlugin"
        }

        register("androidKmpLibrary"){
            id = libs.plugins.f776.androidKmpLibrary.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }

        register("androidApplication"){
            id = libs.plugins.f776.androidApplication.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }

        register("wearApplication"){
            id = libs.plugins.f776.wearApplication.get().pluginId
            implementationClass = "WearApplicationConventionPlugin"
        }

        register("desktopApplication"){
            id = libs.plugins.f776.desktopApplication.get().pluginId
            implementationClass = "DesktopApplicationConventionPlugin"
        }

        register("kover"){
            id = libs.plugins.f776.kover.get().pluginId
            implementationClass = "KoverConventionPlugin"
        }

        register("roomMultiplatform"){
            id = libs.plugins.f776.roomMultiplatform.get().pluginId
            implementationClass = "RoomDatabaseConventionPlugin"
        }
    }
}
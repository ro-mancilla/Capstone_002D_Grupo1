package com.f776.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

internal fun Project.configureKotlinAndroid(
    extension: ApplicationExtension,
    isWear: Boolean = false
) = extension.apply {

    //get module name from module path
    val moduleName = path.split(":")
        .drop(1)
        .joinToString(".")

    namespace =
        if (moduleName.isNotEmpty()) {
            if (moduleName == "androidApp") {
                "com.f776.ecopan"
            } else {
                "com.f776.ecopan.$moduleName"
            }
        } else "com.f776"

    compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
    testOptions.unitTests.isIncludeAndroidResources = true
    defaultConfig {
        minSdk = if (isWear) libs.findVersion("android-wear-minSdk")
            .get().requiredVersion.toInt() else libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
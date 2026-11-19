package com.f776.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

internal fun Project.configureAndroidApplication(
    extension: ApplicationExtension,
    versionCodeProperty: Int,
    versionNameProperty: String
) = extension.apply {
    namespace = "com.f776.ecopan"
    compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()

    defaultConfig {
        applicationId = "com.f776.ecopan"
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        targetSdk = libs.findVersion("android-targetSdk").get().requiredVersion.toInt()
        versionCode = versionCodeProperty
        versionName = versionNameProperty
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Release signing comes from the environment, never from the repository: the keystore path, its
    // password and the key alias are CI secrets (or, for a rehearsal, the throwaway key that
    // scripts/make-demo-keystore.sh makes). Without them the release build still assembles, unsigned,
    // which is what the CI R8 checks need; -Pecopan.requireSigning=true turns "unsigned" into an error
    // (see CheckReleaseConfiguration).
    val keystoreFile = providers.environmentVariable("ECOPAN_KEYSTORE_FILE")
    if (keystoreFile.isPresent) {
        fun required(name: String) = providers.environmentVariable(name).orNull
            ?: error("ECOPAN_KEYSTORE_FILE is set but $name is not")
        val storePassword = required("ECOPAN_KEYSTORE_PASSWORD")
        signingConfigs.create("release") {
            storeFile = rootProject.file(keystoreFile.get())
            this.storePassword = storePassword
            keyAlias = required("ECOPAN_KEY_ALIAS")
            // Most keystores use one password for both; ECOPAN_KEY_PASSWORD overrides when they differ.
            keyPassword = providers.environmentVariable("ECOPAN_KEY_PASSWORD").orNull ?: storePassword
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystoreFile.isPresent) signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                // Includes the default ProGuard rules files that are packaged with
                // the Android Gradle plugin. To learn more, go to the section about
                // R8 configuration files.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Enable the benchmark to run separately from the app process
    experimentalProperties["android.experimental.self-instrumenting"] = true
}
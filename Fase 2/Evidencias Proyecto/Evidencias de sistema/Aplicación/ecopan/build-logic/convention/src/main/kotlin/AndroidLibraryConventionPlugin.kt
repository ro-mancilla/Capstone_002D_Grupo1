import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.f776.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import kotlin.jvm.kotlin

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.findPlugin("androidKmpLibrary").get().get().pluginId)

        kotlinExtension.configure<KotlinMultiplatformAndroidLibraryExtension> {
            val moduleName = path.split(":")
                .drop(1)
                .joinToString(".")

            compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
            minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
            namespace =
                if (moduleName.isNotEmpty()) {
                    "com.f776.ecopan.$moduleName"
                } else "com.f776"
            androidResources.enable = true
        }
        // Android test compilations also pull in commonTest. Enabling them everywhere meant
        // every shared Compose test ran a second time on Robolectric, where it fails for want of
        // an Android environment it never needed — the JVM target already covers that code. So
        // the Android test targets only exist for modules that actually hold Android-specific
        // test sources.
        //
        // "The JVM target already covers that code" stops being true for the mobile-only tier
        // (`com.f776.kotlinMultiplatformMobile`), which has no JVM target at all: without this,
        // moving a module there would silently strand its `commonTest` with nowhere to run — a
        // green build that stopped executing the tests. So a module with `commonTest` and no JVM
        // target gets Android host tests whether or not it has Android-specific sources.
        val hasJvmTarget = extensions.getByType(KotlinMultiplatformExtension::class.java)
            .targets.findByName("jvm") != null
        val hasAndroidDeviceTests = file("src/androidDeviceTest").isDirectory
        // A module whose commonTest is written for the emulator (the e2e module) opts out of the host target.
        val hasAndroidHostTests = file("src/androidHostTest").isDirectory ||
            (!hasJvmTarget && !hasAndroidDeviceTests && file("src/commonTest").isDirectory)

        extensions.configure<KotlinMultiplatformExtension> {
            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                if (hasAndroidHostTests) {
                    // Opt-in to enable and configure host-side (unit) tests
                    withHostTest {
                        isIncludeAndroidResources = true
                    }
                }

                if (hasAndroidDeviceTests) {
                    // Opt-in to enable and configure device-side (instrumented) tests
                    withDeviceTest {
                        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                        execution = "HOST"
                    }
                }
            }

            if (hasAndroidHostTests) {
                sourceSets.getByName("androidHostTest").dependencies {
                    implementation(libs.findLibrary("kotlin.testJunit").get())
                    implementation(libs.findLibrary("androidx.test.core").get())
                    implementation(libs.findLibrary("robolectric").get())
                    implementation(libs.findLibrary("kotlinx.coroutines.test").get())
                    implementation(libs.findLibrary("androidx.ui.test.junit4").get())
                    implementation(libs.findLibrary("androidx.ui.test.manifest").get())
                }
            }

            if (hasAndroidDeviceTests) {
                sourceSets.getByName("androidDeviceTest").dependencies {
                    implementation(libs.findLibrary("androidx.testExt.junit").get())
                    implementation(libs.findLibrary("androidx.runner").get())
                    implementation(libs.findLibrary("androidx.ui.test.junit4").get())
                    implementation(libs.findLibrary("androidx.ui.test.manifest").get())
                }
            }
        }
    }
}
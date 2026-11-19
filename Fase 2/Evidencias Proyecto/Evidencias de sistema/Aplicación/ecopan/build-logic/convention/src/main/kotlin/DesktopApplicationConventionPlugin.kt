import com.f776.convention.configureComposeCompiler
import com.f776.convention.getVersionCodeAndNameProperty
import com.f776.convention.libs
import com.f776.convention.registerReleaseGuard
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class DesktopApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply(libs.findPlugin("kotlinJvm").get().get().pluginId)
            apply(libs.findPlugin("composeMultiplatform").get().get().pluginId)
            apply(libs.findPlugin("composeCompiler").get().get().pluginId)
            apply(libs.findPlugin("f776-kover").get().get().pluginId)
        }

        // Like every other client module: without this the app's own classes target the Gradle JDK (21 here)
        // while the rest of the jar and the bundled runtime are 17, and a 17 JRE refuses them.
        extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(17) }

        extensions.configure<ComposeExtension> {
            configure<ResourcesExtension> {
                generateResClass = always
            }
        }

        configureComposeCompiler()

        // The installer bakes in the backend address like the APK does. Desktop signing is not set up
        // (the macOS entitlements exist, a signing identity does not), so requireSigning always fails here.
        registerReleaseGuard(
            releaseTasks = Regex("^(create|package)Release(Distributable|Dmg|Msi|Deb|DistributionForCurrentOS|UberJarForCurrentOS)$"),
            signingConfigured = providers.provider { false },
        )

        extensions.configure<ComposeExtension> {
            dependencies {
                // Common Compose
                add("implementation", libs.findLibrary("compose-runtime").get())
                add("implementation", libs.findLibrary("compose-foundation").get())
                add("implementation", libs.findLibrary("compose-material3").get())
                add("implementation", libs.findLibrary("compose-ui").get())
                add("implementation", libs.findLibrary("compose-resources").get())
                add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
                add("implementation", libs.findLibrary("compose-adaptive-navigation").get())
                add("implementation", libs.findLibrary("compose-material-icons-extended").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodelCompose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
                add("implementation", libs.findLibrary("adaptive.layout").get())

                add("implementation", this@configure.dependencies.desktop.currentOs)
                // -Pecopan.windowsNatives=true also bundles the Windows Skia natives, so an uber jar built on a Mac
                // or Linux machine runs on Windows (skiko picks the natives by OS at startup). Off by default.
                if (providers.gradleProperty("ecopan.windowsNatives").orNull == "true") {
                    add("implementation", this@configure.dependencies.desktop.windows_x64)
                }
                add("implementation", libs.findLibrary("kotlinx-coroutinesSwing").get())

                // Testing
                add("testImplementation", libs.findLibrary("compose-ui-test").get())
            }

            extensions.configure<DesktopExtension> {
                val (_, versionNameProperty) = getVersionCodeAndNameProperty()
                application {
                    mainClass = "com.f776.ecopan.MainKt"

                    nativeDistributions {
                        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                        // The installed app's name (EcoPan.app, the macOS menu bar). The reverse-DNS id is the
                        // bundle id below: it used to be the packageName, which made the app literally
                        // "com.f776.ecopan" everywhere a name is shown.
                        packageName = "EcoPan"
                        // The bundled Java runtime is trimmed to these modules (plus the plugin's defaults:
                        // java.base, java.desktop, java.logging, jdk.crypto.ec); anything else fails only in the
                        // installed app, never under `./gradlew run`. This is the output of
                        // `./gradlew :jvm:app:suggestRuntimeModules`: jdk.unsupported is sun.misc.Unsafe, which
                        // DataStore's protobuf needs at startup.
                        modules("java.instrument", "java.management", "jdk.unsupported")
                        packageVersion = versionNameProperty
                        description = "EcoPan"
                        copyright = "Copyright (c) 2025, GreatTusk"

                        windows {
                            iconFile.set(project.rootProject.file("composeApp/ic_launcher-playstore.ico"))
                            shortcut = true
                        }

                        linux {
                            // Debian package names must be lowercase, unlike the app name above.
                            packageName = "ecopan"
                            iconFile.set(project.rootProject.file("android/app/src/main/ic_launcher-playstore.png"))
                        }

                        macOS {
                            bundleID = "com.f776.ecopan"
                            dockName = "EcoPan"
                            entitlementsFile.set(project.file("default.entitlements"))
                            iconFile.set(project.file("ic_launcher-playstore.icns"))
                        }
                    }

                    buildTypes.release {
                        proguard {
                            optimize.set(true)
                            obfuscate.set(true)
                            configurationFiles.from("proguard-rules.pro")
                        }
                    }
                }
            }
        }
    }
}
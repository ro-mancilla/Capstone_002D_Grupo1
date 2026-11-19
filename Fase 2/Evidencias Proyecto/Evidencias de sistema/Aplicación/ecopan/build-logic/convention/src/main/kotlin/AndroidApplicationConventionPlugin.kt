import com.android.build.api.dsl.ApplicationExtension
import com.f776.convention.configureAndroidApplication
import com.f776.convention.getVersionCodeAndNameProperty
import com.f776.convention.libs
import com.f776.convention.registerReleaseGuard
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.findPlugin("androidApplication").get().get().pluginId)
        pluginManager.apply(libs.findPlugin("composeCompiler").get().get().pluginId)
        pluginManager.apply(libs.findPlugin("f776-kover").get().get().pluginId)

        extensions.configure<KotlinAndroidExtension> {
            jvmToolchain(17)
        }

        val (versionCodeProperty, versionNameProperty) = getVersionCodeAndNameProperty()

        extensions.configure<ApplicationExtension> {
            configureAndroidApplication(this, versionCodeProperty, versionNameProperty)
        }

        registerReleaseGuard(
            releaseTasks = Regex("^(assemble|bundle)Release$"),
            signingConfigured = providers.environmentVariable("ECOPAN_KEYSTORE_FILE").map { true }.orElse(false),
        )
    }
}
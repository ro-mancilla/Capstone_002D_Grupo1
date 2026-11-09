import com.f776.convention.configureComposeCompiler
import com.f776.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class WearComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply(libs.findPlugin("kotlin-compose").get().get().pluginId)
        }

        if (pluginManager.hasPlugin(libs.findPlugin("f776-androidLibrary").get().get().pluginId)) {
            dependencies {
                add("debugImplementation", libs.findLibrary("ui-tooling").get().get().name)
                add("debugImplementation", libs.findLibrary("ui-test-manifest").get().get().name)
                add("debugImplementation", libs.findLibrary("tiles-tooling").get().get().name)
            }
        }

        configureComposeCompiler()
    }
}
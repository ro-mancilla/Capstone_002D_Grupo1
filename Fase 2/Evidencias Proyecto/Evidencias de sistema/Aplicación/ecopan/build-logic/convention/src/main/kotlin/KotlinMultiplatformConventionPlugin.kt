
import com.f776.convention.configureCommonSourceSets
import com.f776.convention.configureSourceSets
import com.f776.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.findPlugin("kotlinMultiplatform").get().get().pluginId)
        pluginManager.apply(libs.findPlugin("f776-kover").get().get().pluginId)

        extensions.configure<KotlinMultiplatformExtension> {
            configureSourceSets(this)
            configureCommonSourceSets(this)
        }
    }
}
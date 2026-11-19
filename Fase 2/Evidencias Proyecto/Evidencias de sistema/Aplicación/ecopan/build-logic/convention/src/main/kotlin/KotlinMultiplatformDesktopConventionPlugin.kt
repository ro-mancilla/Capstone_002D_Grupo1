
import com.f776.convention.configureCommonSourceSets
import com.f776.convention.configureSourceSets
import com.f776.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `com.f776.kotlinMultiplatform` with the JVM target only: no iOS, and no Android either (the
 * Android target comes from `com.f776.androidKmpLibrary`, which a module using this plugin simply
 * does not apply).
 *
 * The mirror image of `com.f776.kotlinMultiplatformMobile`, and what makes `:jvm:feature:*` a real
 * boundary: a desktop-till feature produces no Android or iOS artifact, so the baker's phone
 * cannot depend on it even by accident.
 */
class KotlinMultiplatformDesktopConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.findPlugin("kotlinMultiplatform").get().get().pluginId)
        pluginManager.apply(libs.findPlugin("f776-kover").get().get().pluginId)

        extensions.configure<KotlinMultiplatformExtension> {
            configureSourceSets(this, includeIos = false)
            configureCommonSourceSets(this)
        }
    }
}

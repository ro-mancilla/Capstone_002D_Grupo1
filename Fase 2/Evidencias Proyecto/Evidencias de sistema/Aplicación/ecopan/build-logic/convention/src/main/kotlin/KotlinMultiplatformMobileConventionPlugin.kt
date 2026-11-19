
import com.f776.convention.configureCommonSourceSets
import com.f776.convention.configureSourceSets
import com.f776.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `com.f776.kotlinMultiplatform` minus the JVM target: Android and iOS source sets only.
 *
 * This is what makes `:mobile:feature:*` a real boundary rather than a naming convention. A module
 * built with this plugin produces no JVM artifact at all, so the desktop till *cannot* depend on
 * it -- there is nothing to resolve. The mirror image of `:jvm:feature:*`, which the till owns and
 * the phone never sees.
 */
class KotlinMultiplatformMobileConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.findPlugin("kotlinMultiplatform").get().get().pluginId)
        pluginManager.apply(libs.findPlugin("f776-kover").get().get().pluginId)

        extensions.configure<KotlinMultiplatformExtension> {
            configureSourceSets(this, includeDesktop = false)
            configureCommonSourceSets(this)
        }
    }
}

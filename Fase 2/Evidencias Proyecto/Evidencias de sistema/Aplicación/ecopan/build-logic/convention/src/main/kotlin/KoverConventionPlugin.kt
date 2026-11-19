import com.f776.convention.libs
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Kover coverage for JVM and Android host tests. Applied to every module (the other convention
 * plugins apply it) and to the root project, which merges them all:
 * `./gradlew :koverHtmlReport :koverXmlReport` writes `build/reports/kover/{html,report.xml}`.
 * Report only, no minimum. `-PignoreTestFailures` lets the report be written although tests failed
 * (CI does this in a second step, so a red run still gets its coverage).
 *
 * The filters keep out code with nothing to test: Room's generated database and DAOs (`*_Impl`),
 * Compose resources (`Res`), Koin modules and `@Preview` functions. The merged report applies the
 * root's filters, so they are static, never read from the modules.
 */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.findPlugin("kover").get().get().pluginId)

        extensions.configure<KoverProjectExtension> {
            reports.filters.excludes {
                classes(
                    "*_Impl",
                    "*_Impl\$*",
                    "*.generated.resources.*",
                    "*ModuleKt",
                    "*ModuleKt\$*",
                    "*Module_*Kt",
                    "*Module_*Kt\$*",
                    "*Previews*Kt",
                    "*Previews*Kt\$*",
                )
                annotatedBy("*Preview*")
            }
        }

        if (providers.gradleProperty("ignoreTestFailures").isPresent) {
            tasks.withType<Test>().configureEach { ignoreFailures = true }
        }

        if (this == rootProject) {
            // Merge every module that applies this plugin. Not decided by `withPlugin`: gradle.properties
            // turns on configure-on-demand, so a root-only task would see no module and write an empty
            // report. So the modules are recognised by their build file instead: a module has Kover
            // exactly when it uses one of the convention plugins that apply it. Anything else with a
            // build file (the `:backend:*` services on their own toolchain, the spike under `:tools`,
            // the benchmark and baseline-profile modules, intermediate folders) is left out, because
            // merging a project without Kover fails dependency resolution.
            val appliers = Regex("""f776\.(kotlinMultiplatform|androidApplication|desktopApplication)""")
            subprojects
                .filter { it.buildFile.exists() && appliers.containsMatchIn(it.buildFile.readText()) }
                .forEach { dependencies.add("kover", dependencies.project(mapOf("path" to it.path))) }
        }
    }
}

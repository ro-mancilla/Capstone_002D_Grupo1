package com.f776.convention

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/**
 * Stops a release build that would ship with a development configuration.
 *
 * The backend address and the quick-login row are BuildKonfig constants fixed at build time, and the
 * defaults are right for a developer's machine and wrong for anything handed to someone else: an APK
 * pointing at `localhost` simply cannot work, and the quick-login row carries the seeded passwords.
 * So a release build must say what it wants:
 *
 * - `-Pecopan.backendUrl=https://...` (or `-Pecopan.backendHost=`), or `-Pecopan.allowLocalhostRelease=true`
 *   for a deliberate local rehearsal over `adb reverse`;
 * - `-Pecopan.devQuickLogin=false`, or `=true` to keep the demo shortcut on purpose;
 * - with `-Pecopan.requireSigning=true`, release signing must be configured (see
 *   [configureAndroidApplication]: ECOPAN_KEYSTORE_FILE and friends).
 *
 * Debug builds and `run` are untouched. Every problem is listed at once.
 */
abstract class CheckReleaseConfiguration : DefaultTask() {
    @get:Input @get:Optional abstract val backendUrl: Property<String>
    @get:Input @get:Optional abstract val backendHost: Property<String>
    @get:Input abstract val allowLocalhost: Property<Boolean>
    @get:Input @get:Optional abstract val devQuickLogin: Property<String>
    @get:Input abstract val requireSigning: Property<Boolean>
    @get:Input abstract val signingConfigured: Property<Boolean>

    @TaskAction
    fun check() {
        val problems = mutableListOf<String>()

        val url = backendUrl.orNull ?: backendHost.orNull?.let { "http://$it:8080" }
        val local = url == null || listOf("localhost", "127.0.0.1", "10.0.2.2").any { url.contains(it) }
        if (local && !allowLocalhost.get()) {
            problems += "the backend address is the development default (${url ?: "http://localhost:8080"}). " +
                "Pass -Pecopan.backendUrl=https://<host>, or -Pecopan.allowLocalhostRelease=true for a local rehearsal."
        }

        if (!devQuickLogin.isPresent) {
            problems += "ecopan.devQuickLogin is not set, and it defaults to ON (the enrolment screen offers one-tap " +
                "sign-in with the seeded passwords). Pass -Pecopan.devQuickLogin=false, or =true to keep it on purpose."
        }

        if (requireSigning.get() && !signingConfigured.get()) {
            problems += "release signing is required (-Pecopan.requireSigning=true) but not configured. Set " +
                "ECOPAN_KEYSTORE_FILE, ECOPAN_KEYSTORE_PASSWORD and ECOPAN_KEY_ALIAS (and ECOPAN_KEY_PASSWORD if it " +
                "differs); scripts/make-demo-keystore.sh makes a throwaway key for rehearsals."
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Release configuration is not ready:\n" + problems.joinToString("\n") { "  - $it" },
            )
        }
    }
}

/**
 * Registers `checkReleaseConfiguration` in this project and makes every task whose name matches
 * [releaseTasks] depend on it. [signingConfigured] says whether this app can sign a release at all.
 */
internal fun Project.registerReleaseGuard(releaseTasks: Regex, signingConfigured: Provider<Boolean>) {
    val guard = tasks.register<CheckReleaseConfiguration>("checkReleaseConfiguration") {
        group = "verification"
        description = "Fails a release build that would ship the development backend address or quick-login."
        backendUrl.set(providers.gradleProperty("ecopan.backendUrl"))
        backendHost.set(providers.gradleProperty("ecopan.backendHost"))
        allowLocalhost.set(providers.gradleProperty("ecopan.allowLocalhostRelease").map { it.toBoolean() }.orElse(false))
        devQuickLogin.set(providers.gradleProperty("ecopan.devQuickLogin"))
        requireSigning.set(providers.gradleProperty("ecopan.requireSigning").map { it.toBoolean() }.orElse(false))
        this.signingConfigured.set(signingConfigured)
    }
    tasks.matching { releaseTasks.matches(it.name) }.configureEach { dependsOn(guard) }
}

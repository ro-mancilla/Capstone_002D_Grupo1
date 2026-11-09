package com.f776.ecopan.api

import io.ktor.server.config.yaml.YamlConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `Settings.readEnv(path, default)` reads **only** the Ktor config (application.yaml, or `-P:` arguments),
 * never the process environment, and falls back to its default when the key is absent. So a key the code
 * reads but the yaml does not define is silently ignored in every deployment: `database.username` was read
 * while the yaml defined `database.user`, so the app logged in as `postgres` everywhere and only worked
 * locally because that is also the local user; `broker.backend` and every `servicebus.*` key were missing
 * altogether, so `BROKER_BACKEND=service_bus` in Azure changed nothing for the app.
 *
 * Neither failure is visible in a test that boots against the compose defaults. This one compares the two
 * lists directly.
 */
class ApplicationConfigKeysTest {

    private val settingsSource = File("src/main/kotlin/com/f776/ecopan/api/core/Settings.kt")

    @Test
    fun `every config key Settings reads is defined in application yaml`() {
        assertTrue(settingsSource.exists(), "expected to run from the backend:app module directory; not found: ${settingsSource.absolutePath}")
        val keys = Regex("""readEnv\("([A-Za-z.]+)"""")
            .findAll(settingsSource.readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        assertTrue(keys.size > 20, "found only ${keys.size} keys in Settings.kt; the pattern has probably stopped matching")

        val config = assertNotNull(YamlConfig("application.yaml"), "application.yaml not found on the test classpath")
        val missing = keys.filter { config.propertyOrNull(it) == null }

        assertTrue(
            missing.isEmpty(),
            "Settings reads these keys but application.yaml does not define them, so the code default is used " +
                "in EVERY environment and the matching environment variable is ignored:\n  " + missing.joinToString("\n  "),
        )
    }
}

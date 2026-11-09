@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.e2e

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.printToString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.ecopan.App
import com.f776.ecopan.AppViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.f776.ecopan.core.datastore.APP_PREFERENCE_NAME
import com.f776.ecopan.core.datastore.ecopanAppDataDir
import com.f776.ecopan.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.Path.Companion.toPath
import com.f776.ecopan.domain.tenant.SessionProvider
import org.koin.mp.KoinPlatform
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.stopKoin

private const val STEP_TIMEOUT_MS = 30_000L

/**
 * The till's real app root in a Compose UI test: real Koin graph, a real Room database and preferences in a
 * throwaway home directory (never the developer's), the real sync engine, the backend named by `-Pecopan.backendUrl`.
 * Elements are found by their visible Spanish text; the few test tags already in the feature modules stand in
 * where text repeats (PIN keys, staff cards).
 */
internal class TillHarness(private val ui: ComposeUiTest, private val install: TillInstall) {
    private val previousHome: String? = System.getProperty("user.home")
    private val home: File = install.home
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts the till as if freshly installed: empty database, no device session. */
    fun launch() {
        // `ecopanAppDataDir()` reads user.home on every call, so this isolates the database and the token store.
        System.setProperty("user.home", home.absolutePath)
        // The preferences store is a singleton per file per process, and the app gives it a scope nobody can cancel.
        // A restart (the lock screen's journey) would find the first launch's store still holding the file, so the
        // harness builds the same store over a scope it cancels on close.
        val preferences = module {
            single<DataStore<Preferences>>(named("datastore")) {
                PreferenceDataStoreFactory.createWithPath(
                    scope = storeScope,
                    produceFile = { File(ecopanAppDataDir(), APP_PREFERENCE_NAME).absolutePath.toPath() },
                )
            }
        }
        startKoin { modules(AppModule, preferences) }
        ui.setContent {
            val viewModel = koinViewModel<AppViewModel>()
            val theme = viewModel.theme.collectAsStateWithLifecycle().value
            App(theme = theme)
        }
    }

    /**
     * First-login enrollment with a branch manager's credentials, which binds the till to that branch (a tenant-wide
     * manager would give a till with no branch, and so no per-branch stock). Returns once the catalog has synced in.
     */
    fun enroll(deviceName: String = "Caja e2e", username: String = "jefelocal", password: String = "jefelocal123") {
        waitForText("Registrar este dispositivo")
        type("Panadería (identificador)", "reference")
        type("Usuario", username)
        type("Contraseña", password)
        type("Nombre del dispositivo", deviceName)
        click("Registrar dispositivo")
        waitForText("Berlín", timeoutMs = 90_000)
    }

    /**
     * Locks the till: the lock screen appears whenever nobody is signed in. No screen offers a lock today (nothing in
     * the app calls `SessionProvider.lock()`, and a restart keeps the last user), so the journeys that need the lock
     * screen call it here -- docs/e2e/COVERAGE.md records that gap.
     */
    fun lockTill() {
        kotlinx.coroutines.runBlocking { KoinPlatform.getKoin().get<SessionProvider>().lock() }
    }

    fun close() {
        runCatching { stopKoin() }
        storeScope.cancel()
        // The previous app's background work (sync, Room) is still winding down; let it finish before the next
        // journey points user.home somewhere else and this one's directory goes away.
        Thread.sleep(3_000)
        previousHome?.let { System.setProperty("user.home", it) }
        if (install.ownedByOneRun) install.delete()
    }

    fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = STEP_TIMEOUT_MS) {
        try {
            ui.waitUntil(timeoutMillis = timeoutMs) { ui.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("waiting for \"$text\" failed.\n${semantics()}", e)
        }
    }

    fun click(text: String, substring: Boolean = false) {
        waitForText(text, substring)
        ui.onNode(hasText(text, substring = substring) and androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasSetTextAction().not()).performClick()
    }

    /** Clicks a button once it is enabled (the weigh panel's Agregar waits for a stable reading). */
    fun clickWhenEnabled(text: String, substring: Boolean = false, timeoutMs: Long = STEP_TIMEOUT_MS) {
        val enabled = hasText(text, substring = substring) and androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.isEnabled()
        ui.waitUntil(timeoutMillis = timeoutMs) { ui.onAllNodes(enabled).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(enabled).performClick()
    }

    fun assertEnabled(text: String, substring: Boolean = false, timeoutMs: Long = STEP_TIMEOUT_MS) {
        val enabled = hasText(text, substring = substring) and androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.isEnabled()
        ui.waitUntil(timeoutMillis = timeoutMs) { ui.onAllNodes(enabled).fetchSemanticsNodes().isNotEmpty() }
    }

    fun clickDescription(description: String) {
        ui.waitUntil(timeoutMillis = STEP_TIMEOUT_MS) { ui.onAllNodes(androidx.compose.ui.test.hasContentDescription(description, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(androidx.compose.ui.test.hasContentDescription(description, substring = true))[0].performClick()
    }

    fun assertDescription(description: String, timeoutMs: Long = 60_000) {
        ui.waitUntil(timeoutMillis = timeoutMs) { ui.onAllNodes(androidx.compose.ui.test.hasContentDescription(description, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    fun assertText(text: String, substring: Boolean = false) = waitForText(text, substring, timeoutMs = 10_000)

    fun assertNoText(text: String, substring: Boolean = false) {
        ui.waitUntil(timeoutMillis = 10_000) { ui.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isEmpty() }
    }

    /** Pays the sale on screen with the given method, tendering [tenderedChip] for cash, and waits for the receipt. */
    fun pay(method: String, tenderedChip: String? = null) {
        click("Cobrar", substring = true)
        waitForText("Cobro")
        click(method)
        if (tenderedChip != null) { click(tenderedChip); waitForText("VUELTO") }
    }

    /** Replaces the text of the field carrying [tag] (the feature modules' test tags, where a label alone is not unique). */
    fun typeIntoTag(tag: String, value: String) {
        ui.waitUntil(timeoutMillis = STEP_TIMEOUT_MS) { ui.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        // The tag may sit on the field itself or on the design-system wrapper around it.
        ui.onNode(androidx.compose.ui.test.hasSetTextAction() and (hasTestTag(tag) or androidx.compose.ui.test.hasAnyAncestor(hasTestTag(tag)))).performTextReplacement(value)
    }

    /** Rings up one unit product in cash with a quick-cash chip and dismisses the receipt. */
    fun sellInCash(product: String, chip: String) {
        // The grid only composes the tiles that fit the window; the search box finds any product.
        typeIntoTag("pos_search", product)
        click(product)
        typeIntoTag("pos_search", "")
        pay("Efectivo", tenderedChip = chip)
        click("Confirmar pago")
        waitForText("Venta registrada", substring = true)
        click("Nueva venta")
    }

    fun clickTag(tag: String) {
        ui.waitUntil(timeoutMillis = STEP_TIMEOUT_MS) { ui.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasTestTag(tag)).performClick()
    }

    /** Types into the field whose label is [label]. */
    fun type(label: String, value: String) {
        waitForText(label)
        field(label).performTextReplacement(value)
    }

    private fun field(label: String): SemanticsNodeInteraction =
        ui.onNode(androidx.compose.ui.test.hasSetTextAction() and (hasText(label) or androidx.compose.ui.test.hasContentDescription(label)))

    /** Every root: a payment or receipt dialog is a second root beside the window's. */
    fun semantics(): String = runCatching {
        val roots = ui.onAllNodes(isRoot(), useUnmergedTree = true)
        (0 until roots.fetchSemanticsNodes().size).joinToString("\n") { i ->
            "--- root $i ---\n" + roots[i].printToString(maxDepth = Int.MAX_VALUE)
        }
    }.getOrElse { "(no semantics tree: $it)" }

    /** On failure the screenshot and the semantics tree are written here; the runner says where. */
    fun saveFailureArtifacts(name: String) {
        val dir = File(System.getenv("ECOPAN_E2E_ARTIFACTS") ?: "build/e2e-artifacts", "till").apply { mkdirs() }
        File(dir, "$name.semantics.txt").writeText(semantics())
        runCatching {
            val image = ui.onAllNodes(isRoot())[0].captureToImage().toAwtImage()
            javax.imageio.ImageIO.write(image, "png", File(dir, "$name.png"))
        }
    }
}

/**
 * One till's persistent state (database, token store) in a throwaway directory. A journey that restarts the app
 * (the PIN lock screen only appears on a fresh start) keeps one install across several runs and deletes it itself.
 */
internal class TillInstall(val ownedByOneRun: Boolean = true) {
    val home: File = Files.createTempDirectory("ecopan-e2e-till").toFile()
    fun delete() { home.deleteRecursively() }
}

/** Runs [body] against a till (a fresh install unless [install] is given); a failure saves the screenshot and semantics tree first. */
internal fun ComposeUiTest.tillJourney(name: String, install: TillInstall = TillInstall(), body: TillHarness.() -> Unit) {
    val till = TillHarness(this, install)
    try {
        till.launch()
        till.body()
    } catch (e: Throwable) {
        till.saveFailureArtifacts(name)
        throw e
    } finally {
        till.close()
    }
}

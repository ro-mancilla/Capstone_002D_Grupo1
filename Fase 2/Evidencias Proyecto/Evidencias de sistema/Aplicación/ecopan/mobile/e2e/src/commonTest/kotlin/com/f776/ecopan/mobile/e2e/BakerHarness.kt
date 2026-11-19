@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.mobile.e2e

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.ecopan.App
import com.f776.ecopan.AppViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.mp.KoinPlatform
import androidx.compose.ui.test.hasContentDescription

private const val STEP_TIMEOUT_MS = 30_000L

/**
 * The baker app's real root in a Compose UI test: real Koin graph, Room and sync engine, against the throwaway stack.
 * Written once; Android (emulator) and iOS only differ in [startAppKoin] and [e2eApi].
 */
internal class BakerHarness(private val ui: ComposeUiTest) {

    fun launch() {
        // One app graph per process, like a real install: its preferences store is a singleton per file, so a second
        // graph over the same files would crash. The first journey enrolls; the rest start from the signed-in phone.
        if (KoinPlatform.getKoinOrNull() == null) startAppKoin()
        ui.setContent {
            val viewModel = koinViewModel<AppViewModel>()
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            App(theme = theme)
        }
    }

    /** Leaves the app's graph (and its sync engine) running for the next journey; the composition goes with the test. */
    fun close() = Unit

    /** Brings a node into view when it sits in a scrolling list; on a screen that does not scroll it is already in view. */
    private fun SemanticsNodeInteraction.scrolledIntoViewIfPossible(): SemanticsNodeInteraction =
        try { performScrollTo() } catch (_: AssertionError) { this }

    private fun tagStartingWith(prefix: String) = SemanticsMatcher("test tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    /** What is on screen, for a failure message: every root (dialogs are roots of their own). */
    fun semantics(): String = runCatching {
        val roots = ui.onAllNodes(isRoot(), useUnmergedTree = true)
        (0 until roots.fetchSemanticsNodes().size).joinToString("\n") { roots[it].printToString(maxDepth = Int.MAX_VALUE) }
    }.getOrElse { "(no semantics tree: $it)" }

    fun await(description: String, timeoutMs: Long = STEP_TIMEOUT_MS, condition: () -> Boolean) {
        try {
            ui.waitUntil(timeoutMillis = timeoutMs) { pumpMainThread(); condition() }
        } catch (e: Throwable) {
            throw AssertionError("waiting for $description failed.\n${semantics()}", e)
        }
    }

    fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = STEP_TIMEOUT_MS) =
        await("\"$text\"", timeoutMs) { ui.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    fun waitForTag(tag: String, timeoutMs: Long = STEP_TIMEOUT_MS) =
        await("tag $tag", timeoutMs) { ui.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    fun click(text: String) {
        waitForText(text)
        // The first of several (a list of rejected operations has one "Reintentar" per row).
        ui.onAllNodes(hasText(text) and hasClickAction() and hasSetTextAction().not())[0].scrolledIntoViewIfPossible().performClick()
    }

    fun clickTag(tag: String) {
        waitForTag(tag)
        ui.onNode(hasTestTag(tag)).scrolledIntoViewIfPossible().performClick()
    }

    /** Clicks the first node whose tag starts with [prefix] (a product tile's tag ends with its id). */
    fun clickTagStartingWith(prefix: String) {
        await("a tag starting with $prefix", 90_000) { ui.onAllNodes(tagStartingWith(prefix)).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(tagStartingWith(prefix))[0].scrolledIntoViewIfPossible().performClick()
    }

    /**
     * Picks [product], keys in [kilos] on the keypad and confirms (an unusual weight needs a second tap). The receipt
     * names the product, which tells this batch's from the previous one's still on screen.
     */
    fun logBatch(product: String, kilos: String) {
        click(product)
        kilos.forEach { clickTag("batch_key_$it") }
        // An unusual weight asks to be confirmed twice; give each tap time to land before judging it.
        repeat(3) {
            if (!has("$product a las", substring = true)) {
                clickTag("batch_confirm")
                runCatching { waitForText("$product a las", substring = true, timeoutMs = 4_000) }
            }
        }
        waitForText("$product a las", substring = true, timeoutMs = 10_000)
    }

    fun hasTag(tag: String, timeoutMs: Long): Boolean = runCatching { waitForTag(tag, timeoutMs) }.isSuccess

    fun type(label: String, value: String) {
        waitForText(label)
        ui.onNode(hasSetTextAction() and (hasText(label))).performTextReplacement(value)
    }

    /** Enrolls the phone if this is a fresh install; a phone enrolled by an earlier journey is already on its home screen. */
    fun ensureEnrolled(username: String = "panadero", password: String = "panadero123") {
        await("the enrollment form or the batch screen", 60_000) {
            has("Registrar este dispositivo") || has("¿Qué estás amasando?")
        }
        if (has("Registrar este dispositivo")) enroll(username, password)
        waitForText("¿Qué estás amasando?", timeoutMs = 90_000)
    }

    fun has(text: String, substring: Boolean = false) =
        ui.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    fun hasDescription(description: String) =
        ui.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()

    fun clickDescription(description: String) {
        await("\"$description\"") { hasDescription(description) }
        ui.onNode(hasContentDescription(description)).scrolledIntoViewIfPossible().performClick()
    }

    /** Asserts after letting the screen settle: [text] is not shown. */
    fun assertNoText(text: String, substring: Boolean = false) {
        check(!has(text, substring)) { "\"$text\" is on screen but should not be.\n${semantics()}" }
    }

    fun assertText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) = waitForText(text, substring, timeoutMs)

    fun enroll(username: String, password: String) {
        waitForText("Registrar este dispositivo")
        type("Panadería (identificador)", "reference")
        type("Usuario", username)
        type("Contraseña", password)
        type("Nombre del dispositivo", "Teléfono e2e")
        click("Registrar dispositivo")
    }
}

internal fun currentMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

@file:OptIn(ExperimentalComposeUiApi::class)

package com.f776.ecopan.feature.pos.screens.pos.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.dp
import kotlin.time.Clock

/** Sub-~100ms inter-key gap ⇒ HID wedge burst, not a human typing (05 §3 item 4). */
private const val SCANNER_MAX_INTER_KEY_GAP_MS = 100L

/**
 * Invisible, always-focused key-event sink for keyboard-wedge barcode scanners (05 §3 item 4,
 * §9 work item 8). A HID scanner needs no driver — it types its payload as a fast keystroke burst
 * terminated by Enter into whatever currently has focus, so this composable simply keeps a
 * near-zero-size focusable target focused and buffers keystrokes by timing.
 *
 * [blockingFocus] must be true whenever a manual-weight or cash-tendered text field is open — this
 * sink never steals focus from those (05 §3 item 4, §6 edge case "scanner burst into an open text
 * field"): while blocked it neither holds focus nor consumes key events.
 */
@Composable
internal fun BarcodeCapture(
    blockingFocus: Boolean,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var buffer by remember { mutableStateOf("") }
    var lastKeyAtMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(blockingFocus) {
        if (!blockingFocus) focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (blockingFocus || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val now = Clock.System.now().toEpochMilliseconds()
                val gapMs = now - lastKeyAtMs
                lastKeyAtMs = now

                when {
                    event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                        val scanned = buffer
                        buffer = ""
                        if (scanned.isNotEmpty()) onBarcodeScanned(scanned)
                        true
                    }
                    gapMs > SCANNER_MAX_INTER_KEY_GAP_MS -> {
                        // Gap too long to be a scanner burst -- drop the stale buffer and start
                        // a fresh one from this key (05 §3 item 4 timing heuristic).
                        val codePoint = event.utf16CodePoint
                        buffer = if (codePoint > 0) codePoint.toChar().toString() else ""
                        false
                    }
                    else -> {
                        val codePoint = event.utf16CodePoint
                        if (codePoint > 0) buffer += codePoint.toChar()
                        false
                    }
                }
            },
    )
}

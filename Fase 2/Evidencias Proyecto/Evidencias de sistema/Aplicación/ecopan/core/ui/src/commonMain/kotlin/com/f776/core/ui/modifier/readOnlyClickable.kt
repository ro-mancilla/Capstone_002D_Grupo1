package com.f776.core.ui.modifier

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.readOnlyClickable(onClick: () -> Unit) = then(pointerInput(Unit) {
    // Modifier.clickable doesn't work for text fields, so we use Modifier.pointerInput
    // in the Initial pass to observe events before the text field consumes them
    // in the Main pass.
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial)
        val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
        if (upEvent != null) {
            onClick()
        }
    }
})
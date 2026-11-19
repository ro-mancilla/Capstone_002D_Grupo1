package com.f776.core.ui.appstate

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SnackbarAppState(
    private val snackbarScope: CoroutineScope,
    val snackbarHostState: SnackbarHostState
) {
    fun showSnackbar(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        snackbarScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = duration
            )
        }
    }

    /**
     * Shows [message] with a single action button. [onActionPerformed] runs only when the user
     * taps the action, not when the snackbar times out or is swiped away, so it is safe to use
     * for undoing a change that has already been written.
     */
    fun showSnackbar(
        message: String,
        actionLabel: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onActionPerformed: () -> Unit
    ) {
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = false,
                duration = duration
            )
            if (result == SnackbarResult.ActionPerformed) onActionPerformed()
        }
    }
}

@Composable
fun rememberSnackbarAppState(
    snackbarHostState: SnackbarHostState = remember {
        SnackbarHostState()
    },
    snackbarScope: CoroutineScope = rememberCoroutineScope()
) = remember(snackbarHostState, snackbarScope) {
    SnackbarAppState(
        snackbarHostState = snackbarHostState,
        snackbarScope = snackbarScope
    )
}
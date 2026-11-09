package com.f776.ecopan.data.reminder.android

import android.content.BroadcastReceiver
import android.content.BroadcastReceiver.PendingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// Source - https://stackoverflow.com/a
// Posted by Dimitrij Brechko
// Retrieved 2025-12-14, License - CC BY-SA 4.0
internal fun BroadcastReceiver.doAsync(
    appScope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
) {
    // Platform type: goAsync() returns null when the receiver was not dispatched by the system,
    // which is the case when onReceive is called directly, as tests do.
    val pendingResult: PendingResult? = goAsync()
    appScope.launch(coroutineContext) { block() }.invokeOnCompletion { pendingResult?.finish() }
}

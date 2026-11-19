package com.f776.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.f776.core.designsystem.icon.EcopanIcons
import ecopan.core.designsystem.generated.resources.Res
import ecopan.core.designsystem.generated.resources.sync_status_attention
import ecopan.core.designsystem.generated.resources.sync_status_indicator_cd
import ecopan.core.designsystem.generated.resources.sync_status_offline
import ecopan.core.designsystem.generated.resources.sync_status_offline_pending
import ecopan.core.designsystem.generated.resources.sync_status_synced
import ecopan.core.designsystem.generated.resources.sync_status_syncing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * UI-level model for the network-state indicator -- deliberately **not** the domain `SyncStatus`
 * (the design system stays domain-free). Mapping domain status to this lives in the app layer.
 */
sealed interface SyncIndicatorState {
    data object OnlineIdle : SyncIndicatorState
    data object Syncing : SyncIndicatorState

    /** Offline (or backing off) with [count] outbox rows still waiting to push. */
    data class OfflinePending(val count: Int) : SyncIndicatorState

    /** `AuthRequired`/`Error` -- something needs a human (re-login, dead-lettered row, etc). */
    data class Attention(val count: Int) : SyncIndicatorState
}

/**
 * The always-visible sync status: cloud-check when synced, a refresh icon while syncing, cloud-off
 * with neutral copy when offline (being offline is normal for this app, so it is never red), and a
 * warning when something needs a human. Tapping opens whatever detail the host wants; this only
 * renders the glanceable summary.
 */
@Composable
fun SyncStatusIndicator(
    state: SyncIndicatorState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill: Triple<StatusTone, androidx.compose.ui.graphics.vector.ImageVector, String> = when (state) {
        SyncIndicatorState.OnlineIdle -> Triple(StatusTone.Success, EcopanIcons.CloudCheck, stringResource(Res.string.sync_status_synced))
        SyncIndicatorState.Syncing -> Triple(StatusTone.Info, EcopanIcons.Refresh, stringResource(Res.string.sync_status_syncing))
        is SyncIndicatorState.OfflinePending -> Triple(
            StatusTone.Neutral,
            EcopanIcons.CloudOff,
            // Offline with nothing waiting is "Sin conexión", not "Sin conexión · 0 pendientes".
            if (state.count == 0) {
                stringResource(Res.string.sync_status_offline)
            } else {
                pluralStringResource(Res.plurals.sync_status_offline_pending, state.count, state.count)
            },
        )

        is SyncIndicatorState.Attention -> Triple(StatusTone.Warning, EcopanIcons.Warning, stringResource(Res.string.sync_status_attention))
    }
    StatusPill(
        label = pill.third,
        tone = pill.first,
        icon = pill.second,
        onClick = onClick,
        onClickLabel = stringResource(Res.string.sync_status_indicator_cd),
        modifier = modifier,
    )
}

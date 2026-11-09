package com.f776.ecopan.feature.production.screens.stockglance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.core.designsystem.component.EcopanAlert
import com.f776.core.designsystem.component.EcopanCard
import com.f776.core.designsystem.component.EcopanIconButton
import com.f776.core.designsystem.component.HintMessage
import com.f776.core.designsystem.component.StatusPill
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.core.designsystem.component.SyncStatusIndicator
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.ui.components.responsiveWidth
import com.f776.core.ui.preview.AdaptivePreview
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.ecopan.feature.production.screens.formatStockKg
import com.f776.ecopan.feature.production.screens.formatSyncMoment
import ecopan.mobile.feature.production.impl.generated.resources.Res
import ecopan.mobile.feature.production.impl.generated.resources.offline_last_sync
import ecopan.mobile.feature.production.impl.generated.resources.stock_back_cd
import ecopan.mobile.feature.production.impl.generated.resources.stock_badge_low
import ecopan.mobile.feature.production.impl.generated.resources.stock_badge_negative
import ecopan.mobile.feature.production.impl.generated.resources.stock_empty
import ecopan.mobile.feature.production.impl.generated.resources.stock_negative_alert
import ecopan.mobile.feature.production.impl.generated.resources.stock_no_reorder_point
import ecopan.mobile.feature.production.impl.generated.resources.stock_quantity_kg
import ecopan.mobile.feature.production.impl.generated.resources.stock_reorder_at
import ecopan.mobile.feature.production.impl.generated.resources.stock_section_ok
import ecopan.mobile.feature.production.impl.generated.resources.stock_section_reorder
import ecopan.mobile.feature.production.impl.generated.resources.stock_subtitle
import ecopan.mobile.feature.production.impl.generated.resources.stock_subtitle_synced
import ecopan.mobile.feature.production.impl.generated.resources.stock_title
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Composable
internal fun StockGlanceScreenRoot(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    viewModel: StockGlanceViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StockGlanceScreen(
        modifier = modifier,
        state = state,
        onBack = onBack,
        onSyncIndicatorTapped = viewModel::onSyncIndicatorTapped,
    )
}

@Composable
private fun StockGlanceScreen(
    modifier: Modifier = Modifier,
    state: StockGlanceUiState,
    onBack: () -> Unit,
    onSyncIndicatorTapped: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = EcopanTheme.colors.page,
        topBar = { StockTopBar(state = state, onBack = onBack, onSyncIndicatorTapped = onSyncIndicatorTapped) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                HintMessage(icon = EcopanIcons.Inventory, title = stringResource(Res.string.stock_empty))
            }
            return@Scaffold
        }
        val stamp = offlineStamp(state)
        // Capped width: on a tablet or landscape phone, rows otherwise put the name and its
        // weight the whole screen apart.
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.responsiveWidth(),
                contentPadding = PaddingValues(
                    start = EcopanSpacing.screenMargin,
                    top = EcopanSpacing.space2,
                    end = EcopanSpacing.screenMargin,
                    bottom = EcopanSpacing.space6,
                ),
                verticalArrangement = Arrangement.spacedBy(EcopanSpacing.cardGap),
            ) {
                // A ledger that has gone negative is a data problem, not a stock problem: batches
                // were logged against deliveries nobody recorded. Saying so once at the top, and
                // telling the baker who can fix it, beats a column of red numbers.
                if (state.negativeCount > 0) {
                    item {
                        EcopanAlert(
                            message = pluralStringResource(
                                Res.plurals.stock_negative_alert,
                                state.negativeCount,
                                state.negativeCount,
                                state.negativeRows.first().name,
                            ),
                        )
                    }
                }
                stamp?.let { stamp ->
                    item { Text(stamp, style = EcopanTheme.typography.bodySmall, color = EcopanTheme.colors.inkMuted) }
                }
                if (state.toReplenish.isNotEmpty()) {
                    item {
                        StockSection(
                            title = stringResource(Res.string.stock_section_reorder, state.toReplenish.size),
                            rows = state.toReplenish,
                            prominent = true,
                        )
                    }
                }
                if (state.inStock.isNotEmpty()) {
                    item {
                        StockSection(
                            title = stringResource(Res.string.stock_section_ok, state.inStock.size),
                            rows = state.inStock,
                            prominent = false,
                        )
                    }
                }
            }
        }
    }
}

/** "Datos de la última sincronización, 05:10" while offline and the time is known; otherwise nothing. */
@Composable
private fun offlineStamp(state: StockGlanceUiState): String? {
    val at = state.lastSyncedAt
    if (!state.offline || at == null) return null
    return stringResource(Res.string.offline_last_sync, syncMoment(at, todayWord = false))
}

@Composable
private fun syncMoment(at: Instant, todayWord: Boolean): String {
    val zone = remember { TimeZone.currentSystemDefault() }
    return formatSyncMoment(at, Clock.System.now(), zone, todayWord)
}

@Composable
private fun StockTopBar(state: StockGlanceUiState, onBack: () -> Unit, onSyncIndicatorTapped: () -> Unit) {
    val c = EcopanTheme.colors
    val at = state.lastSyncedAt
    val subtitle = if (at != null && !state.offline) {
        stringResource(Res.string.stock_subtitle_synced, syncMoment(at, todayWord = false))
    } else {
        stringResource(Res.string.stock_subtitle)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Same insets Material's TopAppBar applies (BatchEntry's bar): this bar is hand-rolled,
            // so without it the title draws under the status bar / display cutout.
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(horizontal = EcopanSpacing.space2, vertical = EcopanSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EcopanIconButton(
            onClick = onBack,
            icon = EcopanIcons.ArrowBack,
            contentDescription = stringResource(Res.string.stock_back_cd),
            touch = true,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.stock_title),
                style = EcopanTheme.typography.subtitle,
                color = c.inkStrong,
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, style = EcopanTheme.typography.bodySmall, color = c.inkMuted)
        }
        // 03 §6.1: the sync state is visible on every baker screen. While everything is in sync the
        // subtitle already says so, so the pill only appears when there is something to know.
        if (state.syncIndicator != SyncIndicatorState.OnlineIdle) {
            SyncStatusIndicator(state = state.syncIndicator, onClick = onSyncIndicatorTapped)
        }
    }
}

@Composable
private fun StockSection(title: String, rows: List<StockRowUi>, prominent: Boolean) {
    val c = EcopanTheme.colors
    EcopanCard(contentPadding = PaddingValues(0.dp)) {
        Text(
            title,
            style = EcopanTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
            color = c.inkBody,
            modifier = Modifier
                .padding(start = EcopanSpacing.space4, top = EcopanSpacing.space4, end = EcopanSpacing.space4, bottom = EcopanSpacing.space2)
                .semantics { heading() },
        )
        rows.forEach { row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            StockRow(row = row, prominent = prominent)
        }
    }
}

/**
 * A balance. Not tappable -- nothing happens when a baker taps a quantity -- so no press state and
 * no chevron. The quantity never wraps; the name takes what is left and wraps to two lines.
 */
@Composable
private fun StockRow(row: StockRowUi, prominent: Boolean) {
    val c = EcopanTheme.colors
    val min = if (prominent) EcopanSpacing.rowMin + EcopanSpacing.space4 else EcopanSpacing.rowMin
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = min)
            .padding(horizontal = EcopanSpacing.space4, vertical = EcopanSpacing.space2)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space1)) {
            Text(
                row.name,
                style = EcopanTheme.typography.subtitle.copy(fontWeight = FontWeight.Medium),
                color = c.inkStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (row.status) {
                StockStatus.NEGATIVE -> StatusPill(stringResource(Res.string.stock_badge_negative), StatusTone.Danger, icon = EcopanIcons.Warning)
                StockStatus.LOW -> StatusPill(stringResource(Res.string.stock_badge_low), StatusTone.Warning, icon = EcopanIcons.ArrowDown)
                StockStatus.OK -> Unit
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(Res.string.stock_quantity_kg, formatStockKg(row.currentGrams)),
                style = if (prominent) EcopanTheme.typography.quantityMd else EcopanTheme.typography.quantitySm,
                color = if (row.status == StockStatus.NEGATIVE) c.danger.fg else c.inkStrong,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
            )
            Text(
                row.lowStockThresholdGrams?.let { stringResource(Res.string.stock_reorder_at, formatStockKg(it)) }
                    ?: stringResource(Res.string.stock_no_reorder_point),
                style = EcopanTheme.typography.bodySmall,
                color = c.inkMuted,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun previewState(syncIndicator: SyncIndicatorState = SyncIndicatorState.OnlineIdle) = StockGlanceUiState(
    loading = false,
    rows = listOf(
        StockRowUi(Uuid.random(), "Azúcar", -200, 3_000),
        StockRowUi(Uuid.random(), "Manteca", 0, 5_000),
        StockRowUi(Uuid.random(), "Levadura", 1_000, 1_000),
        StockRowUi(Uuid.random(), "Harina", 194_048, 20_000),
        StockRowUi(Uuid.random(), "Agua", 146_131, null),
        StockRowUi(Uuid.random(), "Sal", 9_881, 2_000),
    ),
    syncIndicator = syncIndicator,
    lastSyncedAt = Instant.fromEpochMilliseconds(1_785_000_000_000),
)

@AdaptivePreview
@LocalizedPreview
@Composable
private fun StockGlanceScreenPreview() {
    EcopanTheme { StockGlanceScreen(state = previewState(), onBack = {}, onSyncIndicatorTapped = {}) }
}

@AdaptivePreview
@Composable
private fun StockGlanceScreenOfflinePreview() {
    EcopanTheme {
        StockGlanceScreen(state = previewState(SyncIndicatorState.OfflinePending(2)), onBack = {}, onSyncIndicatorTapped = {})
    }
}

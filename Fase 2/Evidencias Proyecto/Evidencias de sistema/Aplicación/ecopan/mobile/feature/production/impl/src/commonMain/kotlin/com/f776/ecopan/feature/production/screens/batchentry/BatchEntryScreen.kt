package com.f776.ecopan.feature.production.screens.batchentry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.core.ui.appstate.LocalSnackbarAppState
import com.f776.core.ui.components.ObserveAsEvents
import com.f776.core.designsystem.component.StatusPill
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.core.designsystem.component.SyncStatusIndicator
import com.f776.core.ui.preview.AdaptivePreview
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.figureStyle
import com.f776.core.designsystem.theme.monoFontFamily
import com.f776.ecopan.domain.production.BatchSyncState
import com.f776.ecopan.feature.production.screens.batchentry.model.BatchRowUi
import com.f776.ecopan.feature.production.screens.batchentry.model.LoggedBatchUi
import com.f776.ecopan.feature.production.screens.batchentry.model.ProductTileUi
import com.f776.ecopan.feature.production.screens.batchentry.model.WeightValidation
import com.f776.ecopan.feature.production.screens.formatAmount
import com.f776.ecopan.feature.production.screens.formatKg
import com.f776.ecopan.feature.production.screens.formatSyncMoment
import ecopan.mobile.feature.production.impl.generated.resources.offline_last_sync
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.remember
import com.f776.core.designsystem.component.EcopanFilterChip
import com.f776.core.designsystem.theme.EcopanSpacing
import ecopan.mobile.feature.production.impl.generated.resources.Res
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_backspace_key
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_confirm_armed_button
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_confirm_button
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_confirm_button_weight
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_decrement_body
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_decrement_empty
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_decrement_none
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_decrement_title
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_estimated_units
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_formula_hint
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_formula_materials
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_kg_suffix
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_logged_dismiss_cd
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_logged_no_decrement_snackbar
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_logged_note
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_logged_note_no_decrement
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_logged_snackbar
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_logged_title
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_no_formula_hint
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_no_products
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_product_question
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_scale_source
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_select_prompt
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_stock_glance_link
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_sync_pending
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_sync_rejected
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_sync_synced
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_today_empty
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_today_header
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_today_total
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_weight_empty
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_weight_invalid
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_weight_ready
import ecopan.mobile.feature.production.impl.generated.resources.batch_entry_weight_unusual
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The keypad, in reading order. `.` is the decimal key: the view model parses with
 * [String.toDoubleOrNull], which only accepts a dot, while the key *renders* as a comma because
 * that is the separator on a Chilean scale's own display. The two never diverge because this is
 * the only place the display form is produced.
 */
private const val DECIMAL_KEY = '.'
private const val DECIMAL_GLYPH = ","

/** A key on the weight pad. The erase key used to be the character `U+232B` acting as a sentinel in
 * a `List<Char>`; typing it means no glyph has to stand in for a key that is not a character. */
private sealed interface PadKey {
    data class Entry(val char: Char) : PadKey
    data object Backspace : PadKey
}

private val KEYPAD_ROWS: List<List<PadKey>> = listOf(
    listOf(PadKey.Entry('7'), PadKey.Entry('8'), PadKey.Entry('9')),
    listOf(PadKey.Entry('4'), PadKey.Entry('5'), PadKey.Entry('6')),
    listOf(PadKey.Entry('1'), PadKey.Entry('2'), PadKey.Entry('3')),
    listOf(PadKey.Entry(DECIMAL_KEY), PadKey.Entry('0'), PadKey.Backspace),
)

/**
 * Test tags, the selectors of the Baseline Profile journeys (`:baselineProfile`, which cannot see this
 * module, so it spells the same strings). The app exposes them as resource ids to UI Automator.
 */
private const val TAG_PRODUCT_PREFIX = "batch_product_"
private const val TAG_KEY_PREFIX = "batch_key_"
private const val TAG_CONFIRM = "batch_confirm"
private const val TAG_LOGGED = "batch_logged"
private const val TAG_SYNC_INDICATOR = "sync_indicator"
private const val TAG_STOCK_GLANCE_LINK = "batch_stock_glance_link"

/** The tag of a keypad key: its digit, or `decimal` / `backspace`. */
private fun PadKey.tag(): String = TAG_KEY_PREFIX + when (this) {
    PadKey.Backspace -> "backspace"
    is PadKey.Entry -> if (char == DECIMAL_KEY) "decimal" else char.toString()
}

/** The screen's horizontal page margin. Named because the chip row has to cancel it and re-apply it. */
private val PAGE_GUTTER = 20.dp

/** Cancels [PAGE_GUTTER] so a horizontally scrolling row can bleed to the screen edges. */
private fun Modifier.offsetGutter(): Modifier = layout { measurable, constraints ->
    val widened = constraints.copy(maxWidth = constraints.maxWidth + (PAGE_GUTTER * 2).roundToPx())
    val placeable = measurable.measure(widened)
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-PAGE_GUTTER.roundToPx(), 0)
    }
}

/** "Datos de la última sincronización, hoy 04:50" while offline and the time is known; otherwise null. */
@Composable
private fun offlineLastSyncLine(state: BatchEntryUiState): String? {
    val at = state.lastSyncedAt
    if (state.syncIndicator !is SyncIndicatorState.OfflinePending || at == null) return null
    val zone = remember { TimeZone.currentSystemDefault() }
    return stringResource(Res.string.offline_last_sync, formatSyncMoment(at, Clock.System.now(), zone, todayWord = true))
}

@Composable
internal fun BatchEntryScreenRoot(
    modifier: Modifier = Modifier,
    onOpenStockGlance: () -> Unit,
    viewModel: BatchEntryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarAppState = LocalSnackbarAppState.current
    val scope = rememberCoroutineScope()
    val noDecrementMessage = stringResource(Res.string.batch_entry_logged_no_decrement_snackbar)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(BatchEntryAction.OnResumed) }

    ObserveAsEvents(viewModel.eventChannel) { event ->
        when (event) {
            is BatchEntryEvent.BatchLogged -> scope.launch {
                snackbarAppState.showSnackbar(
                    getString(Res.string.batch_entry_logged_snackbar, event.productName, event.kgLabel)
                )
            }
            is BatchEntryEvent.BatchLoggedNoDecrement -> scope.launch { snackbarAppState.showSnackbar(noDecrementMessage) }
        }
    }

    BatchEntryScreen(
        modifier = modifier,
        state = state,
        onAction = viewModel::onAction,
        onOpenStockGlance = onOpenStockGlance,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchEntryScreen(
    modifier: Modifier = Modifier,
    state: BatchEntryUiState,
    onAction: (BatchEntryAction) -> Unit,
    onOpenStockGlance: () -> Unit,
) {
    // Layout follows the window, not the device: a phone in landscape is wide and short, a tablet is
    // wide and tall. Wide windows split into a "context" pane and a keypad pane; short windows also
    // get a slimmer header and shorter keys, because 64dp of header on a ~400dp-tall window is a
    // sixth of the screen.
    BoxWithConstraints(modifier = modifier) {
        val windowWidth = maxWidth
        val wide = windowWidth >= WIDE_LAYOUT_MIN_WIDTH
        val short = maxHeight < SHORT_LAYOUT_MAX_HEIGHT
        val keyHeight = if (short) 48.dp else 56.dp
        Scaffold(
            // 06 §7's header: sync indicator left, tenant name right. Persistent, so the indicator
            // is "visible at all times on the baker logging screen" as 03 §6.1 requires -- it must
            // not be inside the Column below, which scrolls away under the keypad.
            topBar = {
                if (short) {
                    CompactHeader(state = state, onAction = onAction)
                } else {
                    TopAppBar(
                        title = { SyncPill(state, onAction) },
                        actions = { TenantName(state.tenantName) },
                    )
                }
            },
        ) { padding ->
            if (state.loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(padding)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER / 2, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ContextPane(state = state, onAction = onAction, onOpenStockGlance = onOpenStockGlance)
                    }
                    // The keypad pane is capped: a 2400px-wide pad of 56dp keys is a row of
                    // sprawling buttons nobody can reach with one thumb.
                    Column(
                        modifier = Modifier
                            .width((windowWidth * 0.4f).coerceIn(280.dp, 380.dp))
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = PAGE_GUTTER / 2, end = PAGE_GUTTER, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Keypad(
                            keyHeight = keyHeight,
                            onDigit = { onAction(BatchEntryAction.OnKeypadDigit(it)) },
                            onBackspace = { onAction(BatchEntryAction.OnKeypadBackspace) },
                        )
                        ConfirmButton(state = state, onAction = onAction)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PAGE_GUTTER, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ContextPane(
                        state = state,
                        onAction = onAction,
                        onOpenStockGlance = onOpenStockGlance,
                        // Single column: the keypad and confirm sit between the readout and the
                        // preview, as they always did.
                        betweenReadoutAndPreview = {
                            Keypad(
                                keyHeight = keyHeight,
                                onDigit = { onAction(BatchEntryAction.OnKeypadDigit(it)) },
                                onBackspace = { onAction(BatchEntryAction.OnKeypadBackspace) },
                            )
                        },
                        afterPreview = { ConfirmButton(state = state, onAction = onAction) },
                    )
                }
            }
        }
    }
}

/** Width from which the screen splits into a context pane and a keypad pane (content area, rail excluded). */
private val WIDE_LAYOUT_MIN_WIDTH = 560.dp

/** Height below which the header and keys shrink: a phone held sideways. */
private val SHORT_LAYOUT_MAX_HEIGHT = 480.dp

@Composable
private fun SyncPill(state: BatchEntryUiState, onAction: (BatchEntryAction) -> Unit) {
    SyncStatusIndicator(
        state = state.syncIndicator,
        onClick = { onAction(BatchEntryAction.OnSyncIndicatorTapped) },
        modifier = Modifier.testTag(TAG_SYNC_INDICATOR),
    )
}

@Composable
private fun TenantName(name: String) {
    if (name.isEmpty()) return
    Text(
        name,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 16.dp),
    )
}

/** The short-window stand-in for the top app bar: same content, one touch target tall instead of 64dp. */
@Composable
private fun CompactHeader(state: BatchEntryUiState, onAction: (BatchEntryAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER - 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { SyncPill(state, onAction) }
        TenantName(state.tenantName)
    }
}

/**
 * Everything on the screen except the keypad and confirm button: suggestion, product picker, scale
 * readout, deduction preview, the receipt, today's ledger. In the single-column layout the keypad
 * and confirm button are slotted back in at their old positions.
 */
@Composable
private fun ColumnScope.ContextPane(
    state: BatchEntryUiState,
    onAction: (BatchEntryAction) -> Unit,
    onOpenStockGlance: () -> Unit,
    betweenReadoutAndPreview: @Composable () -> Unit = {},
    afterPreview: @Composable () -> Unit = {},
) {
    state.suggestedProduction?.let { suggestion ->
        SuggestedProductionCard(
            suggestion = suggestion,
            expanded = state.suggestedExpanded,
            offlineStamp = offlineLastSyncLine(state),
            onToggleExpanded = { onAction(BatchEntryAction.OnToggleSuggestedExpanded) },
        )
    }

    ProductPicker(
        products = state.products,
        selected = state.selectedProduct,
        onSelect = { onAction(BatchEntryAction.OnSelectProduct(it)) },
    )

    ScaleReadout(weightInput = state.weightInput, validation = state.validation)

    betweenReadoutAndPreview()

    DecrementPreview(state = state)

    afterPreview()

    state.lastLogged?.let { logged ->
        LoggedBanner(logged = logged, onDismiss = { onAction(BatchEntryAction.OnDismissLogged) })
    }

    TodaySection(rows = state.todaysBatches, totalGrams = state.todaysTotalGrams)

    if (state.stockGlanceEnabled) {
        OutlinedButton(
            onClick = onOpenStockGlance,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag(TAG_STOCK_GLANCE_LINK),
        ) {
            Text(stringResource(Res.string.batch_entry_stock_glance_link))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun ConfirmButton(state: BatchEntryUiState, onAction: (BatchEntryAction) -> Unit) {
    ConfirmButton(
        // The *parsed* weight, not the keypad echo. Mid-entry the echo is a half-typed
        // string -- typing "25," made the button read "Registrar lote · 25, kg" -- and the
        // button's job is to state what will actually be recorded.
        weightLabel = state.mixWeightGrams?.let { formatAmount(it) }.orEmpty(),
        hasWeight = state.mixWeightGrams != null,
        enabled = state.validation != WeightValidation.EMPTY &&
            state.validation != WeightValidation.HARD_INVALID &&
            !state.confirmInFlight,
        armed = state.confirmArmed,
        onClick = { onAction(BatchEntryAction.OnConfirmRequested) },
    )
}

/**
 * A single scrolling row of chips, deliberately not the wrapping tile grid this screen used to
 * open with. The design puts one compact control here and gives the reclaimed height to the
 * keypad, which is the control the baker actually uses on every batch; a wrapping grid of six
 * products pushed the keypad below the fold and made every entry start with a scroll.
 *
 * It is a chip row rather than the design's dropdown because selection stays one tap: bakers log
 * the same product repeatedly (06 §7.1), and a menu would turn that into two taps plus a
 * disambiguation, for no height saved over a single 48dp row.
 */
@Composable
private fun ProductPicker(
    products: List<ProductTileUi>,
    selected: ProductTileUi?,
    onSelect: (kotlin.uuid.Uuid) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.batch_entry_product_question),
            style = MaterialTheme.typography.titleMedium,
        )
        if (products.isEmpty()) {
            Text(
                stringResource(Res.string.batch_entry_no_products),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        // Picking a chip near the right edge left it half off-screen ("✓ Marr…"); bring the
        // selection into view whenever it changes (including a selection restored on return).
        val listState = rememberLazyListState()
        LaunchedEffect(selected?.id) {
            val index = products.indexOfFirst { it.id == selected?.id }
            if (index >= 0) listState.animateScrollToItem(index)
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // The row bleeds past the page gutter on both sides so a half-visible chip signals
            // "more to scroll", and the content padding puts the first and last chips back on the
            // page grid. Without the negative offset the row was clipped at the gutter and the
            // last chip was cut mid-word with no scroll affordance.
            modifier = Modifier.fillMaxWidth().offsetGutter(),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
        ) {
            items(products, key = { it.id.toString() }) { product ->
                EcopanFilterChip(
                    selected = product.id == selected?.id,
                    onClick = { onSelect(product.id) },
                    label = product.name,
                    // The chips are the only way to choose a product, and the baker taps them
                    // with floured hands: a full touch target, not the 44dp control height.
                    modifier = Modifier.defaultMinSize(minHeight = EcopanSpacing.touch).testTag(TAG_PRODUCT_PREFIX + product.id),
                )
            }
        }
        // The hint answers "and what will that take out of stock?" before any weight is typed --
        // it is the only place the formula's size is visible on this screen.
        Text(
            text = when {
                // No product is preselected on purpose: a batch logged against a silently
                // defaulted product decrements the wrong ingredients, and nothing on this screen
                // would have told the baker. The prompt keeps the line's height either way, so
                // choosing a product does not shift the keypad.
                selected == null -> stringResource(Res.string.batch_entry_select_prompt)
                selected.hasFormula -> stringResource(
                    Res.string.batch_entry_formula_hint,
                    selected.name.lowercase(),
                    pluralStringResource(
                        Res.plurals.batch_entry_formula_materials,
                        selected.materialCount,
                        selected.materialCount,
                    ),
                )
                else -> stringResource(Res.string.batch_entry_no_formula_hint, selected.name)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (selected?.hasFormula == false) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * The hero of the screen: where the weight is, what it came from, and whether it is loggable.
 * The state line on the right is the part that was missing before -- the old readout showed a
 * number with no verdict, so an out-of-range mix looked identical to a good one until the confirm
 * button refused.
 */
@Composable
private fun ScaleReadout(weightInput: String, validation: WeightValidation) {
    val (stateLabel, stateColor) = when (validation) {
        WeightValidation.EMPTY -> stringResource(Res.string.batch_entry_weight_empty) to
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        WeightValidation.HARD_INVALID -> stringResource(Res.string.batch_entry_weight_invalid) to
            MaterialTheme.colorScheme.error
        WeightValidation.OUT_OF_SOFT_BOUNDS -> stringResource(Res.string.batch_entry_weight_unusual) to
            MaterialTheme.colorScheme.error
        WeightValidation.OK -> stringResource(Res.string.batch_entry_weight_ready) to
            MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.batch_entry_scale_source),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = stateColor)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    weightInput.toDisplay(),
                    style = figureStyle(MaterialTheme.typography.displayMedium),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(Res.string.batch_entry_kg_suffix),
                    style = figureStyle(MaterialTheme.typography.titleMedium),
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * Twelve pill keys on a 3-wide grid. Height is fixed at 56dp rather than derived from an aspect
 * ratio: an aspect ratio ties key height to screen width, so the same keypad was noticeably
 * squatter on a narrow phone than on a wide one — the one control on this screen whose size
 * should not depend on the device.
 */
@Composable
private fun Keypad(keyHeight: Dp, onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        KEYPAD_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                PadKey.Backspace -> onBackspace()
                                is PadKey.Entry -> onDigit(key.char)
                            }
                        },
                        modifier = Modifier.weight(1f).height(keyHeight).testTag(key.tag()),
                        shape = MaterialTheme.shapes.extraLarge,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        when (key) {
                            // The visible word is already the accessible label; no
                            // contentDescription to drift out of sync with it. A word rather than
                            // the backspace symbol because this pad is used with floury hands at
                            // arm's length, where a 16dp glyph is the hardest key to hit.
                            PadKey.Backspace -> Text(
                                stringResource(Res.string.batch_entry_backspace_key),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )

                            is PadKey.Entry -> Text(
                                if (key.char == DECIMAL_KEY) DECIMAL_GLYPH else key.char.toString(),
                                style = figureStyle(MaterialTheme.typography.headlineSmall),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * States plainly what confirming will subtract. It renders even with no weight typed, holding its
 * place in the layout and carrying the "type a weight" prompt — the card used to appear only once
 * a preview existed, so the button below it jumped up the screen mid-entry.
 */
@Composable
private fun DecrementPreview(state: BatchEntryUiState) {
    val preview = state.derivedPreview
    val body = when {
        state.selectedProduct?.hasFormula == false -> stringResource(Res.string.batch_entry_decrement_none)
        preview.isNullOrEmpty() -> stringResource(Res.string.batch_entry_decrement_empty)
        else -> stringResource(
            Res.string.batch_entry_decrement_body,
            preview.joinToString(" · ") { "${it.name.lowercase()} ${it.label}" },
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.batch_entry_decrement_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium)
            state.estimatedUnits?.let {
                Text(
                    stringResource(Res.string.batch_entry_estimated_units, it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfirmButton(
    weightLabel: String,
    hasWeight: Boolean,
    enabled: Boolean,
    armed: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp).testTag(TAG_CONFIRM),
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(
            when {
                armed -> stringResource(Res.string.batch_entry_confirm_armed_button, weightLabel)
                hasWeight -> stringResource(Res.string.batch_entry_confirm_button_weight, weightLabel)
                else -> stringResource(Res.string.batch_entry_confirm_button)
            },
            style = figureStyle(MaterialTheme.typography.titleMedium),
        )
    }
}

/**
 * Outlined rather than filled with a "success" colour: this palette has no green container role
 * (see `core/ui/theme/Color.kt` — the scheme is a single warm ember family), and inventing one
 * here would put an unaudited pair on screen. The border plus the check reads as confirmation
 * without borrowing a container that means something else elsewhere in the app.
 */
@Composable
private fun LoggedBanner(logged: LoggedBatchUi, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(TAG_LOGGED),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.batch_entry_logged_title, logged.kgLabel),
                    style = figureStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (logged.decremented.isEmpty()) {
                        stringResource(
                            Res.string.batch_entry_logged_note_no_decrement,
                            logged.productName,
                            logged.timeLabel,
                        )
                    } else {
                        stringResource(
                            Res.string.batch_entry_logged_note,
                            logged.productName,
                            logged.timeLabel,
                            logged.decremented.joinToString(" · ") { "${it.name.lowercase()} ${it.label}" },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.batch_entry_logged_dismiss_cd),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * The day's ledger. Four aligned columns — time, product, sync state, weight — instead of the one
 * concatenated string this used to render: a baker checking whether a mix was already logged scans
 * the right-hand column, and that only works if the weights line up.
 */
@Composable
private fun TodaySection(rows: List<BatchRowUi>, totalGrams: Long) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(Res.string.batch_entry_today_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (rows.isNotEmpty()) {
                Text(
                    stringResource(
                        Res.string.batch_entry_today_total,
                        "${formatKg(totalGrams)} ${stringResource(Res.string.batch_entry_kg_suffix)}",
                    ),
                    style = figureStyle(MaterialTheme.typography.labelMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        if (rows.isEmpty()) {
            Text(
                stringResource(Res.string.batch_entry_today_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@Column
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mono, so 05:12 and 11:07 occupy the same width and the column reads as a column.
                Text(
                    row.timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = monoFontFamily(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    row.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when (row.syncState) {
                    BatchSyncState.PENDING -> StatusPill(
                        label = stringResource(Res.string.batch_entry_sync_pending),
                        // Queued is the normal state of an offline-first app, not a warning; the
                        // amber tone is kept for things that need a human (rejected, attention).
                        tone = StatusTone.Neutral,
                    )
                    BatchSyncState.SYNCED -> StatusPill(
                        label = stringResource(Res.string.batch_entry_sync_synced),
                        tone = StatusTone.Success,
                    )
                    BatchSyncState.REJECTED -> StatusPill(
                        label = stringResource(Res.string.batch_entry_sync_rejected),
                        tone = StatusTone.Danger,
                    )
                }
                Text(
                    "${formatKg(row.mixWeightGrams)} ${stringResource(Res.string.batch_entry_kg_suffix)}",
                    style = figureStyle(MaterialTheme.typography.bodyMedium),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(88.dp),
                )
            }
            HorizontalDivider()
        }
    }
}

/** Keypad input is stored dot-separated for parsing; Chile reads it with a comma. */
private fun String.toDisplay(): String = ifBlank { "0" }.replace(DECIMAL_KEY, ',')

@AdaptivePreview
@LocalizedPreview
@Composable
private fun BatchEntryScreenPreview() {
    EcopanTheme {
        BatchEntryScreen(
            state = BatchEntryUiState(
                loading = false,
                syncIndicator = SyncIndicatorState.OfflinePending(3),
                tenantName = "Panadería El Sol",
            ),
            onAction = {},
            onOpenStockGlance = {},
        )
    }
}

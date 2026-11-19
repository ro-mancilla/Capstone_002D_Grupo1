@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.f776.ecopan.feature.pos.screens.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import com.f776.core.designsystem.component.EcopanAccentButton
import com.f776.core.designsystem.component.EcopanDialog
import com.f776.core.designsystem.component.EcopanFilterChip
import com.f776.core.designsystem.component.EcopanOutlinedButton
import com.f776.core.designsystem.component.EcopanSegmentedSelector
import com.f776.core.designsystem.component.EcopanTextField
import com.f776.core.designsystem.component.SummaryTile
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanSpacing
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.core.ui.appstate.LocalSnackbarAppState
import com.f776.core.designsystem.component.EcopanButton
import com.f776.core.designsystem.component.EcopanButtonSize
import com.f776.core.designsystem.component.EcopanButtonVariant
import com.f776.core.designsystem.component.HintMessage
import com.f776.core.ui.components.ObserveAsEvents
import com.f776.core.ui.components.ProductImage
import com.f776.core.designsystem.component.SimulatedChip
import com.f776.core.designsystem.component.StatusPill
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.core.designsystem.component.SyncStatusIndicator
import com.f776.core.ui.components.VerticalScrollBar
import com.f776.core.ui.preview.AdaptivePreview
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.figureStyle
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.domain.hardware.ReceiptLayout
import com.f776.ecopan.feature.pos.screens.pos.components.BarcodeCapture
import com.f776.ecopan.feature.pos.screens.pos.components.BatchReadyStrip
import com.f776.ecopan.feature.pos.screens.pos.components.ReceiptDialog
import com.f776.ecopan.feature.pos.screens.pos.components.WeighPanel
import ecopan.jvm.feature.pos.impl.generated.resources.Res
import ecopan.jvm.feature.pos.impl.generated.resources.pos_category_all
import ecopan.jvm.feature.pos.impl.generated.resources.pos_stock_low
import ecopan.jvm.feature.pos.impl.generated.resources.pos_stock_negative
import ecopan.jvm.feature.pos.impl.generated.resources.pos_stock_out
import ecopan.jvm.feature.pos.impl.generated.resources.pos_stock_stale_cd
import ecopan.jvm.feature.pos.impl.generated.resources.pos_stock_units_cd
import ecopan.jvm.feature.pos.impl.generated.resources.pos_charge_button
import ecopan.jvm.feature.pos.impl.generated.resources.pos_current_sale
import ecopan.jvm.feature.pos.impl.generated.resources.pos_discard_sale
import ecopan.jvm.feature.pos.impl.generated.resources.pos_empty_cart_hint
import ecopan.jvm.feature.pos.impl.generated.resources.pos_empty_cart_title
import ecopan.jvm.feature.pos.impl.generated.resources.pos_line_count
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_qty_decrease_cd
import ecopan.jvm.feature.pos.impl.generated.resources.pos_qty_increase_cd
import ecopan.jvm.feature.pos.impl.generated.resources.pos_search_clear_cd
import ecopan.jvm.feature.pos.impl.generated.resources.pos_search_empty
import ecopan.jvm.feature.pos.impl.generated.resources.pos_search_placeholder
import ecopan.jvm.feature.pos.impl.generated.resources.pos_till_summary_sales
import ecopan.jvm.feature.pos.impl.generated.resources.pos_till_summary_total
import ecopan.jvm.feature.pos.impl.generated.resources.pos_unit_each
import ecopan.jvm.feature.pos.impl.generated.resources.pos_unit_per_kg
import ecopan.jvm.feature.pos.impl.generated.resources.pos_no_active_branch_message
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_authorizing
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_authorizing_hint
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_approved
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_card_ready_subtitle
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_card_ready_title
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_cancel
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_cash_received_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_change_caption
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_confirm
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_decline_demo
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_declined
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_card
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_cash
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_other
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_method_transfer
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_other_hint
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_other_title
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_retry
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_send_to_terminal
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_short_caption
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_title
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_total_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_transfer_hint
import ecopan.jvm.feature.pos.impl.generated.resources.pos_payment_transfer_title
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_number
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_body
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_cancel
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_invalid
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_offline
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_password
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_pending
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_submit
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_title
import ecopan.jvm.feature.pos.impl.generated.resources.pos_reconnect_username
import ecopan.jvm.feature.pos.impl.generated.resources.pos_scale_status_connected
import ecopan.jvm.feature.pos.impl.generated.resources.pos_scale_status_disconnected
import ecopan.jvm.feature.pos.impl.generated.resources.pos_scale_status_manual
import ecopan.jvm.feature.pos.impl.generated.resources.pos_scale_status_simulated
import ecopan.jvm.feature.pos.impl.generated.resources.pos_switch_to_manual
import ecopan.jvm.feature.pos.impl.generated.resources.pos_till_close
import ecopan.jvm.feature.pos.impl.generated.resources.pos_till_empty
import ecopan.jvm.feature.pos.impl.generated.resources.pos_till_title
import ecopan.jvm.feature.pos.impl.generated.resources.pos_total_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_unknown_barcode_message
import ecopan.jvm.feature.pos.impl.generated.resources.pos_void_line_cd
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun PosScreenRoot(
    modifier: Modifier = Modifier,
    posViewModel: PosViewModel = koinViewModel(),
) {
    val state by posViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarAppState = LocalSnackbarAppState.current
    val noActiveBranchMessage = stringResource(Res.string.pos_no_active_branch_message)
    val unknownBarcodeMessage = stringResource(Res.string.pos_unknown_barcode_message)
    val scope = rememberCoroutineScope()

    ObserveAsEvents(posViewModel.eventChannel) {
        when (it) {
            PosEvent.NoActiveBranchOrSession -> scope.launch { snackbarAppState.showSnackbar(noActiveBranchMessage) }
            PosEvent.UnknownBarcodeScanned -> scope.launch { snackbarAppState.showSnackbar(unknownBarcodeMessage) }
        }
    }

    PosScreen(
        modifier = modifier,
        state = state,
        onAction = posViewModel::onAction,
    )
}

@Composable
private fun PosScreen(
    modifier: Modifier = Modifier,
    state: PosUiState,
    onAction: (PosAction) -> Unit,
) {
    // Invisible HID barcode-scanner sink (05 §3 item 4) -- yields focus priority to the manual-
    // weight and cash-tendered fields whenever either is open, so a scanner burst never leaks into
    // a field the cashier is actively typing into (05 §6 edge case table).
    BarcodeCapture(
        blockingFocus = state.weighPanel?.isManualEntry == true || state.paymentDialog?.method == PaymentMethod.CASH,
        onBarcodeScanned = { onAction(PosAction.OnBarcodeScanned(it)) },
    )

    Scaffold(modifier = modifier) { innerPadding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Row(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier.weight(1.8f).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // FlowRow: the status cluster drops to its own line rather than squeezing the search
                // field down to an unreadable stub on a narrow till window.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    // Fixed width, deliberately not weight(1f): the sync pill beside it relabels
                    // itself as the engine moves through syncing/pending/attention, and a weighted
                    // field would resize on every one of those ticks — the search box visibly
                    // breathing while the cashier types.
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { onAction(PosAction.OnSearchQueryChanged(it)) },
                        modifier = Modifier.width(380.dp).testTag("pos_search"),
                        placeholder = {
                            Text(
                                stringResource(Res.string.pos_search_placeholder),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onAction(PosAction.OnSearchQueryChanged("")) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.pos_search_clear_cd))
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )

                    ScaleStatusRow(scaleMode = state.scaleMode, onSwitchToManualScale = { onAction(PosAction.OnSwitchToManualScale) })
                    SyncStatusIndicator(state = state.syncIndicator, onClick = { onAction(PosAction.OnSyncIndicatorTapped) })
                    OutlinedButton(onClick = { onAction(PosAction.OnOpenTill) }) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.pos_till_title))
                    }
                }

                BatchReadyStrip(
                    notices = state.batchNotices,
                    onDismiss = { onAction(PosAction.OnDismissBatchNotice(it)) },
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = category.name == state.selectedCategory,
                            onClick = { onAction(PosAction.OnCategorySelected(category.name)) },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (category.name == ALL_CATEGORY) stringResource(Res.string.pos_category_all) else category.name)
                                    Text(
                                        category.count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalContentColor.current.copy(alpha = 0.62f),
                                    )
                                }
                            },
                        )
                    }
                }

                if (state.products.isEmpty() && state.searchQuery.isNotBlank()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        HintMessage(
                            icon = Icons.Default.Search,
                            title = stringResource(Res.string.pos_search_empty, state.searchQuery.trim()),
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 186.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.products, key = { it.id.toString() }) { product ->
                            ProductCard(product = product, onTap = { onAction(PosAction.OnProductTapped(product.id)) })
                        }
                    }
                }
            }

            CartPanel(
                modifier = Modifier.weight(1f).fillMaxSize(),
                state = state,
                onAction = onAction,
            )
        }

        state.paymentDialog?.let { dialog ->
            PaymentDialog(dialog = dialog, onAction = onAction)
        }
        state.receipt?.let { receipt ->
            ReceiptDialog(
                receipt = receipt,
                onDismiss = { onAction(PosAction.OnDismissReceipt) },
                onReprint = { onAction(PosAction.OnReprintReceipt) },
            )
        }
        if (state.tillOpen) {
            TillDialog(sales = state.tillSales, onClose = { onAction(PosAction.OnCloseTill) })
        }
        state.reconnect?.let { reconnect ->
            ReconnectDialog(
                state = reconnect,
                pendingCount = (state.syncIndicator as? SyncIndicatorState.Attention)?.count ?: 0,
                onAction = onAction,
            )
        }
    }
}


/**
 * The way back from a dead device session.
 *
 * Deliberately dismissible, and deliberately not a gate: the till keeps selling offline while this
 * is on screen, because a session that has expired says nothing about whether there is a customer
 * at the counter. The pending count is shown because it is the part a cashier can act on -- it is
 * the number of sales that will reach the bakery the moment this succeeds.
 */
@Composable
private fun ReconnectDialog(state: ReconnectUi, pendingCount: Int, onAction: (PosAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(PosAction.OnReconnectDismissed) },
        icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null) },
        title = { Text(stringResource(Res.string.pos_reconnect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.pos_reconnect_body), style = MaterialTheme.typography.bodyMedium)
                if (pendingCount > 0) {
                    Text(
                        stringResource(Res.string.pos_reconnect_pending, pendingCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { onAction(PosAction.OnReconnectUsernameChanged(it)) },
                    label = { Text(stringResource(Res.string.pos_reconnect_username)) },
                    singleLine = true,
                    enabled = !state.submitting,
                    isError = state.invalidCredentials,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onAction(PosAction.OnReconnectPasswordChanged(it)) },
                    label = { Text(stringResource(Res.string.pos_reconnect_password)) },
                    singleLine = true,
                    enabled = !state.submitting,
                    isError = state.invalidCredentials,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.invalidCredentials) {
                    Text(
                        stringResource(Res.string.pos_reconnect_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.needsConnection) {
                    Text(
                        stringResource(Res.string.pos_reconnect_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(PosAction.OnReconnectSubmitted) },
                enabled = !state.submitting && state.username.isNotBlank() && state.password.isNotBlank(),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                } else {
                    Text(stringResource(Res.string.pos_reconnect_submit))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(PosAction.OnReconnectDismissed) }) {
                Text(stringResource(Res.string.pos_reconnect_cancel))
            }
        },
    )
}

@Composable
private fun ScaleStatusRow(scaleMode: ScaleMode, onSwitchToManualScale: () -> Unit) {
    // The glyph used to be concatenated onto the label ("\u2696 Conectada"), which put an icon inside
    // a string, drew it in the body face, and read it out as "scales". StatusPill already takes an
    // ImageVector; this passes one.
    if (scaleMode == ScaleMode.SIMULATED) {
        SimulatedChip(label = stringResource(Res.string.pos_scale_status_simulated))
        return
    }
    val (icon, label, tone) = when (scaleMode) {
        ScaleMode.CONNECTED -> Triple(
            Icons.Filled.Scale,
            stringResource(Res.string.pos_scale_status_connected),
            StatusTone.Success,
        )

        ScaleMode.MANUAL -> Triple(
            Icons.Filled.Scale,
            stringResource(Res.string.pos_scale_status_manual),
            StatusTone.Info,
        )

        ScaleMode.SIMULATED -> error("handled above")

        ScaleMode.DISCONNECTED -> Triple(
            Icons.Filled.WarningAmber,
            stringResource(Res.string.pos_scale_status_disconnected),
            StatusTone.Danger,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(label = label, tone = tone, icon = icon)
        if (scaleMode == ScaleMode.DISCONNECTED) {
            SuggestionChip(onClick = onSwitchToManualScale, label = { Text(stringResource(Res.string.pos_switch_to_manual)) })
        }
    }
}

@Composable
private fun ProductCard(product: PosProductUi, onTap: () -> Unit) {
    val priceMinor = when (product.soldBy) {
        SoldBy.WEIGHT -> product.pricePerKgMinor ?: 0
        SoldBy.UNIT -> product.priceMinor ?: 0
    }
    val unitLabel = when (product.soldBy) {
        SoldBy.WEIGHT -> stringResource(Res.string.pos_unit_per_kg)
        SoldBy.UNIT -> stringResource(Res.string.pos_unit_each)
    }
    // A fixed height, not a minimum: with `heightIn` a two-line name ("Empanada de Pino") grew
    // its own tile and left one row of the grid taller than the others. The name is already
    // capped at two lines, so nothing is lost by giving every tile the height of the tallest.
    OutlinedCard(modifier = Modifier.fillMaxWidth().height(POS_TILE_HEIGHT), onClick = onTap) {
        Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // A photo is a faster target than a name for a cashier who already knows the counter,
            // and it degrades to a glyph on the same footprint when a tenant has not supplied one
            // -- so the tile grid never reflows depending on how complete the catalog is.
            ProductImage(
                imageUrl = product.imageUrl,
                modifier = Modifier.size(POS_TILE_IMAGE),
            )
            Column(Modifier.fillMaxHeight().weight(1f)) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            product.stockBadge?.let { StockBadge(it) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(formatMinor(priceMinor), style = figureStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.SemiBold)
                Text(
                    unitLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            }
        }
    }
}

/**
 * Low / out / negative warning on an Almacén tile. A filled pill is current as of the last sync; when
 * the till is offline ([StockBadgeUi.stale]) the same level is drawn outlined and its semantics say
 * so, rather than hiding a number the cashier can still use.
 */
@Composable
private fun StockBadge(badge: StockBadgeUi) {
    val label = stringResource(
        when (badge.level) {
            StockLevel.LOW -> Res.string.pos_stock_low
            StockLevel.OUT -> Res.string.pos_stock_out
            StockLevel.NEGATIVE -> Res.string.pos_stock_negative
        },
    )
    val description = stringResource(Res.string.pos_stock_units_cd, label, badge.stockUnits).let {
        if (badge.stale) stringResource(Res.string.pos_stock_stale_cd, it) else it
    }
    val filled = when (badge.level) {
        StockLevel.LOW -> MaterialTheme.colorScheme.tertiaryContainer
        StockLevel.OUT, StockLevel.NEGATIVE -> MaterialTheme.colorScheme.errorContainer
    }
    val outline = when (badge.level) {
        StockLevel.LOW -> MaterialTheme.colorScheme.tertiary
        StockLevel.OUT, StockLevel.NEGATIVE -> MaterialTheme.colorScheme.error
    }
    Surface(
        color = if (badge.stale) Color.Transparent else filled,
        border = if (badge.stale) BorderStroke(1.dp, outline) else null,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// Tall enough for the tallest tile: a two-line name, the stock badge and the price. At 116.dp the
// badge pushed the price out of the tile.
private val POS_TILE_HEIGHT = 148.dp

/** Square, and matched to the tile height less its padding, so the picture fills the tile's left
 * edge instead of floating in it. */
private val POS_TILE_IMAGE = 88.dp

@Composable
private fun CartPanel(
    modifier: Modifier = Modifier,
    state: PosUiState,
    onAction: (PosAction) -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 1.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // The header and the total / charge footer are fixed; the weigh panel and the line list
        // share what is left. Left alone the panel took what it wanted and the list got ~1.5 rows
        // (its empty state clipped), so the panel is capped to leave CART_MIN_VISIBLE_ROWS rows and
        // scrolls inside that cap instead.
        val weighPanelMaxHeight = (maxHeight - CART_CHROME_HEIGHT - CART_ROW_HEIGHT * CART_MIN_VISIBLE_ROWS)
            .coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(Res.string.pos_current_sale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    pluralStringResource(Res.plurals.pos_line_count, state.cart.lines.size, state.cart.lines.size),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            HorizontalDivider()

            // The weigh panel belongs beside the running sale, not under the catalog: the cashier is
            // reading the live weight and the cart total together before committing the line.
            state.weighPanel?.let { panel ->
                Column(Modifier.heightIn(max = weighPanelMaxHeight).verticalScroll(rememberScrollState())) {
                WeighPanel(
                    panel = panel,
                    scaleMode = state.scaleMode,
                    onManualWeightChanged = { onAction(PosAction.OnManualWeightChanged(it)) },
                    onAdd = { onAction(PosAction.OnAddWeighedLine(it)) },
                    onClose = { onAction(PosAction.OnCloseWeighPanel) },
                )
                }
            }

            if (state.cart.lines.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                    HintMessage(
                        icon = Icons.Default.ShoppingCart,
                        title = stringResource(Res.string.pos_empty_cart_title),
                        subtitle = stringResource(Res.string.pos_empty_cart_hint),
                    )
                }
            } else {
                // Box + explicit list state so the desktop till gets a scrollbar (a mouse user has
                // no other scroll-position affordance on a long receipt).
                val cartListState = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), state = cartListState) {
                        items(state.cart.lines, key = { it.lineId.toString() }) { line ->
                            CartLineRow(
                                line = line,
                                onStep = { delta -> onAction(PosAction.OnStepLineQuantity(line.lineId, delta)) },
                                onVoid = { onAction(PosAction.OnVoidLine(line.lineId)) },
                            )
                            HorizontalDivider()
                        }
                    }
                    VerticalScrollBar(scrollState = cartListState)
                }
            }

            HorizontalDivider(thickness = 2.dp)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Res.string.pos_total_label), style = MaterialTheme.typography.labelMedium)
                    Text(formatMinor(state.cart.totalMinor), style = EcopanTheme.typography.numeric, color = EcopanTheme.colors.price)
                }
                EcopanButton(
                    text = stringResource(Res.string.pos_charge_button, formatMinor(state.cart.totalMinor)),
                    onClick = { onAction(PosAction.OnCharge) },
                    variant = EcopanButtonVariant.Accent,
                    size = EcopanButtonSize.Touch,
                    enabled = state.cart.lines.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.cart.lines.isNotEmpty()) {
                    TextButton(
                        onClick = { onAction(PosAction.OnDiscardSale) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.pos_discard_sale), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        }
    }
}

/** One cart row: 10dp padding either side of a 48dp icon button. */
private val CART_ROW_HEIGHT = 68.dp

/** The line list keeps at least this many rows visible however tall the weigh panel is. */
private const val CART_MIN_VISIBLE_ROWS = 3

/** Header, dividers, total and the charge / discard buttons: everything in the cart that is not the panel or the list. */
private val CART_CHROME_HEIGHT = 250.dp

@Composable
private fun CartLineRow(line: PosCartLineUi, onStep: (Int) -> Unit, onVoid: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(line.metaLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Only unit lines step; a weighed line's quantity is the weight itself, re-weighed not nudged.
        if (line.soldBy == SoldBy.UNIT && line.quantity != null) {
            QuantityStepper(quantity = line.quantity, onStep = onStep)
        }
        Text(formatMinor(line.totalMinor), fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onVoid) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.pos_void_line_cd)) }
    }
}

@Composable
private fun QuantityStepper(quantity: Int, onStep: (Int) -> Unit) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStep(-1) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(Res.string.pos_qty_decrease_cd),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                quantity.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 20.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onStep(1) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(Res.string.pos_qty_increase_cd),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PaymentDialog(dialog: PaymentDialogUi, onAction: (PosAction) -> Unit) {
    val c = EcopanTheme.colors
    val authorizing = dialog.cardPhase == CardAuthPhase.AUTHORIZING
    val methodLabels = PaymentMethod.entries.associateWith { it.label() }
    EcopanDialog(
        onDismissRequest = { if (!authorizing) onAction(PosAction.OnCancelPayment) },
        title = stringResource(Res.string.pos_payment_title),
        // One footer for every method: cancel at the start of the action row, the single step that
        // moves the payment forward at the end. The content above only ever shows state.
        dismissButton = {
            if (!authorizing) {
                EcopanOutlinedButton(
                    text = stringResource(Res.string.pos_payment_cancel),
                    onClick = { onAction(PosAction.OnCancelPayment) },
                )
            }
        },
        confirmButton = { PaymentConfirmButton(dialog, onAction) },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space4)) {
            Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space1)) {
                Text(stringResource(Res.string.pos_payment_total_label), style = EcopanTheme.typography.eyebrow, color = c.inkMuted)
                Text(formatMinor(dialog.totalMinor), style = EcopanTheme.typography.numeric, color = c.price)
            }

            Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space2)) {
                Text(stringResource(Res.string.pos_payment_method_label), style = EcopanTheme.typography.eyebrow, color = c.inkMuted)
                // Mutually exclusive, so a segmented selector (radio semantics), not four checkboxes.
                EcopanSegmentedSelector<PaymentMethod?>(
                    options = PaymentMethod.entries,
                    selected = dialog.method,
                    onSelect = { method -> method?.let { onAction(PosAction.OnPaymentMethodSelected(it)) } },
                    label = { method -> method?.let(methodLabels::get).orEmpty() },
                    icon = { method -> method?.icon() },
                    enabled = !authorizing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (dialog.method) {
                PaymentMethod.CASH -> CashPaymentContent(dialog, onAction)
                PaymentMethod.CARD -> CardPaymentContent(dialog)
                PaymentMethod.TRANSFER -> OtherPaymentContent(isTransfer = true)
                PaymentMethod.OTHER -> OtherPaymentContent(isTransfer = false)
                null -> Unit
            }
        }
    }
}

/** The one forward action of the payment dialog, always in the same slot whatever the method. */
@Composable
private fun PaymentConfirmButton(dialog: PaymentDialogUi, onAction: (PosAction) -> Unit) {
    when (dialog.method) {
        PaymentMethod.CASH -> {
            val tendered = dialog.cashTenderedText.toLongOrNull() ?: 0
            EcopanAccentButton(
                text = stringResource(Res.string.pos_payment_confirm),
                onClick = { onAction(PosAction.OnConfirmCashPayment) },
                enabled = tendered >= dialog.totalMinor && tendered > 0,
            )
        }
        PaymentMethod.CARD -> when (dialog.cardPhase) {
            CardAuthPhase.IDLE -> EcopanAccentButton(
                text = stringResource(Res.string.pos_payment_send_to_terminal),
                onClick = { onAction(PosAction.OnStartCardAuth) },
                leadingIcon = EcopanIcons.CreditCard,
            )
            CardAuthPhase.AUTHORIZING -> EcopanOutlinedButton(
                text = stringResource(Res.string.pos_payment_decline_demo),
                onClick = { onAction(PosAction.OnDeclineCardAuthDemo) },
            )
            CardAuthPhase.DECLINED -> EcopanAccentButton(
                text = stringResource(Res.string.pos_payment_retry),
                onClick = { onAction(PosAction.OnStartCardAuth) },
                leadingIcon = EcopanIcons.Refresh,
            )
            CardAuthPhase.APPROVED -> Unit
        }
        PaymentMethod.TRANSFER, PaymentMethod.OTHER -> EcopanAccentButton(
            text = stringResource(Res.string.pos_payment_confirm),
            onClick = { onAction(PosAction.OnConfirmOtherPayment) },
        )
        null -> Unit
    }
}

/**
 * Quick-tender amounts: the exact total first (the common "gave me the exact change" case), then the
 * next round note up from it. Beats a fixed 12/15/20k list that is useless on a $900 sale.
 */
internal fun quickCashOptions(totalMinor: Long): List<Long> {
    if (totalMinor <= 0) return emptyList()
    val roundUps = listOf(1_000L, 5_000L, 10_000L, 20_000L)
        .map { step -> ((totalMinor + step - 1) / step) * step }
        .filter { it > totalMinor }
    return (listOf(totalMinor) + roundUps).distinct().take(4)
}

@Composable
private fun CashPaymentContent(dialog: PaymentDialogUi, onAction: (PosAction) -> Unit) {
    val c = EcopanTheme.colors
    val tendered = dialog.cashTenderedText.toLongOrNull() ?: 0
    val change = tendered - dialog.totalMinor
    val short = change < 0

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space3)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.chipGap),
            verticalArrangement = Arrangement.spacedBy(EcopanSpacing.chipGap),
        ) {
            quickCashOptions(dialog.totalMinor).forEach { amount ->
                EcopanFilterChip(
                    selected = tendered == amount,
                    onClick = { onAction(PosAction.OnQuickCashChip(amount)) },
                    label = formatMinor(amount),
                )
            }
        }
        EcopanTextField(
            value = dialog.cashTenderedText,
            onValueChange = { onAction(PosAction.OnCashTenderedChanged(it)) },
            label = stringResource(Res.string.pos_payment_cash_received_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space1)) {
            Text(
                stringResource(if (short) Res.string.pos_payment_short_caption else Res.string.pos_payment_change_caption),
                style = EcopanTheme.typography.eyebrow,
                color = c.inkMuted,
            )
            Text(
                if (dialog.cashTenderedText.isEmpty()) "—" else formatMinor(kotlin.math.abs(change)),
                style = EcopanTheme.typography.headline,
                color = when {
                    dialog.cashTenderedText.isEmpty() -> c.inkMuted
                    short -> c.danger.fg
                    else -> c.success.fg
                },
            )
        }
    }
}

/** Card states are status only (the actions live in the shared footer), left-aligned like cash. */
@Composable
private fun CardPaymentContent(dialog: PaymentDialogUi) {
    val c = EcopanTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space2)) {
        when (dialog.cardPhase) {
            CardAuthPhase.IDLE -> {
                Text(stringResource(Res.string.pos_payment_card_ready_title), style = EcopanTheme.typography.eyebrow, color = c.inkMuted)
                Text(
                    stringResource(Res.string.pos_payment_card_ready_subtitle, formatMinor(dialog.totalMinor)),
                    style = EcopanTheme.typography.subtitle,
                    color = c.inkStrong,
                )
            }
            CardAuthPhase.AUTHORIZING -> {
                CircularProgressIndicator(color = c.crust)
                Text(stringResource(Res.string.pos_payment_authorizing), style = EcopanTheme.typography.subtitle, color = c.inkStrong)
                Text(stringResource(Res.string.pos_payment_authorizing_hint), style = EcopanTheme.typography.bodySmall, color = c.inkMuted)
            }
            CardAuthPhase.APPROVED -> StatusPill(stringResource(Res.string.pos_payment_approved), StatusTone.Success)
            CardAuthPhase.DECLINED -> {
                StatusPill(stringResource(Res.string.pos_payment_declined), StatusTone.Danger)
                dialog.declineReason?.let { Text(it, style = EcopanTheme.typography.bodySmall, color = c.inkMuted) }
            }
        }
    }
}

@Composable
private fun OtherPaymentContent(isTransfer: Boolean) {
    val c = EcopanTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space2)) {
        Text(
            stringResource(if (isTransfer) Res.string.pos_payment_transfer_title else Res.string.pos_payment_other_title),
            style = EcopanTheme.typography.subtitle,
            color = c.inkStrong,
        )
        Text(
            stringResource(if (isTransfer) Res.string.pos_payment_transfer_hint else Res.string.pos_payment_other_hint),
            style = EcopanTheme.typography.bodySmall,
            color = c.inkMuted,
        )
    }
}

@Composable
private fun TillDialog(sales: List<TillSaleUi>, onClose: () -> Unit) {
    val c = EcopanTheme.colors
    EcopanDialog(
        onDismissRequest = onClose,
        title = stringResource(Res.string.pos_till_title),
        icon = EcopanIcons.CashDrawer,
        confirmButton = { EcopanButton(text = stringResource(Res.string.pos_till_close), onClick = onClose) },
        modifier = Modifier.width(640.dp),
    ) {
        if (sales.isEmpty()) {
            HintMessage(
                icon = EcopanIcons.Receipt,
                title = stringResource(Res.string.pos_till_empty),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val summary = sales.toSummary()
            Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space3)) {
                // Closing the till is the reason this dialog exists: lead with the totals a cashier
                // reconciles against the drawer. One column per payment method, so they sum to Total.
                Text(
                    stringResource(Res.string.pos_till_summary_sales) + " · " + summary.salesCount,
                    style = EcopanTheme.typography.eyebrow,
                    color = c.inkMuted,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space2)) {
                    PaymentMethod.entries.forEach { method ->
                        SummaryTile(
                            label = method.label(),
                            value = formatMinor(summary.byMethodMinor.getValue(method)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SummaryTile(
                        label = stringResource(Res.string.pos_till_summary_total),
                        value = formatMinor(summary.totalMinor),
                        modifier = Modifier.weight(1f),
                        emphasized = true,
                    )
                }

                // A scrolling Column, not a LazyColumn: a LazyColumn expands to the height it is
                // offered, which parked a two-sale day in a tall empty well. heightIn caps a long day.
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    sales.forEach { sale ->
                        // Fixed-width columns, so pills and totals line up row to row.
                        Row(
                            Modifier.fillMaxWidth().defaultMinSize(minHeight = EcopanSpacing.rowMin),
                            horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sale.timeLabel, style = EcopanTheme.typography.figure, color = c.inkStrong, modifier = Modifier.width(64.dp))
                            Text(
                                stringResource(Res.string.pos_receipt_number, ReceiptLayout.receiptNumberLabel(sale.receiptNumber).substringAfterLast(' ')),
                                style = EcopanTheme.typography.bodySmall,
                                color = c.inkMuted,
                                modifier = Modifier.weight(1f),
                            )
                            Box(Modifier.width(168.dp), contentAlignment = Alignment.CenterStart) {
                                StatusPill(
                                    label = sale.paymentMethod.label(),
                                    tone = StatusTone.Neutral,
                                    icon = sale.paymentMethod.icon(),
                                )
                            }
                            Text(
                                formatMinor(sale.totalMinor),
                                style = EcopanTheme.typography.figure,
                                color = c.inkStrong,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(96.dp),
                            )
                        }
                        HorizontalDivider(color = c.hairline)
                    }
                }
            }
        }
    }
}

@AdaptivePreview
@LocalizedPreview
@Composable
private fun PosScreenPreview() {
    EcopanTheme {
        Surface {
            PosScreen(state = PosUiState(loading = false), onAction = {})
        }
    }
}

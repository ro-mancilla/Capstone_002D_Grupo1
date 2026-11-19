package com.f776.ecopan.feature.pos.screens.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.component.EcopanAlert
import com.f776.core.designsystem.component.EcopanButton
import com.f776.core.designsystem.component.EcopanButtonSize
import com.f776.core.designsystem.component.EcopanModal
import com.f776.core.designsystem.component.ReceiptColumns
import com.f776.core.designsystem.component.ReceiptPaper
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.feature.pos.screens.pos.DrawerNote
import com.f776.ecopan.feature.pos.screens.pos.PaymentNote
import com.f776.ecopan.feature.pos.screens.pos.PosCartLineUi
import com.f776.ecopan.feature.pos.screens.pos.ReceiptUi
import com.f776.ecopan.feature.pos.screens.pos.formatMinor
import com.f776.ecopan.feature.pos.screens.pos.label
import ecopan.jvm.feature.pos.impl.generated.resources.Res
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_cash_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_change_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_done
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_card_approved
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_card_simulated
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_drawer_opened
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_other_recorded
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_transfer_recorded
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_drawer_simulated
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_method_label
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_preview_caption
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_print_failed
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_print_failed_hint
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_registered
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_reprint
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_title
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_total_label
import org.jetbrains.compose.resources.stringResource

/** The modal is a fixed-proportion 900x680 sheet (spec 4e); on a smaller window it shrinks, it never overflows. */
private val ReceiptModalWidth = 900.dp
private val ReceiptModalHeight = 680.dp

/** Space the print preview keeps around the paper, so the pane is `paper + 80dp` (380dp for 58 mm). */
private val PreviewPanePadding = 40.dp

/**
 * The boleta after a sale (spec 4e/4f). Left: what was sold, the totals and "Nueva venta", all of
 * which stay in place; right: the print preview, which is the only part that scrolls when the
 * receipt is long. The sale is already recorded when this opens, so a failed print is a message with
 * "Reimprimir", never a reason to hold the sale back.
 */
@Composable
internal fun ReceiptDialog(
    receipt: ReceiptUi,
    onDismiss: () -> Unit,
    onReprint: () -> Unit,
) {
    EcopanModal(onDismissRequest = onDismiss, modifier = ReceiptModalSize) {
        ReceiptSheet(receipt, onDismiss, onReprint)
    }
}

/** The modal's size: 900x680 at most, and proportionally smaller on a smaller window. */
private val ReceiptModalSize: Modifier
    get() = Modifier
        .widthIn(max = ReceiptModalWidth)
        .fillMaxWidth(0.94f)
        .heightIn(max = ReceiptModalHeight)
        .fillMaxHeight(0.92f)

/** The two panes; split from the dialog so they can be composed (and checked) without a window. */
@Composable
internal fun ReceiptSheet(receipt: ReceiptUi, onDone: () -> Unit, onReprint: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        SaleSummary(receipt, onDone, onReprint, Modifier.weight(1f))
        PrintPreview(receipt)
    }
}

@Composable
private fun SaleSummary(receipt: ReceiptUi, onDone: () -> Unit, onReprint: () -> Unit, modifier: Modifier) {
    val colors = EcopanTheme.colors
    Column(
        modifier = modifier.fillMaxHeight().padding(EcopanSpacing.space6 + EcopanSpacing.space1),
        verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space3),
    ) {
        Text(stringResource(Res.string.pos_receipt_title), style = EcopanTheme.typography.title, color = colors.inkStrong)
        EcopanAlert(
            message = stringResource(Res.string.pos_receipt_registered, receipt.timeLabel),
            tone = StatusTone.Success,
        )
        if (receipt.printFailed) {
            EcopanAlert(
                message = stringResource(Res.string.pos_receipt_print_failed),
                supporting = stringResource(Res.string.pos_receipt_print_failed_hint),
                icon = EcopanIcons.Print,
                tone = StatusTone.Warning,
                actionLabel = stringResource(Res.string.pos_receipt_reprint),
                onAction = onReprint,
            )
        }

        // The lines scroll on their own so the totals and the button below never move.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            receipt.lines.forEach { line ->
                ReceiptLineRow(line)
                HorizontalDivider(color = colors.hairline)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space2)) {
            AmountRow(stringResource(Res.string.pos_receipt_total_label), receipt.totalMinor, emphasised = true)
            if (receipt.cashTenderedMinor != null && receipt.changeMinor != null) {
                AmountRow(stringResource(Res.string.pos_receipt_cash_label), receipt.cashTenderedMinor)
                AmountRow(stringResource(Res.string.pos_receipt_change_label), receipt.changeMinor, emphasised = true)
            } else {
                TextRow(stringResource(Res.string.pos_receipt_method_label), receipt.paymentMethod.label())
            }
            when (receipt.drawer) {
                DrawerNote.None -> Unit
                DrawerNote.Opened -> EcopanAlert(
                    message = stringResource(Res.string.pos_receipt_drawer_opened),
                    icon = EcopanIcons.CashDrawer,
                    tone = StatusTone.Neutral,
                )
                DrawerNote.OpenedSimulated -> EcopanAlert(
                    message = stringResource(Res.string.pos_receipt_drawer_simulated),
                    icon = EcopanIcons.CashDrawer,
                    tone = StatusTone.Simulated,
                )
            }
            val (noteRes, noteTone) = when (receipt.paymentNote) {
                PaymentNote.None -> null to StatusTone.Neutral
                PaymentNote.CardApproved -> Res.string.pos_receipt_card_approved to StatusTone.Neutral
                PaymentNote.CardApprovedSimulated -> Res.string.pos_receipt_card_simulated to StatusTone.Simulated
                PaymentNote.TransferRecorded -> Res.string.pos_receipt_transfer_recorded to StatusTone.Neutral
                PaymentNote.OtherRecorded -> Res.string.pos_receipt_other_recorded to StatusTone.Neutral
            }
            if (noteRes != null) EcopanAlert(message = stringResource(noteRes), tone = noteTone)
            EcopanButton(
                text = stringResource(Res.string.pos_receipt_done),
                onClick = onDone,
                size = EcopanButtonSize.Touch,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReceiptLineRow(line: PosCartLineUi) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = EcopanSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space3),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, style = EcopanTheme.typography.label, color = EcopanTheme.colors.inkStrong)
            Text(line.metaLabel, style = EcopanTheme.typography.bodySmall, color = EcopanTheme.colors.inkMuted)
        }
        Text(formatMinor(line.totalMinor), style = EcopanTheme.typography.figure, color = EcopanTheme.colors.price)
    }
}

/** A money row: the amount is a price, so it takes the price colour and a tabular figure. */
@Composable
private fun AmountRow(label: String, amountMinor: Long, emphasised: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (emphasised) EcopanTheme.typography.subtitle else EcopanTheme.typography.body,
            color = if (emphasised) EcopanTheme.colors.inkStrong else EcopanTheme.colors.inkMuted,
        )
        Text(
            formatMinor(amountMinor),
            style = if (emphasised) EcopanTheme.typography.numeric else EcopanTheme.typography.figure,
            color = EcopanTheme.colors.price,
        )
    }
}

@Composable
private fun TextRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = EcopanTheme.typography.body, color = EcopanTheme.colors.inkMuted)
        Text(value, style = EcopanTheme.typography.label, color = EcopanTheme.colors.inkStrong)
    }
}

@Composable
private fun PrintPreview(receipt: ReceiptUi) {
    val columns = if (receipt.paperWidth == PaperWidth.Mm80) ReceiptColumns.Mm80 else ReceiptColumns.Mm58
    Column(
        modifier = Modifier
            .width(columns.paperWidth + PreviewPanePadding * 2)
            .fillMaxHeight()
            .background(EcopanTheme.colors.inset)
            .padding(EcopanSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space3),
    ) {
        Text(
            stringResource(Res.string.pos_receipt_preview_caption, receipt.paperWidth.millimetres),
            style = EcopanTheme.typography.label,
            color = EcopanTheme.colors.inkMuted,
        )
        // Only the paper scrolls: a 20-line receipt grows the roll, not the dialog.
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            ReceiptPaper(lines = receipt.paperLines, columns = columns, simulated = receipt.paperSimulated)
        }
    }
}

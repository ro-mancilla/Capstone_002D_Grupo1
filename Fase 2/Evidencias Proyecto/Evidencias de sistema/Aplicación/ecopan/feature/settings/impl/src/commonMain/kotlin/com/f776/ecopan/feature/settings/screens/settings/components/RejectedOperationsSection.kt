package com.f776.ecopan.feature.settings.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.f776.ecopan.domain.sync.RejectedOperation
import com.f776.ecopan.domain.sync.SyncEntityType
import ecopan.feature.settings.impl.generated.resources.Res
import ecopan.feature.settings.impl.generated.resources.rejected_attempts
import ecopan.feature.settings.impl.generated.resources.rejected_category
import ecopan.feature.settings.impl.generated.resources.rejected_discard
import ecopan.feature.settings.impl.generated.resources.rejected_discard_confirm
import ecopan.feature.settings.impl.generated.resources.rejected_discard_message
import ecopan.feature.settings.impl.generated.resources.rejected_entity_batch
import ecopan.feature.settings.impl.generated.resources.rejected_entity_other
import ecopan.feature.settings.impl.generated.resources.rejected_entity_sale
import ecopan.feature.settings.impl.generated.resources.rejected_entity_stock
import ecopan.feature.settings.impl.generated.resources.rejected_cancel
import ecopan.feature.settings.impl.generated.resources.rejected_reason
import ecopan.feature.settings.impl.generated.resources.rejected_reason_unknown
import ecopan.feature.settings.impl.generated.resources.rejected_retry
import ecopan.feature.settings.impl.generated.resources.rejected_subtitle
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * Operations the server permanently refused (HU-SYN-0006): what each one was, why the server said
 * no, and the two things a person can do about it -- send it again, or give up on it. Nothing here
 * edits the operation; correcting the cause (a branch, a product) happens where that data lives,
 * and "Reintentar" then re-sends the original.
 */
@Composable
internal fun RejectedOperationsSection(
    operations: List<RejectedOperation>,
    onRetry: (Uuid) -> Unit,
    onDiscard: (Uuid) -> Unit,
) {
    var pendingDiscard by remember { mutableStateOf<Uuid?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsCategoryHeader(stringResource(Res.string.rejected_category))
        Text(
            stringResource(Res.string.rejected_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        OutlinedCard(Modifier.fillMaxWidth()) {
            operations.forEachIndexed { index, operation ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RejectedRow(operation, onRetry = { onRetry(operation.mutationId) }, onDiscard = { pendingDiscard = operation.mutationId })
            }
        }
    }

    pendingDiscard?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text(stringResource(Res.string.rejected_discard)) },
            text = { Text(stringResource(Res.string.rejected_discard_message)) },
            confirmButton = {
                TextButton(onClick = { onDiscard(id); pendingDiscard = null }) {
                    Text(stringResource(Res.string.rejected_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) { Text(stringResource(Res.string.rejected_cancel)) }
            },
        )
    }
}

@Composable
private fun RejectedRow(operation: RejectedOperation, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(entityLabel(operation.entityType), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(Res.string.rejected_reason, operation.reason ?: stringResource(Res.string.rejected_reason_unknown)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(Res.string.rejected_attempts, operation.attempts),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.rejected_retry)) }
            TextButton(onClick = onDiscard) { Text(stringResource(Res.string.rejected_discard)) }
        }
    }
}

@Composable
private fun entityLabel(type: SyncEntityType?): String = stringResource(
    when (type) {
        SyncEntityType.SALE -> Res.string.rejected_entity_sale
        SyncEntityType.PRODUCTION_BATCH -> Res.string.rejected_entity_batch
        SyncEntityType.STOCK_MOVEMENT -> Res.string.rejected_entity_stock
        else -> Res.string.rejected_entity_other
    },
)

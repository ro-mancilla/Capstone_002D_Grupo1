package com.f776.ecopan.feature.pos.screens.pos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.f776.core.designsystem.component.EcopanToast
import com.f776.core.designsystem.component.EcopanToastTone
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.ecopan.feature.pos.screens.pos.BatchNoticeUi
import com.f776.ecopan.feature.pos.screens.pos.text
import ecopan.jvm.feature.pos.impl.generated.resources.Res
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_more_batches
import org.jetbrains.compose.resources.pluralStringResource
import kotlin.uuid.Uuid

/**
 * The till's "lote listo" strip (spec 3d): a success-toned, dismissible strip above the product
 * grid with the notice's words, a 44dp "Cerrar aviso" button and, when more notices wait behind
 * it, "+N lotes más". It stays until closed -- unlike the web toast it never times out, because the
 * cashier may be at the counter when it arrives. Rendering nothing when there are no notices is also
 * the offline path: no events, no strip, no error state.
 */
@Composable
internal fun BatchReadyStrip(
    notices: List<BatchNoticeUi>,
    onDismiss: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = notices.firstOrNull() ?: return
    val words = current.text()
    val supporting = words.supporting.map { it.text() }
    val waiting = notices.drop(1).sumOf { it.batches.size }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space1)) {
        EcopanToast(
            message = words.title.text(),
            supporting = supporting.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            icon = EcopanIcons.Bakery,
            tone = EcopanToastTone.Success,
            onDismiss = { onDismiss(current.id) },
        )
        if (waiting > 0) {
            Text(
                pluralStringResource(Res.plurals.pos_notice_more_batches, waiting, waiting),
                style = EcopanTheme.typography.bodySmall,
                color = EcopanTheme.colors.inkMuted,
            )
        }
    }
}

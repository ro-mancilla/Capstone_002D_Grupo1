package com.f776.ecopan.feature.production.screens.batchentry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f776.core.designsystem.component.EcopanCard
import com.f776.core.designsystem.component.StatusPill
import com.f776.core.designsystem.component.StatusTone
import com.f776.core.designsystem.icon.EcopanIcons
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.ui.preview.AdaptivePreview
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.ecopan.domain.production.Estimator
import com.f776.ecopan.domain.production.SuggestedQuantity
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedGroupUi
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedProductionUi
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedRowUi
import com.f776.ecopan.feature.production.screens.batchentry.model.SuggestedUnit
import com.f776.ecopan.feature.production.screens.formatDayHeading
import com.f776.ecopan.feature.production.screens.formatSuggestedKg
import ecopan.mobile.feature.production.impl.generated.resources.Res
import ecopan.mobile.feature.production.impl.generated.resources.suggested_estimator_baseline
import ecopan.mobile.feature.production.impl.generated.resources.suggested_estimator_line
import ecopan.mobile.feature.production.impl.generated.resources.suggested_estimator_model
import ecopan.mobile.feature.production.impl.generated.resources.suggested_estimator_unknown
import ecopan.mobile.feature.production.impl.generated.resources.suggested_group_default_type
import ecopan.mobile.feature.production.impl.generated.resources.suggested_group_kg
import ecopan.mobile.feature.production.impl.generated.resources.suggested_group_units
import ecopan.mobile.feature.production.impl.generated.resources.suggested_heading
import ecopan.mobile.feature.production.impl.generated.resources.suggested_kg_unit
import ecopan.mobile.feature.production.impl.generated.resources.suggested_show_all
import ecopan.mobile.feature.production.impl.generated.resources.suggested_show_less
import ecopan.mobile.feature.production.impl.generated.resources.suggested_subtitle
import ecopan.mobile.feature.production.impl.generated.resources.suggested_units_unit
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tomorrow's suggested production, read-only, above the product picker: the baker sees what the
 * day is expected to sell before deciding what to mix.
 *
 * Deliberately **not tappable** (Panadero móvil 2d): a flat surface, no chevrons, no press state on
 * the rows. Only the expander responds -- choosing a product is the chips' job below it. The card
 * fades in over 200 ms when it first appears (a sync just delivered it); with reduced motion it
 * simply appears.
 *
 * [offlineStamp] is the finished "Datos de la última sincronización, hoy 04:50" line, or null while
 * online. It is formatted by the caller because "hoy" depends on the clock.
 */
@Composable
internal fun SuggestedProductionCard(
    suggestion: SuggestedProductionUi,
    expanded: Boolean,
    offlineStamp: String?,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appeared = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appeared,
        enter = fadeIn(animationSpec = EcopanTheme.motion.base()),
        exit = ExitTransition.None,
        modifier = modifier,
    ) {
        EcopanCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = EcopanSpacing.space4, top = EcopanSpacing.space4, end = EcopanSpacing.space4, bottom = EcopanSpacing.space2),
        ) {
            Header(suggestion = suggestion, offlineStamp = offlineStamp)
            suggestion.visibleGroups(expanded).forEach { group -> SuggestedGroup(group) }
            if (suggestion.canCollapse) {
                Expander(expanded = expanded, total = suggestion.totalCount, onClick = onToggleExpanded)
            }
        }
    }
}

@Composable
private fun Header(suggestion: SuggestedProductionUi, offlineStamp: String?) {
    val c = EcopanTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space2), verticalAlignment = Alignment.CenterVertically) {
        Icon(EcopanIcons.TrendingUp, contentDescription = null, tint = c.crust, modifier = Modifier.size(EcopanSpacing.space5))
        Text(
            stringResource(Res.string.suggested_heading, formatDayHeading(suggestion.day)),
            style = EcopanTheme.typography.subtitle,
            color = c.inkStrong,
        )
    }
    Column(modifier = Modifier.padding(top = EcopanSpacing.space1), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space0_5)) {
        Text(stringResource(Res.string.suggested_subtitle), style = EcopanTheme.typography.bodySmall, color = c.inkBody)
        Text(
            stringResource(Res.string.suggested_estimator_line, estimatorLabel(suggestion.estimator)),
            style = EcopanTheme.typography.bodySmall,
            color = c.inkMuted,
        )
        if (offlineStamp != null) {
            Text(offlineStamp, style = EcopanTheme.typography.bodySmall, color = c.inkMuted)
        }
    }
}

@Composable
private fun SuggestedGroup(group: SuggestedGroupUi) {
    val type = group.typeName.ifBlank { stringResource(Res.string.suggested_group_default_type) }
    Text(
        stringResource(
            when (group.unit) {
                SuggestedUnit.KILOGRAMS -> Res.string.suggested_group_kg
                SuggestedUnit.UNITS -> Res.string.suggested_group_units
            },
            type,
        ),
        style = EcopanTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = EcopanTheme.colors.inkMuted,
        modifier = Modifier.padding(top = EcopanSpacing.space4, bottom = EcopanSpacing.space1),
    )
    group.rows.forEach { row -> SuggestedRow(row) }
}

/**
 * One product. The quantity is the anchor and is never allowed to give way: it is measured first,
 * does not wrap and keeps its width, while the name takes what is left, wraps to two lines and then
 * ellipsises -- at 1.3x text too.
 */
@Composable
private fun SuggestedRow(row: SuggestedRowUi) {
    val c = EcopanTheme.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = EcopanSpacing.rowMin)
            .padding(vertical = EcopanSpacing.space2)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EcopanSpacing.space1)) {
            Text(
                row.productName,
                style = EcopanTheme.typography.subtitle.copy(fontWeight = FontWeight.Medium),
                color = c.inkStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.showEstimator) {
                StatusPill(
                    label = estimatorLabel(row.estimator),
                    tone = if (row.estimator == Estimator.MODEL) StatusTone.Info else StatusTone.Neutral,
                )
            }
        }
        Quantity(row.quantity)
    }
}

@Composable
private fun Quantity(quantity: SuggestedQuantity) {
    val c = EcopanTheme.colors
    val (figure, unit) = when (quantity) {
        is SuggestedQuantity.Grams -> formatSuggestedKg(quantity.grams) to stringResource(Res.string.suggested_kg_unit)
        is SuggestedQuantity.Units -> quantity.units.toString() to stringResource(Res.string.suggested_units_unit)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space1), verticalAlignment = Alignment.Bottom) {
        Text(figure, style = EcopanTheme.typography.quantityLg, color = c.inkStrong, maxLines = 1, softWrap = false)
        Text(
            unit,
            style = EcopanTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = c.inkMuted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The one thing on the card that responds: "Ver los 5 productos" / "Ver menos". */
@Composable
private fun Expander(expanded: Boolean, total: Int, onClick: () -> Unit) {
    val c = EcopanTheme.colors
    val label = if (expanded) {
        stringResource(Res.string.suggested_show_less)
    } else {
        stringResource(Res.string.suggested_show_all, total)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = EcopanSpacing.touch)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(EcopanSpacing.space1, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = EcopanTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = c.crust)
        Icon(
            if (expanded) EcopanIcons.ExpandLess else EcopanIcons.ExpandMore,
            contentDescription = null,
            tint = c.crust,
            modifier = Modifier.size(EcopanSpacing.space5),
        )
    }
}

@Composable
private fun estimatorLabel(estimator: Estimator): String = stringResource(
    when (estimator) {
        Estimator.MODEL -> Res.string.suggested_estimator_model
        Estimator.BASELINE -> Res.string.suggested_estimator_baseline
        Estimator.UNKNOWN -> Res.string.suggested_estimator_unknown
    },
)

private fun previewCard(): SuggestedProductionUi {
    fun row(name: String, quantity: SuggestedQuantity, estimator: Estimator = Estimator.BASELINE) =
        SuggestedRowUi(Uuid.random(), name, quantity, estimator, showEstimator = estimator != Estimator.BASELINE)
    return SuggestedProductionUi(
        day = LocalDate(2026, 10, 8),
        groups = listOf(
            SuggestedGroupUi(
                "Pan", SuggestedUnit.KILOGRAMS,
                listOf(
                    row("Pan Amasado", SuggestedQuantity.Grams(22_700)),
                    row("Marraqueta", SuggestedQuantity.Grams(18_300)),
                    row("Pan de Hoja Integral con Semillas de Zapallo y Linaza (grande)", SuggestedQuantity.Grams(6_500), Estimator.MODEL),
                    row("Hallulla", SuggestedQuantity.Grams(3_200)),
                ),
            ),
            SuggestedGroupUi(
                "Pastelería", SuggestedUnit.UNITS,
                listOf(row("Berlín", SuggestedQuantity.Units(19)), row("Empanada de Pino", SuggestedQuantity.Units(19))),
            ),
        ),
        estimator = Estimator.BASELINE,
        updatedAt = Instant.fromEpochMilliseconds(1_785_000_000_000),
    )
}

@AdaptivePreview
@LocalizedPreview
@Composable
private fun SuggestedProductionCardFoldedPreview() {
    EcopanTheme {
        SuggestedProductionCard(previewCard(), expanded = false, offlineStamp = null, onToggleExpanded = {})
    }
}

@AdaptivePreview
@Composable
private fun SuggestedProductionCardExpandedOfflinePreview() {
    EcopanTheme {
        SuggestedProductionCard(
            previewCard(),
            expanded = true,
            offlineStamp = "Datos de la última sincronización, hoy 04:50",
            onToggleExpanded = {},
        )
    }
}

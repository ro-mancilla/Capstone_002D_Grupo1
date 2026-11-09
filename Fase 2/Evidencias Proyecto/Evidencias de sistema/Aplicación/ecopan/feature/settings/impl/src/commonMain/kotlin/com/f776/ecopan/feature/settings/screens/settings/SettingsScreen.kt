@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.f776.ecopan.feature.settings.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.f776.core.ui.preview.AdaptivePreview
import com.f776.core.ui.components.isExpanded
import com.f776.core.ui.components.responsiveWidth
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.core.designsystem.theme.EcopanSpacing
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.designsystem.theme.monoFontFamily
import com.f776.ecopan.domain.preferences.AppTheme
import com.f776.ecopan.feature.settings.screens.settings.components.RejectedOperationsSection
import com.f776.ecopan.feature.settings.screens.settings.components.SettingsCategoryHeader
import kotlin.uuid.Uuid
import ecopan.feature.settings.impl.generated.resources.Res
import ecopan.feature.settings.impl.generated.resources.about_app_label
import ecopan.feature.settings.impl.generated.resources.about_build_label
import ecopan.feature.settings.impl.generated.resources.about_bakery_label
import ecopan.feature.settings.impl.generated.resources.about_branch_label
import ecopan.feature.settings.impl.generated.resources.about_category
import ecopan.feature.settings.impl.generated.resources.about_device_label
import ecopan.feature.settings.impl.generated.resources.about_unknown_value
import ecopan.feature.settings.impl.generated.resources.about_version_label
import ecopan.feature.settings.impl.generated.resources.appearance_category
import ecopan.feature.settings.impl.generated.resources.devices_category
import ecopan.feature.settings.impl.generated.resources.permissions_category
import ecopan.feature.settings.impl.generated.resources.settings_title
import ecopan.feature.settings.impl.generated.resources.theme_dark
import ecopan.feature.settings.impl.generated.resources.theme_explanation
import ecopan.feature.settings.impl.generated.resources.theme_label
import ecopan.feature.settings.impl.generated.resources.theme_light
import ecopan.feature.settings.impl.generated.resources.back_cd
import ecopan.feature.settings.impl.generated.resources.theme_system
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal expect fun SettingsScreen(
    /** Null when settings is a navigation-suite tab, which has nothing to go back to. */
    onBack: (() -> Unit)?,
    viewModel: SettingsViewModel = koinViewModel()
)

@Composable
internal fun SettingsScreenContent(
    uiState: SettingsUiState,
    onBack: (() -> Unit)?,
    onThemeSelected: (AppTheme) -> Unit,
    onAboutClick: () -> Unit,
    onRetryRejected: (Uuid) -> Unit = {},
    onDiscardRejected: (Uuid) -> Unit = {},
    dynamicColorSetting: @Composable () -> Unit = {},
    alarmPermissionSetting: @Composable () -> Unit = {},
    notificationPermissionSetting: @Composable () -> Unit = {},
    rateAppListItem: @Composable () -> Unit = {},
    /** The big "Balanza" card of Dispositivos, and the printer / drawer / terminal cards beside it. */
    devicesScale: @Composable () -> Unit = {},
    devicesOther: @Composable () -> Unit = {},
    /** Pinned under the content; the unapplied-changes bar lives here. */
    bottomBar: @Composable () -> Unit = {},
    showPermissionsSection: Boolean = false,
    showDevicesSection: Boolean = false,
) {
    Scaffold(
        bottomBar = bottomBar,
        // A plain bar, not LargeFlexibleTopAppBar. The large variant only earns its height by
        // collapsing on scroll, and it was given no scroll behaviour here — so it sat permanently
        // expanded, spending a third of a phone's first screenful on the word "Configuración" and
        // pushing the first real control below the fold. The design's header is one compact row
        // with a hairline under it.
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(Res.string.settings_title)) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.back_cd)
                                )
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { padding ->
        // Only present when the server has refused something: an empty "rejected" card on every
        // visit would teach cashiers to ignore the one section that means a sale was not accepted.
        val rejected: @Composable () -> Unit = {
            if (uiState.rejected.isNotEmpty()) {
                RejectedOperationsSection(uiState.rejected, onRetryRejected, onDiscardRejected)
            }
        }

        val appearance: @Composable () -> Unit = {
            SettingsSection(stringResource(Res.string.appearance_category)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.theme_label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ThemeChoiceRow(selected = uiState.theme, onSelect = onThemeSelected)
                    Text(
                        stringResource(Res.string.theme_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                dynamicColorSetting()
            }
        }

        val permissions: @Composable () -> Unit = {
            if (showPermissionsSection) {
                SettingsSection(stringResource(Res.string.permissions_category)) {
                    notificationPermissionSetting()
                    alarmPermissionSetting()
                }
            }
        }

        val about: @Composable () -> Unit = {
            SettingsSection(stringResource(Res.string.about_category)) {
                InstallationRows(uiState.installation)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                rateAppListItem()
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.about_app_label)) },
                    trailingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onAboutClick() }
                )
            }
        }

        val expanded = currentWindowAdaptiveInfo().windowSizeClass.isExpanded

        val devices: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsCategoryHeader(stringResource(Res.string.devices_category))
                Column(verticalArrangement = Arrangement.spacedBy(EcopanSpacing.cardGap)) {
                    devicesScale()
                    devicesOther()
                }
            }
        }

        if (expanded && showDevicesSection) {
            // On the till the single 480dp column left two thirds of a 1280dp window empty
            // while the device list scrolled. Devices are the long, primary column; the short
            // preference cards sit beside them. Each column scrolls on its own: one shared
            // scroll left the short column's half of the window empty once the long one moved.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1120.dp + 48.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    rejected()
                    devices()
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    appearance()
                    permissions()
                    about()
                }
            }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    // Extra at the bottom so the last card clears the navigation bar instead of
                    // ending flush against it.
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Full-bleed ListItems read fine on a phone but become ~1800dp-wide rows on a
                // tablet; cap the content column and centre it instead.
                Column(
                    modifier = Modifier.responsiveWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    rejected()
                    appearance()
                    if (showDevicesSection) devices()
                    permissions()
                    about()
                }
            }
        }
    }
}

/**
 * A muted eyebrow over a bounded card, replacing the full-bleed `ListItem` runs this screen used
 * to be. Grouping matters more here than in a list of contacts: "Tema" and "Color dinámico" are
 * one decision about how the app looks, and an unbroken column of rows gave that no more weight
 * than the divider between any two unrelated settings.
 */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsCategoryHeader(title)
        OutlinedCard(Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * What this build is and where it is installed: the four facts a baker reads back down the phone
 * when something is wrong. Numbers (version, build) are monospaced; names (bakery, device, branch) are in
 * the body font. All are right-aligned so they line up as a column.
 */
@Composable
private fun InstallationRows(installation: InstallationUiState) {
    val unknown = stringResource(Res.string.about_unknown_value)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InstallationRow(stringResource(Res.string.about_version_label), installation.version.ifBlank { unknown }, mono = true)
        InstallationRow(stringResource(Res.string.about_build_label), installation.build.ifBlank { unknown }, mono = true)
        // Blank before the bootstrap pull lands: an unenrolled device has no bakery to name, and
        // an em dash says that more honestly than an empty row.
        InstallationRow(stringResource(Res.string.about_bakery_label), installation.bakeryName.ifBlank { unknown })
        // The name typed at enrollment ("Caja 1") and the branch, as two rows: they answer different
        // questions. Blank device name means enrolled before the name was kept. Never the UUID.
        InstallationRow(stringResource(Res.string.about_device_label), installation.deviceName.ifBlank { unknown })
        InstallationRow(stringResource(Res.string.about_branch_label), installation.branchName.ifBlank { unknown })
    }
}

@Composable
private fun InstallationRow(label: String, value: String, stacked: Boolean = false, mono: Boolean = false) {
    val labelText: @Composable () -> Unit = {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Never abbreviated: the device id is what support asks a baker to read out, so it is shown
    // whole even though that costs a line.
    val valueText: @Composable (Modifier, TextAlign) -> Unit = { modifier, align ->
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) monoFontFamily() else null,
            textAlign = align,
            modifier = modifier,
        )
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            labelText()
            valueText(Modifier.fillMaxWidth(), TextAlign.Start)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            labelText()
            valueText(Modifier.weight(1f), TextAlign.End)
        }
    }
}

/**
 * The Material segmented button, not a hand-rolled row of `Surface`s. The hand-rolled version put
 * `Modifier.selectable` *outside* the surface's shape, so its ripple and hover highlight painted
 * the full rectangular bounds while the control drew a pill inside them — the highlight visibly
 * overhung the button on both ends. `SegmentedButton` clips its own indication to the shape it is
 * given, and brings the rest of the spec with it: the connected start/end shapes, the checkmark on
 * the active segment, and `Role.RadioButton` semantics so a screen reader announces one three-way
 * choice rather than three unrelated buttons.
 *
 * Inline rather than in a dialog: choosing between "Claro", "Oscuro" and "Sistema" cost two taps
 * and a full-screen interruption to change something visibly happening behind it.
 */
@Composable
private fun ThemeChoiceRow(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    val options = listOf(
        AppTheme.LIGHT to stringResource(Res.string.theme_light),
        AppTheme.DARK to stringResource(Res.string.theme_dark),
        AppTheme.SYSTEM to stringResource(Res.string.theme_system),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (theme, label) ->
            SegmentedButton(
                selected = theme == selected,
                onClick = { onSelect(theme) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                ),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@AdaptivePreview
@LocalizedPreview
@Composable
private fun SettingsScreenContentPreview() {
    EcopanTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(),
            onBack = null,
            onThemeSelected = {},
            onAboutClick = {}
        )
    }
}

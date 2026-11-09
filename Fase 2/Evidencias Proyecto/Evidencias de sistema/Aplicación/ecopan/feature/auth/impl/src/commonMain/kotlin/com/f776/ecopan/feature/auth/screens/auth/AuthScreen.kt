@file:OptIn(ExperimentalLayoutApi::class)

package com.f776.ecopan.feature.auth.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.f776.core.designsystem.component.EcopanAlert
import com.f776.core.designsystem.component.StatusTone
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f776.core.ui.components.responsiveWidth
import com.f776.core.ui.preview.AdaptivePreview
import com.f776.core.ui.preview.LocalizedPreview
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.ecopan.core.domain.auth.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import ecopan.feature.auth.impl.generated.resources.Res
import ecopan.feature.auth.impl.generated.resources.auth_app_name
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_footer
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_kicker
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_subtitle
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_step_active
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_step_done
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_step_pending
import ecopan.feature.auth.impl.generated.resources.auth_pin_backspace_cd
import ecopan.feature.auth.impl.generated.resources.auth_dev_quick_login_heading
import ecopan.feature.auth.impl.generated.resources.auth_enroll_device_name_hint
import ecopan.feature.auth.impl.generated.resources.auth_enroll_device_name_label
import ecopan.feature.auth.impl.generated.resources.auth_enroll_error_invalid_credentials
import ecopan.feature.auth.impl.generated.resources.auth_enroll_footer
import ecopan.feature.auth.impl.generated.resources.auth_enroll_kicker
import ecopan.feature.auth.impl.generated.resources.auth_enroll_needs_connection
import ecopan.feature.auth.impl.generated.resources.auth_enroll_too_many_attempts
import ecopan.feature.auth.impl.generated.resources.auth_enroll_password_label
import ecopan.feature.auth.impl.generated.resources.auth_enroll_submit
import ecopan.feature.auth.impl.generated.resources.auth_enroll_subtitle
import ecopan.feature.auth.impl.generated.resources.auth_enroll_tenant_label
import ecopan.feature.auth.impl.generated.resources.auth_enroll_title
import ecopan.feature.auth.impl.generated.resources.auth_enroll_username_label
import ecopan.feature.auth.impl.generated.resources.auth_lock_choose_name_sub
import ecopan.feature.auth.impl.generated.resources.auth_lock_choose_name_title
import ecopan.feature.auth.impl.generated.resources.auth_lock_enter_pin_sub
import ecopan.feature.auth.impl.generated.resources.auth_lock_entering_sub
import ecopan.feature.auth.impl.generated.resources.auth_lock_locked_sub
import ecopan.feature.auth.impl.generated.resources.auth_lock_staff_section
import ecopan.feature.auth.impl.generated.resources.auth_lock_too_many_attempts
import ecopan.feature.auth.impl.generated.resources.auth_lock_unsupported
import ecopan.feature.auth.impl.generated.resources.auth_lock_wrong_pin_sub
import ecopan.feature.auth.impl.generated.resources.auth_lock_wrong_pin_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * The only public composable this module exposes — [App][com.f776.ecopan.App] calls it directly,
 * wrapping the real navigation in [content]. Enrollment/Bootstrap/Lock gate whether [content] ever
 * renders at all, rather than being NavKey destinations themselves (see [AuthViewModel]'s KDoc).
 */
@Composable
fun AuthGate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AuthFlowRoot(modifier, content)
}

@Composable
private fun AuthFlowRoot(modifier: Modifier = Modifier, content: @Composable () -> Unit, authViewModel: AuthViewModel = koinViewModel()) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    AuthFlowScreen(modifier = modifier, state = state, onAction = authViewModel::onAction, content = content)
}

@Composable
internal fun AuthFlowScreen(
    modifier: Modifier = Modifier,
    state: AuthUiState,
    onAction: (AuthAction) -> Unit,
    content: @Composable () -> Unit = {},
) {
    if (state.phase == AuthPhase.UNLOCKED) {
        content()
        return
    }
    Scaffold(modifier = modifier) { innerPadding ->
        // Scrollable because the enrollment form is taller than a phone viewport with the IME open;
        // centred content still centres when there is room, and the submit button stays reachable
        // when there isn't.
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.phase == AuthPhase.ENROLLMENT -> EnrollmentScreen(state, onAction)
                state.phase == AuthPhase.BOOTSTRAPPING -> BootstrapScreen(state)
                state.phase == AuthPhase.LOCK -> LockScreen(state, onAction)
                else -> Unit
            }
        }
    }
}

@Composable
private fun AuthHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
            Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                Text("E", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Column {
            Text(stringResource(Res.string.auth_app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(Res.string.auth_enroll_kicker),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EnrollmentScreen(state: AuthUiState, onAction: (AuthAction) -> Unit) {
    Column(Modifier.responsiveWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AuthHeader() }
        if (state.devQuickLoginEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.auth_dev_quick_login_heading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevQuickLoginProfile.entries.forEach { profile ->
                        OutlinedButton(
                            onClick = { onAction(AuthAction.OnDevQuickLoginTapped(profile)) },
                            // The selector of the Baseline Profile journeys: `dev_quick_login_BAKER` and so on.
                            modifier = Modifier.testTag("dev_quick_login_${profile.name}"),
                        ) {
                            Text(profile.role.label())
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.auth_enroll_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.auth_enroll_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.tenantSlug,
                    onValueChange = { onAction(AuthAction.OnTenantSlugChanged(it)) },
                    label = { Text(stringResource(Res.string.auth_enroll_tenant_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { onAction(AuthAction.OnUsernameChanged(it)) },
                        label = { Text(stringResource(Res.string.auth_enroll_username_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onAction(AuthAction.OnPasswordChanged(it)) },
                        label = { Text(stringResource(Res.string.auth_enroll_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = state.deviceName,
                        onValueChange = { onAction(AuthAction.OnDeviceNameChanged(it)) },
                        label = { Text(stringResource(Res.string.auth_enroll_device_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(Res.string.auth_enroll_device_name_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.enrollError?.let { error ->
                    EcopanAlert(
                        message = stringResource(
                            when (error) {
                                EnrollError.INVALID_CREDENTIALS -> Res.string.auth_enroll_error_invalid_credentials
                                EnrollError.NEEDS_CONNECTION -> Res.string.auth_enroll_needs_connection
                                EnrollError.TOO_MANY_ATTEMPTS -> Res.string.auth_enroll_too_many_attempts
                            },
                        ),
                        // A wrong password is the cashier's to fix; the other two are wait-and-retry.
                        tone = if (error == EnrollError.INVALID_CREDENTIALS) StatusTone.Danger else StatusTone.Warning,
                    )
                }
                Button(
                    onClick = { onAction(AuthAction.OnEnrollTapped) },
                    enabled = state.tenantSlug.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.auth_enroll_submit))
                }
            }
        }
        Text(
            stringResource(Res.string.auth_enroll_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BootstrapScreen(state: AuthUiState) {
    Column(
        Modifier.responsiveWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
        Text(
            stringResource(Res.string.auth_bootstrap_kicker),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(state.tenantName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(Res.string.auth_bootstrap_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.bootstrapSteps.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Material Symbols, not glyphs: a "✓"/"⟳"/"·" column renders in whatever
                        // the text face happens to have, sits on the text baseline rather than
                        // optically centred, and is read aloud as punctuation. These carry a
                        // contentDescription so the step's state survives without the colour.
                        val (marker, markerDescription) = when {
                            step.done -> Icons.Filled.CheckCircle to stringResource(Res.string.auth_bootstrap_step_done)
                            step.active -> Icons.Filled.Sync to stringResource(Res.string.auth_bootstrap_step_active)
                            else -> Icons.Outlined.Circle to stringResource(Res.string.auth_bootstrap_step_pending)
                        }
                        Icon(
                            imageVector = marker,
                            contentDescription = markerDescription,
                            tint = if (step.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            step.step.label(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (step.active) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        step.note.label()?.let { note ->
                            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text(
            stringResource(Res.string.auth_bootstrap_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LockScreen(state: AuthUiState, onAction: (AuthAction) -> Unit) {
    if (!state.pinUnlockSupported) {
        Text(stringResource(Res.string.auth_lock_unsupported), color = MaterialTheme.colorScheme.error)
        return
    }

    Column(
        modifier = Modifier.responsiveWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(Res.string.auth_lock_staff_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A real two-column grid, not a plain flow. Each card used to size to its own name, so
            // five people produced three different card widths and a ragged right edge that read as
            // a broken layout rather than a list of choices. `maxItemsInEachRow` plus a weight on
            // every child makes the columns equal, and the trailing spacer keeps the last card its
            // column's width instead of stretching across the row when the count is odd.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = STAFF_GRID_COLUMNS,
            ) {
                state.staff.forEach { person ->
                    StaffCard(
                        person = person,
                        selected = person.id == state.selectedStaffId,
                        onClick = { onAction(AuthAction.OnStaffSelected(person.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((STAFF_GRID_COLUMNS - state.staff.size % STAFF_GRID_COLUMNS) % STAFF_GRID_COLUMNS) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        val locked = state.lockRemainingSeconds > 0
        val selected = state.staff.firstOrNull { it.id == state.selectedStaffId }
        val (title, subtitle) = when {
            locked -> stringResource(Res.string.auth_lock_too_many_attempts) to
                stringResource(Res.string.auth_lock_locked_sub, "0:${state.lockRemainingSeconds.toString().padStart(2, '0')}")
            state.pinError -> (selected?.name.orEmpty()) to stringResource(Res.string.auth_lock_wrong_pin_sub)
            selected == null -> stringResource(Res.string.auth_lock_choose_name_title) to stringResource(Res.string.auth_lock_choose_name_sub)
            state.enteredPinLength > 0 -> selected.name to stringResource(Res.string.auth_lock_entering_sub)
            else -> selected.name to stringResource(Res.string.auth_lock_enter_pin_sub)
        }

        val padDisabled = selected == null || locked

        // The PIN step is its own surface. Before, the prompt, the dots and the pad were three
        // loose blocks in the page column, and with nobody selected the ten disabled keys floated
        // there at disabled alpha looking like a screen that had failed to load. Inside a panel,
        // under its own heading, the same disabled pad reads as the next step instead.
        // `surfaceContainerLowest`, deliberately, not `surfaceVariant`: this theme defines
        // `secondaryContainer` and `surfaceVariant` as the *same* colour (#ECE5D8 light, #312B26
        // dark), so a tonal key on a surfaceVariant panel is invisible -- the enabled pad read as
        // ten bare digits while the disabled one at least showed its containers. The lowest
        // surface steps away from both and keeps the keys legible in either theme.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.pinError || locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(MAX_PIN_DOTS) { index ->
                        val filled = index < state.enteredPinLength
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (filled) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(14.dp),
                        ) {}
                    }
                }

                // Weighted keys, not fixed 64dp — same pattern as batch entry's keypad, so the pad
                // shrinks on narrow phones instead of overflowing and stays capped on wide windows.
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = PIN_PAD_MAX_WIDTH).fillMaxWidth(),
                ) {
                    PIN_PAD_ROWS.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { key ->
                                when (key) {
                                    PinPadKey.Blank -> Spacer(Modifier.weight(1f).aspectRatio(PIN_KEY_ASPECT))

                                    is PinPadKey.Digit -> FilledTonalButton(
                                        onClick = { onAction(AuthAction.OnPinDigitTapped(key.value)) },
                                        enabled = !padDisabled,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.weight(1f).aspectRatio(PIN_KEY_ASPECT).testTag("pin_key_${key.value}"),
                                    ) {
                                        Text(key.value, style = MaterialTheme.typography.titleMedium)
                                    }

                                    PinPadKey.Backspace -> FilledTonalButton(
                                        onClick = { onAction(AuthAction.OnPinBackspace) },
                                        enabled = !padDisabled,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.weight(1f).aspectRatio(PIN_KEY_ASPECT),
                                    ) {
                                        // Auto-mirrored: the erase key points at the text it eats,
                                        // which flips with the writing direction.
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = stringResource(Res.string.auth_pin_backspace_cd),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One person in the staff grid. Fixed height so a one-line name and a two-line one produce the
 * same card, which is the other half of keeping the grid regular. */
@Composable
private fun StaffCard(
    person: StaffUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(
        // Tagged by role (`staff_card_BAKER`) for the Baseline Profile journeys: the seeded reference
        // tenant has one person per role.
        modifier = modifier.height(STAFF_CARD_HEIGHT).testTag("staff_card_${person.role.name}"),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        person.initials,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    person.role.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Two columns: at the till's 480dp content width a third column would cut names in half, and one
 * column would push the pad off the fold. */
private const val STAFF_GRID_COLUMNS = 2
private val STAFF_CARD_HEIGHT = 64.dp
private const val MAX_PIN_DOTS = 4

/** Sized so the staff grid and the whole pad clear the till's 800dp window without scrolling --
 * a cashier mid-queue should never have to scroll to reach a digit. */
private val PIN_PAD_MAX_WIDTH = 260.dp
private const val PIN_KEY_ASPECT = 1.6f

/** A key on the PIN pad. Typed rather than a list of strings, so the erase key can be an icon and
 * does not have to masquerade as a digit to travel through the same list. */
private sealed interface PinPadKey {
    data class Digit(val value: String) : PinPadKey
    data object Backspace : PinPadKey
    data object Blank : PinPadKey
}

private val PIN_PAD_ROWS: List<List<PinPadKey>> = listOf(
    listOf(PinPadKey.Digit("1"), PinPadKey.Digit("2"), PinPadKey.Digit("3")),
    listOf(PinPadKey.Digit("4"), PinPadKey.Digit("5"), PinPadKey.Digit("6")),
    listOf(PinPadKey.Digit("7"), PinPadKey.Digit("8"), PinPadKey.Digit("9")),
    listOf(PinPadKey.Blank, PinPadKey.Digit("0"), PinPadKey.Backspace),
)

@AdaptivePreview
@LocalizedPreview
@Composable
private fun AuthEnrollmentPreview() {
    EcopanTheme {
        Surface {
            AuthFlowScreen(
                state = AuthUiState(loading = false, phase = AuthPhase.ENROLLMENT, devQuickLoginEnabled = true),
                onAction = {},
            )
        }
    }
}

@AdaptivePreview
@LocalizedPreview
@Composable
private fun AuthLockPreview() {
    EcopanTheme {
        Surface {
            AuthFlowScreen(
                state = AuthUiState(
                    loading = false,
                    phase = AuthPhase.LOCK,
                    staff = listOf(StaffUi(Uuid.random(), "Camila Reyes", Role.CASHIER, "CR")),
                ),
                onAction = {},
            )
        }
    }
}

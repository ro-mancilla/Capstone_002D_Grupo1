package com.f776.ecopan.feature.auth.screens.auth

import androidx.compose.runtime.Composable
import ecopan.feature.auth.impl.generated.resources.Res
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_note_done
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_note_retrying
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_step_account
import ecopan.feature.auth.impl.generated.resources.auth_bootstrap_step_initial_sync
import org.jetbrains.compose.resources.stringResource

/** Where a [BootstrapStep] becomes user-visible text — same convention as `Role.label()`. */
@Composable
internal fun BootstrapStep.label(): String = stringResource(
    when (this) {
        BootstrapStep.ACCOUNT -> Res.string.auth_bootstrap_step_account
        BootstrapStep.INITIAL_SYNC -> Res.string.auth_bootstrap_step_initial_sync
    },
)

/** `null` for [BootstrapStepNote.NONE] — the row simply omits the note text. */
@Composable
internal fun BootstrapStepNote.label(): String? = when (this) {
    BootstrapStepNote.NONE -> null
    BootstrapStepNote.DONE -> stringResource(Res.string.auth_bootstrap_note_done)
    BootstrapStepNote.RETRYING_IN_BACKGROUND -> stringResource(Res.string.auth_bootstrap_note_retrying)
}

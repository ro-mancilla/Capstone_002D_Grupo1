package com.f776.ecopan.feature.auth.screens.auth

import androidx.compose.runtime.Composable
import com.f776.ecopan.core.domain.auth.Role
import ecopan.feature.auth.impl.generated.resources.Res
import ecopan.feature.auth.impl.generated.resources.auth_role_admin
import ecopan.feature.auth.impl.generated.resources.auth_role_baker
import ecopan.feature.auth.impl.generated.resources.auth_role_branch_manager
import ecopan.feature.auth.impl.generated.resources.auth_role_cashier
import ecopan.feature.auth.impl.generated.resources.auth_role_general_manager
import org.jetbrains.compose.resources.stringResource

/**
 * The one place a [Role] becomes user-visible text. The enum constants are an English wire/storage
 * detail (they arrive verbatim from the backend's `role` column); this app's UI is Spanish-only, so
 * rendering `.name` put `BRANCH_MANAGER` on the lock screen next to fully translated copy.
 */
@Composable
internal fun Role.label(): String = stringResource(
    when (this) {
        Role.CASHIER -> Res.string.auth_role_cashier
        Role.BAKER -> Res.string.auth_role_baker
        Role.BRANCH_MANAGER -> Res.string.auth_role_branch_manager
        Role.GENERAL_MANAGER -> Res.string.auth_role_general_manager
        Role.ADMIN -> Res.string.auth_role_admin
    },
)

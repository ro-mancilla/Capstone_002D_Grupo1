package com.f776.ecopan.feature.auth.screens.auth

import kotlin.uuid.Uuid

internal sealed interface AuthAction {
    data class OnTenantSlugChanged(val value: String) : AuthAction
    data class OnUsernameChanged(val value: String) : AuthAction
    data class OnPasswordChanged(val value: String) : AuthAction
    data class OnDeviceNameChanged(val value: String) : AuthAction
    data object OnEnrollTapped : AuthAction
    data class OnDevQuickLoginTapped(val profile: DevQuickLoginProfile) : AuthAction

    data class OnStaffSelected(val staffId: Uuid) : AuthAction
    data object OnBackToStaffPicker : AuthAction
    /** [digit] is a single "0".."9". Backspace is [OnPinBackspace], not a sentinel digit -- the
     * pad's erase key is an icon, and a glyph standing in for one has no business in the domain. */
    data class OnPinDigitTapped(val digit: String) : AuthAction
    data object OnPinBackspace : AuthAction
}

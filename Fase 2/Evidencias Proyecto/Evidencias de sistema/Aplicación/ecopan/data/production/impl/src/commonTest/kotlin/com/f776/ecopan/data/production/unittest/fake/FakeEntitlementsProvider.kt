package com.f776.ecopan.data.production.unittest.fake

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.tenant.EntitlementsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeEntitlementsProvider : EntitlementsProvider {
    private val enabled = MutableStateFlow<Set<EntitlementModule>>(emptySet())

    fun setEnabled(module: EntitlementModule, isEnabled: Boolean) {
        enabled.value = if (isEnabled) enabled.value + module else enabled.value - module
    }

    override fun isModuleEnabled(module: EntitlementModule) = enabled.map { module in it }
}

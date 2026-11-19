package com.f776.ecopan.feature.production.unittest.fake

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.tenant.EntitlementsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeEntitlementsProvider : EntitlementsProvider {
    val enabled = MutableStateFlow(setOf(EntitlementModule.POS_SALES, EntitlementModule.INVENTORY_FORMULAS))

    override fun isModuleEnabled(module: EntitlementModule) = enabled.map { module in it }
}

package com.f776.ecopan.data.tenant

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.tenant.EntitlementsProvider
import com.f776.ecopan.domain.tenant.TenantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class EntitlementsProviderImpl(
    private val tenantRepository: TenantRepository,
) : EntitlementsProvider {

    override fun isModuleEnabled(module: EntitlementModule): Flow<Boolean> {
        if (module == EntitlementModule.POS_SALES) {
            return flowOf(true) // always on, never toggled (01 §7)
        }
        return tenantRepository.observeTenant().map { tenant -> module in (tenant?.enabledModules ?: emptySet()) }
    }
}

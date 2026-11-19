package com.f776.ecopan.data.tenant.unittest

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.tenant.EntitlementsProviderImpl
import com.f776.ecopan.data.tenant.TenantRepositoryImpl
import com.f776.ecopan.data.tenant.unittest.fake.FakeBranchDao
import com.f776.ecopan.data.tenant.unittest.fake.FakePinVerifier
import com.f776.ecopan.data.tenant.unittest.fake.FakeTenantDao
import com.f776.ecopan.data.tenant.unittest.fake.FakeUserDao
import com.f776.ecopan.domain.tenant.TenantProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class EntitlementsProviderImplTest {

    private val repository = TenantRepositoryImpl(FakeTenantDao(), FakeBranchDao(), FakeUserDao(), FakePinVerifier())
    private val entitlements = EntitlementsProviderImpl(repository)

    @Test
    fun `POS_SALES is always enabled even with no tenant cached`() = runTest {
        assertTrue(entitlements.isModuleEnabled(EntitlementModule.POS_SALES).first())
    }

    @Test
    fun `an unlisted module is disabled before any tenant is cached`() = runTest {
        assertFalse(entitlements.isModuleEnabled(EntitlementModule.DEMAND_FORECASTING).first())
    }

    @Test
    fun `a module present in the tenant's enabledModules reports enabled`() = runTest {
        repository.upsertTenant(
            TenantProfile(
                id = Uuid.random(),
                slug = "unipan",
                name = "UniPan",
                currencyCode = "CLP",
                enabledModules = setOf(EntitlementModule.MULTI_BRANCH_ROLES),
            )
        )

        assertTrue(entitlements.isModuleEnabled(EntitlementModule.MULTI_BRANCH_ROLES).first())
        assertFalse(entitlements.isModuleEnabled(EntitlementModule.DEMAND_FORECASTING).first())
    }

    @Test
    fun `toggling a module off is reflected immediately this is the entitlement-toggle demo`() = runTest {
        val tenantId = Uuid.random()
        repository.upsertTenant(
            TenantProfile(tenantId, "unipan", "UniPan", "CLP", setOf(EntitlementModule.ANALYTICS_DASHBOARD))
        )
        assertTrue(entitlements.isModuleEnabled(EntitlementModule.ANALYTICS_DASHBOARD).first())

        repository.upsertTenant(
            TenantProfile(tenantId, "unipan", "UniPan", "CLP", emptySet())
        )

        assertFalse(entitlements.isModuleEnabled(EntitlementModule.ANALYTICS_DASHBOARD).first())
    }
}
